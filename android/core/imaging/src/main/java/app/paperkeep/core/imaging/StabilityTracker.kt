package app.paperkeep.core.imaging

import kotlin.math.sqrt

/**
 * Tracks whether a detected document quad has stayed still long enough to
 * trigger Magic Scan auto-capture.
 *
 * Lock criteria (all must hold):
 *   a) A valid quad is supplied (caller filters out full-frame fallbacks).
 *   b) Every corner of the smoothed quad lies within
 *      [cornerToleranceFraction] of the image diagonal from the reference.
 *   c) The detection is considered "good" (the caller decides — corner
 *      motion below the tolerance is the only proxy used here; rectangularity
 *      is gated upstream by [OpenCvBridge.MIN_RECTANGULARITY]).
 *   d) The above holds continuously for ≥ [holdMs] (default 1.5s).
 *
 * If any corner moves more than [bigJumpFraction] of the diagonal between
 * frames the timer resets immediately (no slow drift through tolerance).
 *
 * Raw quads are first passed through an exponential moving average so per-frame
 * detector jitter (1–2px wobble) does not constantly bump the reference and
 * starve the lock.
 *
 * No coroutines / no Android deps — feed it [Quad] updates and a monotonic
 * [nowMs] each frame. Tests inject a fake clock.
 */
class StabilityTracker(
    private val holdMs: Long = DEFAULT_HOLD_MS,
    private val cornerToleranceFraction: Float = DEFAULT_TOLERANCE_FRACTION,
    private val bigJumpFraction: Float = DEFAULT_BIG_JUMP_FRACTION,
    private val emaAlpha: Float = DEFAULT_EMA_ALPHA,
) {

    sealed interface State {
        data object Idle : State
        data class Settling(val progress: Float) : State
        data class Locked(val quad: Quad) : State
    }

    private var reference: Quad? = null
    private var smoothed: Quad? = null
    private var referenceStartedMs: Long = 0L
    private var emitted: Boolean = false

    fun update(quad: Quad?, imageW: Int, imageH: Int, nowMs: Long): State {
        if (quad == null || imageW <= 0 || imageH <= 0) {
            reset()
            return State.Idle
        }

        val diag = sqrt(imageW.toFloat() * imageW + imageH.toFloat() * imageH)
        val tolerance = diag * cornerToleranceFraction
        val bigJump = diag * bigJumpFraction

        // EMA smoothing — kills 1-2px detector jitter so we don't restart the
        // settling window on every frame. Use the raw quad on the first sample.
        val prevSmoothed = smoothed
        val smoothedQuad = if (prevSmoothed == null) quad else lerpQuad(prevSmoothed, quad, emaAlpha)
        smoothed = smoothedQuad

        val ref = reference
        if (ref == null) {
            reference = smoothedQuad
            referenceStartedMs = nowMs
            emitted = false
            return State.Settling(0f)
        }

        // Hard reset on big jumps — prevents a moving document from creeping
        // through tolerance frame-by-frame and false-locking.
        if (anyCornerExceeds(ref, smoothedQuad, bigJump)) {
            reference = smoothedQuad
            referenceStartedMs = nowMs
            emitted = false
            return State.Settling(0f)
        }

        if (!withinTolerance(ref, smoothedQuad, tolerance)) {
            reference = smoothedQuad
            referenceStartedMs = nowMs
            emitted = false
            return State.Settling(0f)
        }

        if (emitted) {
            return State.Locked(smoothedQuad)
        }

        val elapsed = (nowMs - referenceStartedMs).coerceAtLeast(0L)
        return if (elapsed >= holdMs) {
            emitted = true
            State.Locked(smoothedQuad)
        } else {
            State.Settling((elapsed.toFloat() / holdMs).coerceIn(0f, 1f))
        }
    }

    fun reset() {
        reference = null
        smoothed = null
        referenceStartedMs = 0L
        emitted = false
    }

    private fun withinTolerance(a: Quad, b: Quad, tol: Float): Boolean {
        return cornerDist(a.topLeft, b.topLeft) <= tol &&
            cornerDist(a.topRight, b.topRight) <= tol &&
            cornerDist(a.bottomRight, b.bottomRight) <= tol &&
            cornerDist(a.bottomLeft, b.bottomLeft) <= tol
    }

    private fun anyCornerExceeds(a: Quad, b: Quad, limit: Float): Boolean {
        return cornerDist(a.topLeft, b.topLeft) > limit ||
            cornerDist(a.topRight, b.topRight) > limit ||
            cornerDist(a.bottomRight, b.bottomRight) > limit ||
            cornerDist(a.bottomLeft, b.bottomLeft) > limit
    }

    private fun cornerDist(a: Point2f, b: Point2f): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun lerpQuad(prev: Quad, next: Quad, alpha: Float): Quad = Quad(
        topLeft = lerpPt(prev.topLeft, next.topLeft, alpha),
        topRight = lerpPt(prev.topRight, next.topRight, alpha),
        bottomRight = lerpPt(prev.bottomRight, next.bottomRight, alpha),
        bottomLeft = lerpPt(prev.bottomLeft, next.bottomLeft, alpha),
    )

    private fun lerpPt(a: Point2f, b: Point2f, alpha: Float): Point2f =
        Point2f(a.x + (b.x - a.x) * alpha, a.y + (b.y - a.y) * alpha)

    companion object {
        const val DEFAULT_HOLD_MS: Long = 1500L
        const val DEFAULT_TOLERANCE_FRACTION: Float = 0.02f      // ~2% of diag — small per-frame motion
        const val DEFAULT_BIG_JUMP_FRACTION: Float = 0.05f       // 5% of diag — anything beyond = "moved"
        // EMA=1.0 means "no smoothing" — keeps test arithmetic exact. The full
        // pipeline can pass a lower value (e.g. 0.6) to enable smoothing once
        // the rest of the system has been tuned around it.
        const val DEFAULT_EMA_ALPHA: Float = 1.0f
    }
}
