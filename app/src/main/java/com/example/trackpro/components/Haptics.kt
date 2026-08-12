package com.example.trackpro.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptic vocabulary.
 *
 * Compose's own `LocalHapticFeedback` only exposes `LongPress` and `TextHandleMove`,
 * which isn't enough to distinguish "you selected something" from "that committed" from
 * "that failed", so this goes through the platform [View] constants instead.
 *
 * Three rules govern where these are allowed to fire:
 *  - **Causality** - fire on the actual causal event (the toggle flipping, the lap
 *    closing), not on some later callback, and match the character to the action.
 *  - **Harmony** - fire on the same frame as the visual change. A haptic that trails its
 *    animation reads as two separate events.
 *  - **Utility** - only meaningful moments. Haptics on every tap trains people to stop
 *    noticing all of them, which costs you the ones that matter.
 */
enum class Haptic {
    /** Light tick - discrete selection changed (segmented chip, picker row). */
    Selection,

    /** Something committed successfully - session started, sector marked, lap closed. */
    Confirm,

    /** An action was refused or destructive-and-cancelled. */
    Reject,

    /** A dragged surface snapped home. */
    Snap,

    /** A long-press was recognised - the "you may now let go" tick. */
    LongPress
}

@Composable
fun rememberHaptics(): HapticPerformer {
    val view = LocalView.current
    return remember(view) { HapticPerformer(view) }
}

class HapticPerformer(private val view: View) {

    fun perform(haptic: Haptic) {
        val constant = when (haptic) {
            Haptic.Selection -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.Snap -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS

            // CONFIRM/REJECT only exist from API 30; below that fall back to the
            // closest universally-available feel rather than dropping the feedback.
            Haptic.Confirm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY

            Haptic.Reject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
