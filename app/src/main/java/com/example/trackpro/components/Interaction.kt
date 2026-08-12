package com.example.trackpro.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion

/**
 * True when the user has asked the system to remove animations.
 *
 * Android has no direct equivalent of the web's `prefers-reduced-motion`; the honest
 * signal is the global animator duration scale, which is what Developer Options'
 * "Animator duration scale: off" and most accessibility tooling actually set.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * A press affordance that responds on pointer-**down**.
 *
 * Material's ripple only confirms a press after the fact; the moment feedback waits for
 * release, directness falls off a cliff. This watches the interaction source directly,
 * so the surface starts shrinking the instant a finger lands and springs back the
 * instant it lifts. Because it's a spring rather than a fixed transition, a rapidly
 * repeated press re-targets from wherever the scale currently *is* instead of jumping
 * back to 1.0 first.
 *
 * **Ordering matters: apply this before `.background()`/`.border()`.**
 * `graphicsLayer` only transforms what comes after it in the chain, so
 * `Modifier.pressable(...).background(...)` scales the filled surface, while
 * `.background(...).pressable(...)` would scale only the content inside it and leave the
 * background stationary. The ripple is deliberately disabled (`indication = null`) - the
 * scale *is* the feedback, and keeping both would also force `background` before
 * `clickable`, which is the opposite of the order the transform needs.
 *
 * [scale] is calibrated to surface size: large cards want ~0.98 (2% of a big surface is
 * already a lot of travel), small controls want ~0.96 to register at all.
 *
 * Under reduced motion the transform is replaced by a brief opacity dip - non-vestibular,
 * so it stays within the accessibility contract while still confirming the touch.
 *
 * Haptics are opt-in and default to off: press feedback fires on every tap in the app,
 * and a haptic on every tap is exactly the over-feedback that trains people to ignore
 * haptics entirely. Reserve it for commits.
 */
fun Modifier.pressableRow(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic? = null,
    role: Role? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberHaptics()

    val highlight by animateColorAsState(
        targetValue = if (pressed && enabled) {
            TrackProTheme.colors.textPrimary.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        animationSpec = Motion.snappy(),
        label = "pressHighlight"
    )

    // Background before clickable here - this one tints rather than transforms, so the
    // ordering constraint that pressable() has doesn't apply.
    this
        .background(highlight)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role
        ) {
            haptic?.let { haptics.perform(it) }
            onClick()
        }
}

/**
 * [onLongClick] opts the surface into `combinedClickable`. It always fires a
 * [Haptic.LongPress] tick, because a long-press has no visual "it worked" moment of its
 * own - the finger is still down and nothing has moved yet, so the tick is the only
 * signal that the gesture registered and can be released.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    scale: Float = 0.98f,
    haptic: Haptic? = null,
    role: Role? = null,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val haptics = rememberHaptics()

    val active = pressed && enabled
    val targetScale = if (active && !reducedMotion) scale else 1f
    val targetAlpha = if (active && reducedMotion) 0.75f else 1f

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = Motion.snappy(),
        label = "pressScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = Motion.snappy(),
        label = "pressAlpha"
    )

    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            alpha = animatedAlpha
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { handler ->
                {
                    haptics.perform(Haptic.LongPress)
                    handler()
                }
            },
            onClick = {
                haptic?.let { haptics.perform(it) }
                onClick()
            }
        )
}
