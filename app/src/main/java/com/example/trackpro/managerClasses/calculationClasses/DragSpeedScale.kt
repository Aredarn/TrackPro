package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.managerClasses.utilities.UnitFormatter

/**
 * The speed milestones a drag run is measured against, in the driver's own units.
 *
 * These are not conversions of one another. "0-60" means 0-60 km/h to a metric driver and
 * 0-60 mph to an imperial one, and both are the figure their world quotes - so each system
 * gets the set of milestones that is actually meaningful in it, rather than one set of km/h
 * thresholds wearing whichever label the settings screen happens to be showing. That was the
 * bug this type exists to prevent: the thresholds were hardcoded in km/h and the tiles were
 * labelled with bare numbers, so an imperial driver read "0-60" as the classic 0-60 mph when
 * it was really 0-37 mph.
 *
 * Speeds are stored and compared in km/h everywhere in the app; [toKmh] is the only place
 * these display-unit milestones cross over.
 */
class DragSpeedScale private constructor(
    /** True for km/h milestones, false for mph. */
    val metric: Boolean,
    /** Standing-start milestones, e.g. 0-60. */
    val standingSpeeds: List<Int>,
    /** Rolling milestones measured between two speeds, e.g. 50-150. */
    val rollingSpeeds: List<Pair<Int, Int>>
) {
    val unitLabel: String get() = UnitFormatter.speedUnitLabel(metric)

    val standingLabels: List<String> = standingSpeeds.map { "0-$it" }

    val rollingLabels: List<String> = rollingSpeeds.map { (from, to) -> "$from-$to" }

    /** A milestone in display units as the km/h the GPS actually reports. */
    fun toKmh(displaySpeed: Int): Float =
        UnitFormatter.convertSpeedToKmh(displaySpeed.toDouble(), metric).toFloat()

    companion object {
        /** 0-100 km/h and 100-200 km/h are the figures quoted in metric markets. */
        val Metric = DragSpeedScale(
            metric = true,
            standingSpeeds = listOf(60, 100, 160, 200),
            rollingSpeeds = listOf(50 to 150, 100 to 200)
        )

        /**
         * 0-60 mph and the 60-130 mph roll are the figures quoted in imperial markets.
         * Chosen for being the standard measurements there, not for lining up numerically
         * with the metric set.
         */
        val Imperial = DragSpeedScale(
            metric = false,
            standingSpeeds = listOf(30, 60, 100, 120),
            rollingSpeeds = listOf(40 to 100, 60 to 130)
        )

        fun of(metric: Boolean): DragSpeedScale = if (metric) Metric else Imperial
    }
}
