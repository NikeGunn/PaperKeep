package app.paperkeep.feature.scanner.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.db.OcrStatus
import app.paperkeep.core.data.db.ScanDao
import app.paperkeep.core.data.db.ScanEntity
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Page
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.ImageCleanupProcessor
import app.paperkeep.core.imaging.ImageFilterProcessor
import app.paperkeep.core.imaging.PerspectiveTransform
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocTypePolicies
import app.paperkeep.core.ml.DocumentClassifier
import app.paperkeep.core.ml.DocumentType
import app.paperkeep.core.ml.OcrOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the capture pipeline (P1.9), crop screen state (P1.10), and document
 * classification (P3.1).
 *
 * Classification runs concurrently with the crop screen display so the user
 * can immediately start adjusting corners while the classifier runs in the
 * background. When the result arrives, the recommended filter is applied
 * UNLESS the user has already manually changed the filter.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val classifier: DocumentClassifier,
    private val dispatchers: AppDispatchers,
    private val savedState: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val imageStore: EncryptedImageStore,
    private val scanDao: ScanDao,
    private val ocrOrchestrator: OcrOrchestrator,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _reviewState = MutableStateFlow<FilterReviewState>(FilterReviewState.Idle)
    val reviewState: StateFlow<FilterReviewState> = _reviewState.asStateFlow()

    // ── Filter review (post-ML-Kit multi-page batch) ─────────────────────────

    /**
     * Hand a batch of cropped pages (returned by Google's ML Kit scanner) to
     * the filter-review screen. Picks an opinionated default filter based on
     * the document classifier (run once on the first page) so first-time users
     * get a sensible look without touching the strip.
     */
    fun beginFilterReview(pages: List<Bitmap>) {
        if (pages.isEmpty()) {
            _reviewState.value = FilterReviewState.Idle
            return
        }
        // Show the review screen immediately with the raw pages so the user
        // sees their scan instantly. Default to DOCUMENT (the most-used look
        // in mainstream scanner apps); the classifier may refine this once
        // it returns. Shadow removal runs in the background and patches the
        // state per page as each finishes — so previews look clean within a
        // second or two without blocking the UI.
        _reviewState.value = FilterReviewState.Reviewing(
            pages = pages,
            cleanedPages = List(pages.size) { null },
            currentIndex = 0,
            selectedFilter = ImageFilter.DOCUMENT,
        )

        viewModelScope.launch(dispatchers.default) {
            val recommended = runCatching {
                val cls = classifier.classify(pages.first())
                DocTypePolicies.forType(cls.type).recommendedFilter
            }.getOrDefault(ImageFilter.DOCUMENT)
            withContext(dispatchers.main) {
                val current = _reviewState.value as? FilterReviewState.Reviewing
                    ?: return@withContext
                _reviewState.value = current.copy(selectedFilter = recommended)
            }
        }

        // Shadow-removal pass per page. Patches state incrementally so the
        // first page goes clean within ~300ms while the rest catch up.
        viewModelScope.launch(dispatchers.default) {
            pages.forEachIndexed { idx, page ->
                val cleaned = runCatching { ImageCleanupProcessor.removeShadow(page) }.getOrNull()
                    ?: return@forEachIndexed
                withContext(dispatchers.main) {
                    val current = _reviewState.value as? FilterReviewState.Reviewing
                        ?: return@withContext
                    val updated = current.cleanedPages.toMutableList()
                    if (idx < updated.size) updated[idx] = cleaned
                    _reviewState.value = current.copy(cleanedPages = updated)
                }
            }
        }
    }

    fun selectReviewFilter(filter: ImageFilter) {
        val current = _reviewState.value as? FilterReviewState.Reviewing ?: return
        _reviewState.value = current.copy(selectedFilter = filter)
    }

    fun selectReviewPage(index: Int) {
        val current = _reviewState.value as? FilterReviewState.Reviewing ?: return
        if (index !in current.pages.indices) return
        _reviewState.value = current.copy(currentIndex = index)
    }

    /**
     * Persist every page in the review batch with the currently selected filter.
     * Pages share a document — the first page creates it (or appends), subsequent
     * pages append to whatever documentId came back.
     *
     * On success: clears the review state and invokes [onSaved] with the docId.
     * On failure: reverts to [FilterReviewState.Reviewing] so the user can retry.
     */
    fun saveReviewedBatch(
        appendToDocumentId: String? = null,
        replacePageId: String? = null,
        onSaved: (String) -> Unit = {},
    ) {
        val reviewing = _reviewState.value as? FilterReviewState.Reviewing ?: return
        // Prefer the shadow-cleaned version of every page when available, so
        // what the user previewed is what gets saved. Falls back to the raw
        // page for any page whose cleanup hasn't completed yet — the
        // persistence path runs removeShadow again as a safety net.
        val pages = reviewing.pages.mapIndexed { i, p -> reviewing.cleanedPages.getOrNull(i) ?: p }
        val filter = reviewing.selectedFilter
        if (pages.isEmpty()) return

        _reviewState.value = FilterReviewState.Saving
        viewModelScope.launch(dispatchers.io) {
            var docId: String? = appendToDocumentId
            var firstReplace: String? = replacePageId
            try {
                for (bitmap in pages) {
                    val w = bitmap.width.toFloat()
                    val h = bitmap.height.toFloat()
                    val fullQuad = Quad(
                        topLeft = Point2f(0f, 0f),
                        topRight = Point2f(w - 1f, 0f),
                        bottomRight = Point2f(w - 1f, h - 1f),
                        bottomLeft = Point2f(0f, h - 1f),
                    )
                    val readyState = CaptureState.ReadyToCrop(
                        image = bitmap,
                        quad = fullQuad,
                        classification = null,
                        selectedFilter = filter,
                    )
                    val saveResult = persistCurrentCapture(readyState, docId, firstReplace)
                    docId = saveResult.documentId
                    // replacePageId only applies to the first page; everything
                    // after that is an append into the same document.
                    firstReplace = null

                    launch(dispatchers.default) {
                        runCatching { ocrOrchestrator.processPage(saveResult.page, saveResult.imageBytes) }
                    }
                }

                withContext(dispatchers.main) {
                    _reviewState.value = FilterReviewState.Idle
                    docId?.let(onSaved)
                }
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Capture", "saveReviewedBatch: failed", e)
                withContext(dispatchers.main) {
                    // Reinstate the review state so the user can try again.
                    _reviewState.value = reviewing
                    _state.value = CaptureState.Error(e.message ?: "Failed to save scan")
                }
            }
        }
    }

    fun cancelFilterReview() {
        _reviewState.value = FilterReviewState.Idle
    }

    // ── Capture pipeline ──────────────────────────────────────────────────────

    fun onImageCaptured(bitmap: Bitmap) {
        _state.value = CaptureState.Processing(bitmap)

        viewModelScope.launch(dispatchers.default) {
            try {
                val result = edgeDetector.detect(bitmap)
                val detected = (result as? DetectionResult.Found)?.corners
                val confident = detected?.let {
                    isDetectionConfident(it, bitmap.width, bitmap.height)
                } ?: false
                DebugLog.d(
                    "Paperkeep.Capture",
                    "edge detect: result=${if (detected == null) "NotFound" else "Found"} " +
                        "confident=$confident bitmap=${bitmap.width}x${bitmap.height}",
                )
                val trusted = detected?.takeIf { confident }
                val quad = sanitizeQuad(
                    quad = trusted ?: defaultInsetQuad(bitmap),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                )

                val previewForClassification = PerspectiveTransform.warp(bitmap, quad)

                withContext(dispatchers.main) {
                    persistQuad(quad)
                    _state.value = CaptureState.ReadyToCrop(
                        image = bitmap,
                        quad = quad,
                        classification = null,
                        selectedFilter = ImageFilter.AUTO,
                    )
                }

                // Classification runs concurrently — does not block the crop screen
                val classResult = classifier.classify(previewForClassification)
                val policy = DocTypePolicies.forType(classResult.type)

                withContext(dispatchers.main) {
                    val current = _state.value as? CaptureState.ReadyToCrop ?: return@withContext
                    // Only auto-apply the filter if the user hasn't manually changed it
                    val filter = if (current.userOverrodeType) current.selectedFilter
                                 else policy.recommendedFilter
                    _state.value = current.copy(
                        classification = classResult,
                        selectedFilter = filter,
                    )
                }
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    _state.value = CaptureState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // ── Crop / quad adjustment ────────────────────────────────────────────────

    fun onQuadUpdated(newQuad: Quad) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        val sanitized = sanitizeQuad(newQuad, current.image.width, current.image.height)
        persistQuad(sanitized)
        _state.value = current.copy(quad = sanitized)
    }

    fun rotateImage() {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        viewModelScope.launch(dispatchers.default) {
            val rotated = PerspectiveTransform.rotate(current.image, 90)
            val rotatedQuad = rotateQuadClockwise(
                quad = current.quad,
                sourceWidth = current.image.width,
                sourceHeight = current.image.height,
            )
            withContext(dispatchers.main) {
                persistQuad(rotatedQuad)
                _state.value = current.copy(image = rotated, quad = rotatedQuad)
            }
        }
    }

    fun retake() {
        _state.value = CaptureState.Idle
        savedState.remove<FloatArray>(KEY_QUAD)
    }

    // ── Filter override (user taps filter strip) ──────────────────────────────

    /**
     * Called when the user explicitly selects a filter from the preview strip.
     * Marks the current suggestion as user-overridden so async classifier updates
     * do not overwrite the selected filter.
     */
    fun onFilterSelected(filter: ImageFilter) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        _state.value = current.copy(selectedFilter = filter, userOverrodeType = true)
    }

    // ── Document type override (user taps DocTypeChip and picks a type) ───────

    /**
     * Called when the user selects a document type from the chip picker overlay.
     * Auto-applies the recommended filter for [type] and marks the state as
     * user-overridden so future classifier updates don't overwrite the choice.
     */
    fun onDocTypeOverride(type: DocumentType) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        val policy = DocTypePolicies.forType(type)
        _state.value = current.copy(
            classification = ClassificationResult(type, 1.0f),
            selectedFilter = policy.recommendedFilter,
            userOverrodeType = true,
        )
    }

    // ── Persist to encrypted store + Room ───────────────────────────────────

    /**
     * Saves the current crop result.
     *
     * @param appendToDocumentId when non-null, the page is appended to that
     * existing document (incrementing its page count). Otherwise a brand-new
     * document is created. The [onSaved] callback receives the document id
     * either way.
     */
    /**
     * Save a page that's already been cropped by an external scanner (ML Kit
     * document scanner). Bypasses the local Crop screen entirely — the
     * incoming bitmap IS the document, edge-to-edge.
     *
     * Internally this builds a full-bitmap quad and reuses [persistCurrentCapture]
     * so encryption, thumbnailing, classification, and OCR all run identically
     * to the manual-crop path.
     */
    fun savePreCroppedPage(
        bitmap: Bitmap,
        appendToDocumentId: String? = null,
        replacePageId: String? = null,
        onSaved: (String) -> Unit = {},
    ) {
        _state.value = CaptureState.Processing(bitmap)
        viewModelScope.launch(dispatchers.io) {
            try {
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                val fullQuad = Quad(
                    topLeft = Point2f(0f, 0f),
                    topRight = Point2f(w - 1f, 0f),
                    bottomRight = Point2f(w - 1f, h - 1f),
                    bottomLeft = Point2f(0f, h - 1f),
                )
                // Classify in the same coroutine — runs on the already-cropped
                // image so the recommended filter is the right one for this
                // doc type. Cheap on small images; doesn't hold up persistence.
                val classification = runCatching { classifier.classify(bitmap) }.getOrNull()
                val policy = classification?.let { DocTypePolicies.forType(it.type) }
                val filter = policy?.recommendedFilter ?: ImageFilter.AUTO

                val readyState = CaptureState.ReadyToCrop(
                    image = bitmap,
                    quad = fullQuad,
                    classification = classification,
                    selectedFilter = filter,
                )

                val saveResult = persistCurrentCapture(readyState, appendToDocumentId, replacePageId)
                DebugLog.d(
                    "Paperkeep.Capture",
                    "savePreCroppedPage: persisted doc=${saveResult.documentId} " +
                        "pageIndex=${saveResult.page.pageIndex} " +
                        "imageBytes=${saveResult.imageBytes.size}",
                )

                launch(dispatchers.default) {
                    runCatching { ocrOrchestrator.processPage(saveResult.page, saveResult.imageBytes) }
                }

                withContext(dispatchers.main) {
                    _state.value = CaptureState.Idle
                    savedState.remove<FloatArray>(KEY_QUAD)
                    onSaved(saveResult.documentId)
                }
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Capture", "savePreCroppedPage: failed", e)
                withContext(dispatchers.main) {
                    _state.value = CaptureState.Error(e.message ?: "Failed to save scan")
                }
            }
        }
    }

    fun saveCurrentCapture(
        appendToDocumentId: String? = null,
        replacePageId: String? = null,
        onSaved: (String) -> Unit = {},
    ) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        DebugLog.d(
            "Paperkeep.Capture",
            "saveCurrentCapture: start append=$appendToDocumentId replace=$replacePageId",
        )
        _state.value = CaptureState.Processing(current.image)

        viewModelScope.launch(dispatchers.io) {
            try {
                val saveResult = persistCurrentCapture(current, appendToDocumentId, replacePageId)
                DebugLog.d(
                    "Paperkeep.Capture",
                    "saveCurrentCapture: persisted doc=${saveResult.documentId} " +
                        "pageIndex=${saveResult.page.pageIndex} " +
                        "imageBytes=${saveResult.imageBytes.size}",
                )

                // OCR runs in the background so library navigation stays snappy.
                launch(dispatchers.default) {
                    runCatching { ocrOrchestrator.processPage(saveResult.page, saveResult.imageBytes) }
                }

                withContext(dispatchers.main) {
                    _state.value = CaptureState.Idle
                    savedState.remove<FloatArray>(KEY_QUAD)
                    onSaved(saveResult.documentId)
                }
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Capture", "saveCurrentCapture: failed", e)
                withContext(dispatchers.main) {
                    _state.value = CaptureState.Error(e.message ?: "Failed to save capture")
                }
            }
        }
    }

    // ── SavedStateHandle persistence ──────────────────────────────────────────

    private fun persistQuad(quad: Quad) {
        savedState[KEY_QUAD] = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y,
        )
    }

    fun restoreQuadFromSavedState(): Quad? {
        val arr = savedState.get<FloatArray>(KEY_QUAD) ?: return null
        if (arr.size != 8) return null
        return Quad(
            topLeft = Point2f(arr[0], arr[1]),
            topRight = Point2f(arr[2], arr[3]),
            bottomRight = Point2f(arr[4], arr[5]),
            bottomLeft = Point2f(arr[6], arr[7]),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Default fallback when edge detection fails or low-confidence: a quad
     * inset by [insetFraction] on every side. Default 0.10 → quad covers 80%
     * of each dimension (≈64% of total area). Visible to the user as a
     * generous default rectangle they can drag.
     */
    private fun defaultInsetQuad(bitmap: Bitmap, insetFraction: Float = 0.10f): Quad {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val insetX = (w * insetFraction).coerceAtMost(w * 0.25f)
        val insetY = (h * insetFraction).coerceAtMost(h * 0.25f)
        return Quad(
            topLeft = Point2f(insetX, insetY),
            topRight = Point2f(w - insetX, insetY),
            bottomRight = Point2f(w - insetX, h - insetY),
            bottomLeft = Point2f(insetX, h - insetY),
        )
    }

    private fun sanitizeQuad(quad: Quad, imageWidth: Int, imageHeight: Int): Quad {
        val maxX = (imageWidth - 1).coerceAtLeast(0).toFloat()
        val maxY = (imageHeight - 1).coerceAtLeast(0).toFloat()
        return Quad(
            topLeft = Point2f(quad.topLeft.x.coerceIn(0f, maxX), quad.topLeft.y.coerceIn(0f, maxY)),
            topRight = Point2f(quad.topRight.x.coerceIn(0f, maxX), quad.topRight.y.coerceIn(0f, maxY)),
            bottomRight = Point2f(quad.bottomRight.x.coerceIn(0f, maxX), quad.bottomRight.y.coerceIn(0f, maxY)),
            bottomLeft = Point2f(quad.bottomLeft.x.coerceIn(0f, maxX), quad.bottomLeft.y.coerceIn(0f, maxY)),
        )
    }

    private fun rotateQuadClockwise(quad: Quad, sourceWidth: Int, sourceHeight: Int): Quad {
        val newWidth = sourceHeight.toFloat()
        val newHeight = sourceWidth.toFloat()

        fun rotatePoint(point: Point2f): Point2f {
            val x = (sourceHeight.toFloat() - point.y).coerceIn(0f, newWidth)
            val y = point.x.coerceIn(0f, newHeight)
            return Point2f(x, y)
        }

        return sanitizeQuad(
            quad = Quad(
                topLeft = rotatePoint(quad.topLeft),
                topRight = rotatePoint(quad.topRight),
                bottomRight = rotatePoint(quad.bottomRight),
                bottomLeft = rotatePoint(quad.bottomLeft),
            ),
            imageWidth = sourceHeight,
            imageHeight = sourceWidth,
        )
    }

    private fun isDetectionConfident(quad: Quad, imageWidth: Int, imageHeight: Int): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0) return false

        val imageArea = imageWidth.toFloat() * imageHeight.toFloat()
        val areaFraction = quad.area() / imageArea
        if (areaFraction < MIN_CONFIDENT_QUAD_AREA || areaFraction > MAX_CONFIDENT_QUAD_AREA) {
            return false
        }

        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)

        if (minOf(topWidth, bottomWidth) < imageWidth * MIN_EDGE_SPAN_FRACTION) return false
        if (minOf(leftHeight, rightHeight) < imageHeight * MIN_EDGE_SPAN_FRACTION) return false

        return true
    }

    /**
     * If [bitmap] is landscape (wider than tall) rotate it 90° clockwise so
     * the long edge runs vertically. This is the same auto-orient step
     * CamScanner / Adobe Scan apply — 95% of scanned documents (letters,
     * receipts, notes, contracts, IDs) are naturally portrait, so a
     * landscape result almost always means the user held the phone sideways.
     *
     * Square-ish bitmaps (within 5% of 1:1) are left alone — they're
     * receipts or stamps where orientation is ambiguous.
     */
    private fun autoOrientToPortrait(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val aspect = w.toFloat() / h.toFloat()
        if (aspect <= 1.05f) return bitmap // already portrait or square
        return PerspectiveTransform.rotate(bitmap, 90)
    }

    private fun distance(a: Point2f, b: Point2f): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private suspend fun persistCurrentCapture(
        state: CaptureState.ReadyToCrop,
        appendToDocumentId: String?,
        replacePageId: String? = null,
    ): PersistResult {
        val repo        = documentRepository
        val cryptoStore = imageStore
        val dao         = scanDao
        val context     = appContext

        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis)

        // Resolve target document — either the existing one (append/replace mode)
        // or a freshly-minted one.
        val existingDoc = appendToDocumentId?.let { repo.getDocumentById(it) }
        val isAppend = existingDoc != null
        val replaceTarget = if (replacePageId != null && existingDoc != null) {
            existingDoc.pages.firstOrNull { it.id == replacePageId }
        } else null
        val isReplace = replaceTarget != null

        val documentId = existingDoc?.id ?: UUID.randomUUID().toString()
        val pageId = replaceTarget?.id ?: UUID.randomUUID().toString()
        val nextPageIndex = when {
            replaceTarget != null -> replaceTarget.pageIndex
            else -> existingDoc?.pages?.maxOfOrNull { it.pageIndex }?.plus(1) ?: 0
        }
        // Add a timestamp suffix so replace writes don't collide with the
        // existing on-disk filename. We still update the page's path in DB.
        val pathSuffix = if (isReplace) "_${nowMillis}" else ""
        val imageFile = File(context.filesDir, "scans/$documentId/page_$nextPageIndex$pathSuffix.enc")
        val thumbFile = File(context.filesDir, "scans/$documentId/thumb_$nextPageIndex$pathSuffix.enc")

        val warped = PerspectiveTransform.warp(state.image, state.quad)
        // Shadow / illumination removal runs BEFORE the user-selected filter
        // so all 10 filters inherit a clean uniformly-lit baseline. This is
        // the same approach CamScanner uses — the "clean document" look
        // government-grade exports require comes from normalized lighting,
        // not from a single colour curve. Skipped for ORIGINAL because that
        // filter's contract is "show me the raw photo, nothing applied".
        val deshadowed = if (state.selectedFilter == ImageFilter.ORIGINAL) {
            warped
        } else {
            ImageCleanupProcessor.removeShadow(warped) ?: warped
        }
        // Auto-orient to A4-fit: if a page came out landscape we rotate it to
        // portrait so it fits naturally on an A4 export page. The vast
        // majority of scans (letters, receipts, notes, contracts, IDs) are
        // portrait-oriented; users with genuinely-landscape content can flip
        // it back via the Reader's rotate tool. Skipped for ORIGINAL.
        val oriented = if (state.selectedFilter == ImageFilter.ORIGINAL) {
            deshadowed
        } else {
            autoOrientToPortrait(deshadowed)
        }
        val finalImage = ImageFilterProcessor.apply(oriented, state.selectedFilter)

        val imageBytes = finalImage.toJpegBytes(FULL_IMAGE_QUALITY)
        val thumbnailBytes = finalImage
            .toThumbnailBitmap(THUMB_MAX_WIDTH_PX, THUMB_MAX_HEIGHT_PX)
            .toJpegBytes(THUMB_IMAGE_QUALITY)

        val createdFiles = mutableListOf<File>()
        try {
            cryptoStore.write(imageFile, imageBytes)
            createdFiles += imageFile
            DebugLog.d(
                "Paperkeep.Capture",
                "wrote image=${imageFile.absolutePath} size=${imageFile.length()}",
            )

            cryptoStore.write(thumbFile, thumbnailBytes)
            createdFiles += thumbFile
            DebugLog.d(
                "Paperkeep.Capture",
                "wrote thumb=${thumbFile.absolutePath} size=${thumbFile.length()}",
            )

            val docType = state.classification
                ?.type
                ?.takeIf { it != DocumentType.UNKNOWN }
                ?.key

            if (isReplace) {
                // Retake: update existing page row in place + bump document.
                repo.updateDocument(
                    existingDoc!!.copy(updatedAt = now)
                )
            } else if (!isAppend) {
                val document = Document(
                    id = documentId,
                    title = "Scan $nowMillis",
                    createdAt = now,
                    updatedAt = now,
                    folderId = null,
                    pageCount = 1,
                    colorTag = null,
                    docType = docType,
                    isFavorite = false,
                    isArchived = false,
                    pages = emptyList(),
                )
                repo.saveDocument(document)
            } else {
                val newCount = (existingDoc!!.pageCount).coerceAtLeast(0) + 1
                repo.updateDocument(
                    existingDoc.copy(
                        pageCount = newCount,
                        updatedAt = now,
                    )
                )
            }

            val page = Page(
                id = pageId,
                documentId = documentId,
                pageIndex = nextPageIndex,
                encryptedImagePath = imageFile.absolutePath,
                encryptedThumbPath = thumbFile.absolutePath,
                ocrStatus = OcrStatus.PENDING,
                ocrLanguage = null,
                ocrText = null,
                width = finalImage.width,
                height = finalImage.height,
                filter = state.selectedFilter.key,
                title = replaceTarget?.title,
            )
            if (isReplace) {
                // Update the existing row's paths/dims; reset OCR so it re-runs.
                repo.updatePagePaths(
                    pageId = pageId,
                    imagePath = imageFile.absolutePath,
                    thumbPath = thumbFile.absolutePath,
                    width = finalImage.width,
                    height = finalImage.height,
                )
                repo.updateOcrStatus(pageId, OcrStatus.PENDING, null)
                repo.updateOcrText(pageId, null)
                // Best-effort cleanup of the previous on-disk encrypted files.
                runCatching {
                    cryptoStore.delete(File(replaceTarget!!.encryptedImagePath))
                    cryptoStore.delete(File(replaceTarget.encryptedThumbPath))
                }
            } else {
                repo.savePage(page)
            }
            // FTS refresh is best-effort — on a fresh install the FTS table may not
            // exist until the next app launch triggers the Room onCreate callback.
            runCatching { repo.refreshFtsRow(documentId) }

            if (!isAppend && !isReplace) {
                // Legacy scanner strip still reads from scans; keep it in sync.
                // Append/replace modes don't need a new strip row — the existing doc
                // is already represented.
                dao.insert(
                    ScanEntity(
                        id = documentId,
                        title = "Scan $nowMillis",
                        pageCount = 1,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                        originalPath = imageFile.absolutePath,
                        thumbnailPath = thumbFile.absolutePath,
                        widthPx = finalImage.width,
                        heightPx = finalImage.height,
                    )
                )
            }

            return PersistResult(
                documentId = documentId,
                page = page,
                imageBytes = imageBytes,
            )
        } catch (e: Exception) {
            createdFiles.forEach { file ->
                runCatching { cryptoStore.delete(file) }
            }
            throw e
        }
    }

    private fun Bitmap.toThumbnailBitmap(maxWidthPx: Int, maxHeightPx: Int): Bitmap {
        val srcWidth = width.coerceAtLeast(1)
        val srcHeight = height.coerceAtLeast(1)
        if (srcWidth <= maxWidthPx && srcHeight <= maxHeightPx) return this

        val scale = minOf(
            maxWidthPx / srcWidth.toFloat(),
            maxHeightPx / srcHeight.toFloat(),
        )
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray =
        ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }

    private data class PersistResult(
        val documentId: String,
        val page: Page,
        val imageBytes: ByteArray,
    )

    companion object {
        private const val KEY_QUAD = "capture_quad"
        private const val FULL_IMAGE_QUALITY = 92
        private const val THUMB_IMAGE_QUALITY = 85
        private const val THUMB_MAX_WIDTH_PX = 200
        private const val THUMB_MAX_HEIGHT_PX = 280
        private const val MIN_CONFIDENT_QUAD_AREA = 0.12f
        private const val MAX_CONFIDENT_QUAD_AREA = 0.98f
        private const val MIN_EDGE_SPAN_FRACTION = 0.30f
    }
}
