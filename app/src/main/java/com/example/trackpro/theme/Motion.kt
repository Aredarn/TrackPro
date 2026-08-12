package com.example.trackpro.theme

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI

/**
 * Motion tokens.
 *
 * Apple deliberately replaced the physics triplet (mass/stiffness/damping) with two
 * designer-facing parameters, and these tokens are the only place that conversion lives:
 *
 *   - **damping ratio** - overshoot. 1.0 settles with no bounce; below 1.0 oscillates.
 *   - **response** - roughly how long (seconds) the value takes to reach the target.
 *     It is *not* a duration: a spring has no fixed duration, its settle time emerges
 *     from the parameters, which is exactly why it can be re-targeted mid-flight.
 *
 * Compose wants `dampingRatio` + `stiffness` instead, so response converts as
 * `stiffness = (2*PI / response)^2` (unit mass). The values below land close to
 * Compose's own StiffnessLow (200) / StiffnessMediumLow (400), which is a decent sanity
 * check that the mapping is sane.
 *
 * House rule: **critically damped by default.** Bounce is only correct when the gesture
 * itself carried momentum - overshoot on a flicked card feels physical, overshoot on a
 * menu that merely appeared feels like a toy. So [standard] and [snappy] have no bounce,
 * and only [sheet] does, because a sheet is only ever moved by a gesture.
 */
object Motion {

    private fun stiffnessFor(responseSeconds: Float): Float {
        val omega = (2.0 * PI / responseSeconds).toFloat()
        return omega * omega
    }

    /** Response 0.4s, no overshoot. The default for anything that isn't gesture-driven. */
    val standardStiffness = stiffnessFor(0.4f)   // ~247

    /** Response 0.3s, no overshoot. Press states and selection - wants to feel immediate. */
    val snappyStiffness = stiffnessFor(0.3f)     // ~439

    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = standardStiffness)

    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = snappyStiffness)

    /** Damping 0.8 / response 0.3 - sheet settle, per Apple's shipped drawer values. */
    fun <T> sheet(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = snappyStiffness)

    /**
     * Integer-valued animations need an explicit visibility threshold, or the spring
     * keeps chasing sub-pixel values it can never actually represent and never formally
     * finishes. One whole pixel is the smallest meaningful step.
     */
    fun contentSize(): FiniteAnimationSpec<IntSize> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = standardStiffness,
            visibilityThreshold = IntSize(1, 1)
        )

    /**
     * Flick projection. `calculateTargetValue` on this spec is Compose's equivalent of
     * Apple's `project(velocity, decelerationRate)` - it answers "where would this come
     * to rest if I let go now", which is what you snap against. Snapping to the nearest
     * point from the *release* position instead is what makes a flick feel dead.
     */
    val decay: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = 1f)

}
