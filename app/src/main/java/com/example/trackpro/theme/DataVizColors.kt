package com.example.trackpro.theme

/**
 * Fixed hex colours for MapLibre / MPAndroidChart / Canvas draw calls, which run outside any
 * `@Composable` and cannot read `TrackProTheme.colors`. Each value is a Paddock night token
 * or a timing convention, named in its comment; change both together.
 *
 * The speed heatmap lives in `SpeedColorUtils` and runs green to red by convention.
 */
object DataVizColors {

    // ── Map ────────────────────────────────────────────────
    /** The track itself. (`textPrimary`) */
    const val trackLine = "#F4F5F7"

    /** Track edges. (`sectorLine`, lifted off the tile) */
    const val boundaryLine = "#3A3F4A"

    /** Sector gates. (`accent`) */
    const val sectorMarker = "#FF6B1A"

    /** Start. (`deltaGood`) */
    const val startMarker = "#3DDC84"

    /** Finish. (`danger`) */
    const val endMarker = "#FF4D4F"

    /** Ring behind markers so they read on any tile. (`bgDeep`) */
    const val darkOutline = "#0F1115"

    // ── Two overlaid laps ──────────────────────────────────
    /** The lap under inspection. (`accent`) */
    const val seriesPrimary = "#FF6B1A"

    /** The lap it is compared with: a cool blue, clearly not the accent and not a fault. */
    const val seriesCompare = "#5AB8FF"

    // ── Chart canvas ───────────────────────────────────────
    /** (`bgCard`) */
    const val chartBackground = "#1A1D23"

    /** (`sectorLine`) */
    const val chartGrid = "#2A2E36"

    /** (`textFaint`) */
    const val chartAxisText = "#868B95"

    /** (`accent`) */
    const val chartLine = "#FF6B1A"

    // ── Gauge sweep ────────────────────────────────────────
    /** (`bgElevated`) */
    const val gaugeTrack = "#252932"

    /** (`deltaGood`) */
    const val gaugeLow = "#3DDC84"

    /** (`deltaBad`) */
    const val gaugeMid = "#FFB020"

    /** (`danger`) */
    const val gaugeHigh = "#FF4D4F"

    /** (`textFaint`) */
    const val gaugeTick = "#868B95"
}
