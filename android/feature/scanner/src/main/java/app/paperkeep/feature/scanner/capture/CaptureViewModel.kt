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
     * Saves the current crop result as a new document, then invokes [onSaved]
     * with the created document id.
     */
    fun saveCurrentCapture(onSaved: (String) -> Unit = {}) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        DebugLog.d("Paperkeep.Capture", "saveCurrentCapture: start")
        _state.value = CaptureState.Processing(current.image)

        viewModelScope.launch(dispatchers.io) {
            try {
                val saveResult = persistCurrentCapture(current)
                DebugLog.d(
                    "Paperkeep.Capture",
                    "saveCurrentCapture: persisted doc=${saveResult.documentId} " +
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

    private fun distance(a: Point2f, b: Point2f): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private suspend fun persistCurrentCapture(state: CaptureState.ReadyToCrop): PersistResult {
        val repo        = documentRepository
        val cryptoStore = imageStore
        val dao         = scanDao
        val context     = appContext

        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis)
        val documentId = UUID.randomUUID().toString()
        val pageId = UUID.randomUUID().toString()

        val imageFile = File(context.filesDir, "scans/$documentId/page_0.enc")
        val thumbFile = File(context.filesDir, "scans/$documentId/thumb_0.enc")

        val warped = PerspectiveTransform.warp(state.image, state.quad)
        val finalImage = ImageFilterProcessor.apply(warped, state.selectedFilter)

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

            val page = Page(
                id = pageId,
                documentId = documentId,
                pageIndex = 0,
                encryptedImagePath = imageFile.absolutePath,
                encryptedThumbPath = thumbFile.absolutePath,
                ocrStatus = OcrStatus.PENDING,
                ocrLanguage = null,
                ocrText = null,
                width = finalImage.width,
                height = finalImage.height,
                filter = state.selectedFilter.key,
            )
            repo.savePage(page)
            // FTS refresh is best-effort — on a fresh install the FTS table may not
            // exist until the next app launch triggers the Room onCreate callback.
            runCatching { repo.refreshFtsRow(documentId) }

            // Legacy scanner strip still reads from scans; keep it in sync.
            dao.insert(
                ScanEntity(
                    id = documentId,
                    title = document.title,
                    pageCount = 1,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                    originalPath = imageFile.absolutePath,
                    thumbnailPath = thumbFile.absolutePath,
                    widthPx = finalImage.width,
                    heightPx = finalImage.height,
                )
            )

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
