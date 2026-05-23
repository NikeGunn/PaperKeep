package app.paperkeep.feature.scanner.edge

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.StabilityTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the live edge-detection overlay AND the Magic Scan auto-capture
 * signal.
 *
 * For every preview frame:
 *   1. Run [EdgeDetector] on the bitmap (off-main).
 *   2. Feed the result + monotonic time into [StabilityTracker].
 *   3. Translate the tracker state into an [EdgeOverlayState] for the overlay.
 *   4. When the tracker transitions to [StabilityTracker.State.Locked], emit
 *      one event on [autoCaptureSignals] so the screen can fire the shutter.
 *
 * The auto-capture event is emitted exactly once per stability "lock" —
 * subsequent locked frames do not re-emit. [resetStability] clears the
 * tracker (called after the shutter fires or when the user retakes).
 */
@HiltViewModel
class EdgeDetectionViewModel @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    // Production EMA = 0.6: each frame is 60% of the new detection + 40% of the
    // smoothed previous quad. Cuts per-frame jitter without lagging real motion.
    // Tests construct their own tracker via the default ctor (alpha=1.0 → no
    // smoothing → exact arithmetic).
    private val tracker = StabilityTracker(emaAlpha = 0.6f)
    private var lastEmittedLockedAt: Long = -1L

    private val _overlayState = MutableStateFlow<EdgeOverlayState>(EdgeOverlayState.None)
    val overlayState: StateFlow<EdgeOverlayState> = _overlayState.asStateFlow()

    private val _autoCaptureSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoCaptureSignals: SharedFlow<Unit> = _autoCaptureSignals.asSharedFlow()

    /** Allows tests / live frames to override the clock source. */
    var clockMs: () -> Long = { System.currentTimeMillis() }

    fun processFrame(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            tracker.reset()
            _overlayState.value = EdgeOverlayState.None
            return
        }

        viewModelScope.launch(dispatchers.default) {
            val w = bitmap.width
            val h = bitmap.height
            val result = edgeDetector.detect(bitmap)
            val found = result as? DetectionResult.Found
            val quad = found?.corners
            val confidence = found?.confidence ?: 0f
            val trackerState = tracker.update(quad, w, h, clockMs())

            // Strong-confidence cutoff. Below this we still show the quad — but
            // in yellow so the user sees that detection is tentative. Picked
            // around the OpenCV bridge MIN_ACCEPT_SCORE plus headroom.
            val strong = confidence >= STRONG_CONFIDENCE_THRESHOLD

            val overlay = when (trackerState) {
                StabilityTracker.State.Idle -> EdgeOverlayState.None
                is StabilityTracker.State.Settling -> {
                    if (quad != null) {
                        EdgeOverlayState.Settling(quad, trackerState.progress, confidence)
                    } else EdgeOverlayState.None
                }
                is StabilityTracker.State.Locked -> EdgeOverlayState.Locked(trackerState.quad)
            }
            @Suppress("UNUSED_VARIABLE") val _strong = strong // kept for future overlay routing

            withContext(dispatchers.main) {
                _overlayState.value = overlay
                if (overlay is EdgeOverlayState.Locked) {
                    val now = clockMs()
                    if (now != lastEmittedLockedAt) {
                        lastEmittedLockedAt = now
                        _autoCaptureSignals.tryEmit(Unit)
                    }
                }
            }
        }
    }

    /** Reset both the tracker and the lock-edge-emit guard. Call after shutter. */
    fun resetStability() {
        tracker.reset()
        lastEmittedLockedAt = -1L
        _overlayState.value = EdgeOverlayState.None
        latestAnalysisFrame = null
    }

    // ── Tap-to-detect ────────────────────────────────────────────────────────
    //
    // The scanner screen caches the latest analysis frame via [cacheFrame]
    // instead of feeding every frame to [processFrame]. When the user taps
    // the viewfinder, [onTap] runs the detector against that cached frame,
    // anchored to the tap point in bitmap coordinates. The result is rendered
    // by the overlay just like an auto-detected quad.

    @Volatile private var latestAnalysisFrame: Bitmap? = null

    /** Called from the camera analyzer to keep the most recent frame around. */
    fun cacheFrame(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        latestAnalysisFrame = bitmap
    }

    /**
     * User tapped the viewfinder at ([bitmapX], [bitmapY]) in the analysis
     * bitmap's coordinate space. Runs tap-anchored detection on the cached
     * frame and updates the overlay.
     */
    fun onTap(bitmapX: Float, bitmapY: Float) {
        val bitmap = latestAnalysisFrame ?: return
        viewModelScope.launch(dispatchers.default) {
            val result = edgeDetector.detectAt(bitmap, bitmapX, bitmapY)
            val overlay = when (result) {
                is DetectionResult.Found -> EdgeOverlayState.Locked(result.corners)
                DetectionResult.NotFound -> EdgeOverlayState.Partial
            }
            withContext(dispatchers.main) {
                _overlayState.value = overlay
            }
        }
    }

    /** Force the overlay to "partial" state — contours exist but no clean quad. */
    fun markPartial() {
        _overlayState.value = EdgeOverlayState.Partial
    }

    companion object {
        // Confidence above which a detection is rendered green (strong).
        // Below this and the overlay falls back to yellow (weak).
        private const val STRONG_CONFIDENCE_THRESHOLD = 0.55f
    }
}
