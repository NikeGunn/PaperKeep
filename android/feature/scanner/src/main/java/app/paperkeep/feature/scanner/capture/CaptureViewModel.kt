package app.paperkeep.feature.scanner.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
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
                val quad = when (result) {
                    is DetectionResult.Found -> result.corners
                    DetectionResult.NotFound -> fullImageQuad(bitmap)
                }
                val warped = PerspectiveTransform.warp(bitmap, quad)

                withContext(dispatchers.main) {
                    persistQuad(quad)
                    _state.value = CaptureState.ReadyToCrop(
                        image = warped,
                        quad = quad,
                        classification = null,
                        selectedFilter = ImageFilter.AUTO,
                    )
                }

                // Classification runs concurrently — does not block the crop screen
                val classResult = classifier.classify(warped)
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
        viewModelScope.launch(dispatchers.default) {
            val rewarped = PerspectiveTransform.warp(current.image, newQuad)
            withContext(dispatchers.main) {
                persistQuad(newQuad)
                _state.value = current.copy(image = rewarped, quad = newQuad)
            }
        }
    }

    fun rotateImage() {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        viewModelScope.launch(dispatchers.default) {
            val rotated = PerspectiveTransform.rotate(current.image, 90)
            withContext(dispatchers.main) {
                _state.value = current.copy(image = rotated)
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
     * Sets [CaptureState.ReadyToCrop.userOverrodeType] = false because this only
     * changes the filter, not the document type.
     */
    fun onFilterSelected(filter: ImageFilter) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        _state.value = current.copy(selectedFilter = filter)
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
        _state.value = CaptureState.Processing(current.image)

        viewModelScope.launch(dispatchers.io) {
            try {
                val saveResult = persistCurrentCapture(current)

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

    private fun fullImageQuad(bitmap: Bitmap): Quad {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return Quad(
            topLeft = Point2f(0f, 0f),
            topRight = Point2f(w, 0f),
            bottomRight = Point2f(w, h),
            bottomLeft = Point2f(0f, h),
        )
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

        val imageBytes = state.image.toJpegBytes(FULL_IMAGE_QUALITY)
        val thumbnailBytes = state.image
            .toThumbnailBitmap(THUMB_MAX_EDGE_PX)
            .toJpegBytes(THUMB_IMAGE_QUALITY)

        val createdFiles = mutableListOf<File>()
        try {
            cryptoStore.write(imageFile, imageBytes)
            createdFiles += imageFile

            cryptoStore.write(thumbFile, thumbnailBytes)
            createdFiles += thumbFile

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
                width = state.image.width,
                height = state.image.height,
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
                    widthPx = state.image.width,
                    heightPx = state.image.height,
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

    private fun Bitmap.toThumbnailBitmap(maxEdgePx: Int): Bitmap {
        val maxDimension = maxOf(width, height).coerceAtLeast(1)
        if (maxDimension <= maxEdgePx) return this

        val scale = maxEdgePx / maxDimension.toFloat()
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
        private const val THUMB_MAX_EDGE_PX = 256
    }
}
