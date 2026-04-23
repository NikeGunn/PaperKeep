package app.paperkeep.core.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verifies the Paperkeep haptic token spec (§7) by testing [hapticConstantFor] directly.
 * This avoids Build.VERSION.SDK_INT ambiguity in JVM tests.
 *
 *  On API 30+: CONFIRM→16, TEXT_HANDLE_MOVE→9, LONG_PRESS→0, REJECT→17
 *  On API <30: fallback to closest available constant
 */
class HapticsTest {

    // ── API 30+ (modern path) ─────────────────────────────────────────────────

    @Test
    fun confirm_api30_mapsToConfirmConstant() {
        assertEquals(
            "CONFIRM on API 30 must use HapticFeedbackConstants.CONFIRM (16)",
            HapticFeedbackConstants.CONFIRM,
            hapticConstantFor(PaperkeepHaptic.CONFIRM, sdkInt = 30),
        )
    }

    @Test
    fun textHandleMove_api30_mapsToTextHandleMoveConstant() {
        assertEquals(
            "TEXT_HANDLE_MOVE on API 30 must use HapticFeedbackConstants.TEXT_HANDLE_MOVE (9)",
            HapticFeedbackConstants.TEXT_HANDLE_MOVE,
            hapticConstantFor(PaperkeepHaptic.TEXT_HANDLE_MOVE, sdkInt = 30),
        )
    }

    @Test
    fun longPress_allApis_mapsToLongPressConstant() {
        // LONG_PRESS is available since API 3 — no fallback needed
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(PaperkeepHaptic.LONG_PRESS, sdkInt = 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(PaperkeepHaptic.LONG_PRESS, sdkInt = 30))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(PaperkeepHaptic.LONG_PRESS, sdkInt = 34))
    }

    @Test
    fun reject_api30_mapsToRejectConstant() {
        assertEquals(
            "REJECT on API 30 must use HapticFeedbackConstants.REJECT (17)",
            HapticFeedbackConstants.REJECT,
            hapticConstantFor(PaperkeepHaptic.REJECT, sdkInt = 30),
        )
    }

    // ── API < 30 fallback path ────────────────────────────────────────────────

    @Test
    fun confirm_api26_fallsBackToVirtualKey() {
        assertEquals(
            "CONFIRM on API 26 must fall back to VIRTUAL_KEY",
            HapticFeedbackConstants.VIRTUAL_KEY,
            hapticConstantFor(PaperkeepHaptic.CONFIRM, sdkInt = 26),
        )
    }

    @Test
    fun textHandleMove_api26_fallsBackToClockTick() {
        assertEquals(
            "TEXT_HANDLE_MOVE on API 26 must fall back to CLOCK_TICK",
            HapticFeedbackConstants.CLOCK_TICK,
            hapticConstantFor(PaperkeepHaptic.TEXT_HANDLE_MOVE, sdkInt = 26),
        )
    }

    @Test
    fun reject_api26_fallsBackToVirtualKeyRelease() {
        assertEquals(
            "REJECT on API 26 must fall back to VIRTUAL_KEY_RELEASE",
            HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
            hapticConstantFor(PaperkeepHaptic.REJECT, sdkInt = 26),
        )
    }

    // ── View.performHaptic integration ───────────────────────────────────────

    @Test
    fun performHaptic_invokesExactlyOneHapticFeedbackCall() {
        PaperkeepHaptic.entries.forEach { token ->
            val view: View = mockk(relaxed = true) {
                every { performHapticFeedback(any()) } returns true
            }
            view.performHaptic(token)
            verify(exactly = 1) { view.performHapticFeedback(any()) }
        }
    }

    // ── Token set integrity ───────────────────────────────────────────────────

    @Test
    fun allFourTokensAreDefined() {
        val expected = setOf("CONFIRM", "TEXT_HANDLE_MOVE", "LONG_PRESS", "REJECT")
        val actual = PaperkeepHaptic.entries.map { it.name }.toSet()
        assertEquals("Haptic token set must match spec §7", expected, actual)
    }

    @Test
    fun api30Constants_areAllDistinct() {
        val constants = PaperkeepHaptic.entries.map { hapticConstantFor(it, sdkInt = 30) }
        assertEquals(
            "Every haptic token must map to a distinct platform constant on API 30",
            constants.size,
            constants.toSet().size,
        )
    }

    @Test
    fun api26Constants_areAllDistinct() {
        val constants = PaperkeepHaptic.entries.map { hapticConstantFor(it, sdkInt = 26) }
        assertEquals(
            "Every haptic token must map to a distinct platform constant on API 26",
            constants.size,
            constants.toSet().size,
        )
    }

    @Test
    fun modernAndLegacyMappings_differ_forSensitiveTokens() {
        // Confirm, TextHandleMove, Reject have API-specific constants
        assertNotEquals(
            hapticConstantFor(PaperkeepHaptic.CONFIRM, sdkInt = 30),
            hapticConstantFor(PaperkeepHaptic.CONFIRM, sdkInt = 26),
        )
        assertNotEquals(
            hapticConstantFor(PaperkeepHaptic.TEXT_HANDLE_MOVE, sdkInt = 30),
            hapticConstantFor(PaperkeepHaptic.TEXT_HANDLE_MOVE, sdkInt = 26),
        )
        assertNotEquals(
            hapticConstantFor(PaperkeepHaptic.REJECT, sdkInt = 30),
            hapticConstantFor(PaperkeepHaptic.REJECT, sdkInt = 26),
        )
    }

    @Test
    fun longPress_isSameOnAllApis() {
        // LONG_PRESS never needs a fallback — same constant across all supported API levels
        assertEquals(
            hapticConstantFor(PaperkeepHaptic.LONG_PRESS, sdkInt = 26),
            hapticConstantFor(PaperkeepHaptic.LONG_PRESS, sdkInt = 30),
        )
    }
}
