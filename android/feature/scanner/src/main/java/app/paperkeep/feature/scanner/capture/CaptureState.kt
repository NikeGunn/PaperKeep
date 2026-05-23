package app.paperkeep.feature.scanner.capture

import android.graphics.Bitmap
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocumentType

/** All possible states of the capture + crop pipeline. */
sealed interface CaptureState {
    /** Camera is live — no capture in progress. */
    data object Idle : CaptureState

    /** Capture triggered, waiting for full-res image from CameraX. */
    data object Capturing : CaptureState

    /** Image captured, edge detection + initial analysis running. */
    data class Processing(val raw: Bitmap) : CaptureState

    /**
     * Processing complete — crop screen is shown.
     *
    * [image]              Captured source bitmap (full resolution).
     * [quad]               Detected / user-adjusted crop corners.
     * [classification]     Result from [DocumentClassifier] — null while still running.
     * [selectedFilter]     Currently selected [ImageFilter] (auto-set from classification,
     *                      can be overridden by the user via the filter strip).
    * [userOverrodeType]   True when the user manually picked a [DocumentType] or filter,
    *                      preventing async classifier updates from overriding choices.
     */
    data class ReadyToCrop(
        val image: Bitmap,
        val quad: Quad,
        val classification: ClassificationResult? = null,
        val selectedFilter: ImageFilter = ImageFilter.AUTO,
        val userOverrodeType: Boolean = false,
    ) : CaptureState

    /** Something went wrong — [message] is for developer logging, not shown to users. */
    data class Error(val message: String) : CaptureState
}

/**
 * Multi-page filter-review state. Used after Google's ML Kit scanner returns
 * one or more cropped pages: the user picks a single [ImageFilter] that
 * applies to every page in the batch, previews it page-by-page, and saves
 * the whole batch in one tap.
 *
 * Kept separate from [CaptureState] because the legacy single-capture
 * pipeline (manual camera → Crop screen) still exists and shouldn't be
 * coupled to the new multi-page flow.
 */
sealed interface FilterReviewState {
    data object Idle : FilterReviewState

    /**
     * [pages]              cropped page bitmaps as ML Kit returned them.
     * [cleanedPages]       same pages with illumination normalisation applied,
     *                      lazily filled on a background thread so the UI is
     *                      responsive. Falls back to [pages] when the cleaned
     *                      version isn't ready yet (or when OpenCV is absent).
     * [currentIndex]       page being shown in the large preview.
     * [selectedFilter]     filter applied to every page on save.
     */
    data class Reviewing(
        val pages: List<Bitmap>,
        val cleanedPages: List<Bitmap?> = List(pages.size) { null },
        val currentIndex: Int = 0,
        val selectedFilter: ImageFilter = ImageFilter.DOCUMENT,
    ) : FilterReviewState {
        /** The image the user actually sees / picks filters against. */
        fun previewSourceAt(index: Int): Bitmap =
            cleanedPages.getOrNull(index) ?: pages[index]
    }

    /** Background persistence in flight. UI shows a spinner. */
    data object Saving : FilterReviewState
}
