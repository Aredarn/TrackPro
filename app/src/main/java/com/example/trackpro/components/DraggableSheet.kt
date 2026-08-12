package com.example.trackpro.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A bottom sheet you can actually grab.
 *
 * Replaces the previous `AnimatedVisibility(visible = someBoolean)` sheets, which drew a
 * grab handle that did nothing - the UI promised a gesture it didn't implement.
 *
 * The four things that make this feel physical rather than merely animated:
 *
 *  1. **1:1 tracking.** While dragging, the sheet moves exactly with the finger. No
 *     easing, no lag, no "animate once the gesture completes".
 *  2. **Momentum projection.** On release, [Motion.decay]'s `calculateTargetValue`
 *     answers "where would this come to rest if I let go now"; the dismiss/settle
 *     decision is made against *that*, not against where the finger happened to stop.
 *     A fast flick from near the top still dismisses.
 *  3. **Velocity handoff.** The settle spring starts at the finger's release velocity, so
 *     there is no visible seam between dragging and animating.
 *  4. **Rubber-banding.** Dragging up past the open position meets progressive
 *     resistance instead of a hard stop - a wall reads as "frozen", resistance reads as
 *     "responsive, but there's nothing more here".
 *
 * Everything is driven by one [Animatable], so a sheet caught mid-flight is simply
 * re-targeted from its current on-screen value: you can grab a closing sheet and pull it
 * back open without waiting for it to finish. That interruptibility is the whole point.
 */
@Composable
fun DraggableSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reducedMotion = rememberReducedMotion()

    // Offset in px from the resting (open) position; larger = further off-screen.
    // Starts parked well off-screen so the very first frame - before the sheet has been
    // measured - doesn't flash it at rest position.
    val offsetY = remember { Animatable(OffscreenParkPx) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible, sheetHeight) {
        if (sheetHeight <= 0f) return@LaunchedEffect
        val target = if (visible) 0f else sheetHeight
        when {
            // Not yet measured on first composition - jump, don't animate from 10,000px.
            offsetY.value == OffscreenParkPx -> offsetY.snapTo(target)
            reducedMotion -> offsetY.snapTo(target)
            else -> offsetY.animateTo(target, Motion.sheet())
        }
    }

    // Deliberately stays in composition while dismissed. Removing it (as the previous
    // AnimatedVisibility did) is what makes a sheet un-grabbable mid-flight; keeping it
    // means a closing sheet can be caught and pulled back open at any instant.

    val settle: (Float) -> Unit = { velocity ->
        scope.launch {
            // Where would it come to rest on its own? Decide against that, not against
            // the release position.
            val projected = Motion.decay.calculateTargetValue(offsetY.value, velocity)
            val shouldDismiss = projected > sheetHeight * 0.4f
            if (shouldDismiss) {
                haptics.perform(Haptic.Snap)
                // Hand the finger's velocity straight to the spring - no seam.
                offsetY.animateTo(sheetHeight, Motion.sheet(), initialVelocity = velocity)
                onDismiss()
            } else {
                haptics.perform(Haptic.Snap)
                offsetY.animateTo(0f, Motion.sheet(), initialVelocity = velocity)
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val raw = offsetY.value + delta
            // Past the open position, resist progressively instead of stopping dead.
            val resolved = if (raw < 0f) rubberBand(raw, sheetHeight) else raw
            offsetY.snapTo(resolved)
        }
    }

    // Lets a sheet that contains its own scrollable behave correctly: the inner list
    // scrolls until it hits the top, and only then does further downward drag start
    // moving the sheet. Without this the two fight and the sheet slides away while the
    // list is still mid-scroll.
    val nestedScroll = remember(sheetHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging up while the sheet is off its rest position: close the gap first.
                return if (available.y < 0f && offsetY.value > 0f) {
                    val delta = maxOf(available.y, -offsetY.value)
                    scope.launch { offsetY.snapTo(offsetY.value + delta) }
                    Offset(0f, delta)
                } else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Only what the list left over, and only downward.
                return if (available.y > 0f) {
                    scope.launch { offsetY.snapTo(offsetY.value + available.y) }
                    Offset(0f, available.y)
                } else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (offsetY.value > 0f) {
                    settle(available.y)
                    available
                } else Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { sheetHeight = it.height.toFloat() }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            // These sheets float over a MapLibre AndroidView with all gestures enabled.
            // A Compose surface that only has a background doesn't consume touches, so
            // without this a drag on the sheet body would fall straight through and pan
            // the map underneath. Runs on the Main pass, which is dispatched child ->
            // parent, so the inner list and buttons still get first refusal.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            }
            .nestedScroll(nestedScroll)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    TrackProTheme.colors.bgCard,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
        ) {
            // The handle is the drag affordance, so it's also the drag *target* - a
            // sheet whose body scrolls shouldn't be draggable from anywhere.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            TrackProTheme.colors.textMuted.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
            content()
        }
    }
}

/**
 * Progressive resistance past a boundary, matching the standard iOS feel: the further
 * you pull, the less the surface follows.
 */
private fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
    if (dimension <= 0f) return overshoot
    val magnitude = abs(overshoot)
    return -(magnitude * dimension * constant) / (dimension + constant * magnitude)
}


/** Far enough off-screen for any phone, used only before the sheet has been measured. */
private const val OffscreenParkPx = 10_000f
