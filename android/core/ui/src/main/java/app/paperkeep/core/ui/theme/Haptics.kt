package app.paperkeep.core.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Paperkeep semantic haptic tokens — per design spec §7.
 *
 * Every haptic has a meaning. No generic vibrations.
 *
 *  CONFIRM          — shutter fired / action committed
 *  TEXT_HANDLE_MOVE — crop-handle drag tick
 *  LONG_PRESS       — multi-select mode entered
 *  REJECT           — destructive undo expired / invalid action
 */
enum class PaperkeepHaptic {
    CONFIRM,
    TEXT_HANDLE_MOVE,
    LONG_PRESS,
    REJECT,
}

/**
 * Maps a [PaperkeepHaptic] token to the appropriate [HapticFeedbackConstants] int.
 *
 * CONFIRM, TEXT_HANDLE_MOVE, and REJECT require API 30+. On older devices we use
 * the closest semantically equivalent constant available from API 23.
 *
 * Extracted as a pure function so it can be unit-tested without a real device or
 * mocking [Build.VERSION.SDK_INT].
 */
fun hapticConstantFor(token: PaperkeepHaptic, sdkInt: Int = Build.VERSION.SDK_INT): Int =
    when (token) {
        PaperkeepHaptic.CONFIRM ->
            if (sdkInt >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.CONFIRM          // 16, API 30+
            else
                HapticFeedbackConstants.VIRTUAL_KEY      // 1,  API 3+

        PaperkeepHaptic.TEXT_HANDLE_MOVE ->
            if (sdkInt >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.TEXT_HANDLE_MOVE // 9,  API 30+
            else
                HapticFeedbackConstants.CLOCK_TICK       // 4,  API 21+

        PaperkeepHaptic.LONG_PRESS ->
            HapticFeedbackConstants.LONG_PRESS           // 0,  API 3+

        PaperkeepHaptic.REJECT ->
            if (sdkInt >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.REJECT           // 17, API 30+
            else
                HapticFeedbackConstants.VIRTUAL_KEY_RELEASE // 8, API 23+
    }

/**
 * Performs the platform haptic feedback corresponding to [token].
 */
fun View.performHaptic(token: PaperkeepHaptic) {
    performHapticFeedback(hapticConstantFor(token))
}

/**
 * Composable-scoped haptic performer.
 *
 * Usage:
 * ```kotlin
 * val haptic = rememberPaperkeepHaptic()
 * Button(onClick = { haptic(PaperkeepHaptic.CONFIRM) }) { ... }
 * ```
 */
@Composable
fun rememberPaperkeepHaptic(): (PaperkeepHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { token -> view.performHaptic(token) } }
}
