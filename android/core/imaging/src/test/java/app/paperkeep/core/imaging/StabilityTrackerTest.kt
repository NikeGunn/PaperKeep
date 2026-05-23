package app.paperkeep.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityTrackerTest {

    private val w = 1000
    private val h = 1000

    private fun quad(x: Float = 200f, y: Float = 200f, side: Float = 600f): Quad = Quad(
        topLeft = Point2f(x, y),
        topRight = Point2f(x + side, y),
        bottomRight = Point2f(x + side, y + side),
        bottomLeft = Point2f(x, y + side),
    )

    @Test
    fun nullQuadIsAlwaysIdle() {
        val tracker = StabilityTracker()
        assertEquals(StabilityTracker.State.Idle, tracker.update(null, w, h, 0L))
        assertEquals(StabilityTracker.State.Idle, tracker.update(null, w, h, 5000L))
    }

    @Test
    fun firstDetectedFrameIsSettlingZero() {
        val tracker = StabilityTracker()
        val state = tracker.update(quad(), w, h, 0L)
        assertTrue(state is StabilityTracker.State.Settling)
        assertEquals(0f, (state as StabilityTracker.State.Settling).progress, 0.0001f)
    }

    @Test
    fun progressClimbsLinearlyAcrossHoldWindow() {
        val tracker = StabilityTracker(holdMs = 1000L)
        val q = quad()
        tracker.update(q, w, h, 0L)
        val mid = tracker.update(q, w, h, 500L)
        assertTrue(mid is StabilityTracker.State.Settling)
        assertEquals(0.5f, (mid as StabilityTracker.State.Settling).progress, 0.01f)
    }

    @Test
    fun heldStillBeyondHoldEmitsLocked() {
        val tracker = StabilityTracker(holdMs = 1000L)
        val q = quad()
        tracker.update(q, w, h, 0L)
        val locked = tracker.update(q, w, h, 1100L)
        assertTrue(locked is StabilityTracker.State.Locked)
        assertEquals(q, (locked as StabilityTracker.State.Locked).quad)
    }

    @Test
    fun smallJitterDoesNotResetTimer() {
        val tracker = StabilityTracker(holdMs = 1000L, cornerToleranceFraction = 0.02f)
        val q1 = quad()
        // Move every corner by ~5px on a 1000-px image; diagonal=1414, 0.02 → 28px tolerance.
        val q2 = Quad(
            topLeft = Point2f(205f, 200f),
            topRight = Point2f(795f, 205f),
            bottomRight = Point2f(800f, 805f),
            bottomLeft = Point2f(195f, 800f),
        )
        tracker.update(q1, w, h, 0L)
        val state = tracker.update(q2, w, h, 1100L)
        assertTrue("Small jitter must still progress to Locked", state is StabilityTracker.State.Locked)
    }

    @Test
    fun bigMovementResetsProgressToZero() {
        val tracker = StabilityTracker(holdMs = 1000L, cornerToleranceFraction = 0.02f)
        val q1 = quad(x = 100f, y = 100f, side = 600f)
        // Move every corner ~200px — way beyond tolerance.
        val q2 = quad(x = 300f, y = 300f, side = 600f)
        tracker.update(q1, w, h, 0L)
        val state = tracker.update(q2, w, h, 500L)
        assertTrue(state is StabilityTracker.State.Settling)
        assertEquals(0f, (state as StabilityTracker.State.Settling).progress, 0.0001f)
    }

    @Test
    fun lockedStaysLockedAcrossNextFramesUntilReset() {
        val tracker = StabilityTracker(holdMs = 1000L)
        val q = quad()
        tracker.update(q, w, h, 0L)
        val locked = tracker.update(q, w, h, 1500L)
        assertTrue(locked is StabilityTracker.State.Locked)

        val again = tracker.update(q, w, h, 1600L)
        assertTrue("Once locked the tracker stays locked while held", again is StabilityTracker.State.Locked)

        tracker.reset()
        val afterReset = tracker.update(q, w, h, 1700L)
        assertTrue("After reset() the tracker starts a fresh settling window",
            afterReset is StabilityTracker.State.Settling)
        assertEquals(0f, (afterReset as StabilityTracker.State.Settling).progress, 0.0001f)
    }

    @Test
    fun nullFrameClearsReference() {
        val tracker = StabilityTracker(holdMs = 1000L)
        val q = quad()
        tracker.update(q, w, h, 0L)
        tracker.update(null, w, h, 500L) // gap
        val state = tracker.update(q, w, h, 600L)
        assertTrue(state is StabilityTracker.State.Settling)
        assertEquals(
            "Null frame must restart the settling window",
            0f,
            (state as StabilityTracker.State.Settling).progress,
            0.0001f,
        )
    }

    @Test
    fun zeroDimensionInputIsIdle() {
        val tracker = StabilityTracker()
        assertEquals(StabilityTracker.State.Idle, tracker.update(quad(), 0, 100, 0L))
        assertEquals(StabilityTracker.State.Idle, tracker.update(quad(), 100, 0, 0L))
    }

    @Test
    fun progressMonotonicallyIncreasesWhileStill() {
        val tracker = StabilityTracker(holdMs = 1000L)
        val q = quad()
        tracker.update(q, w, h, 0L)
        val a = tracker.update(q, w, h, 200L) as StabilityTracker.State.Settling
        val b = tracker.update(q, w, h, 400L) as StabilityTracker.State.Settling
        val c = tracker.update(q, w, h, 800L) as StabilityTracker.State.Settling
        assertTrue(a.progress < b.progress)
        assertTrue(b.progress < c.progress)
    }

    @Test
    fun differentToleranceParameterChangesSensitivity() {
        val tight = StabilityTracker(holdMs = 1000L, cornerToleranceFraction = 0.005f) // 7px on 1414
        val loose = StabilityTracker(holdMs = 1000L, cornerToleranceFraction = 0.1f)   // 141px on 1414

        val q1 = quad(x = 200f, y = 200f, side = 600f)
        // Move 20px on every corner
        val q2 = quad(x = 220f, y = 220f, side = 600f)

        tight.update(q1, w, h, 0L)
        val tightState = tight.update(q2, w, h, 1100L)
        assertNotEquals("Tight tolerance must reset on a 20px move",
            StabilityTracker.State.Locked::class, tightState::class)

        loose.update(q1, w, h, 0L)
        val looseState = loose.update(q2, w, h, 1100L)
        assertTrue("Loose tolerance must accept a 20px move and progress to Locked",
            looseState is StabilityTracker.State.Locked)
    }
}
