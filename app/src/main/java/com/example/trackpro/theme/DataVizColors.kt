package com.example.trackpro.theme

/**
 * Fixed hex colours for MapLibre / MPAndroidChart / Canvas draw calls.
 *
 * These run outside any `@Composable` context (Style and Layer objects, View callbacks,
 * DrawScope), so they cannot read `TrackProTheme.colors` and have to be literals. They do
 * not switch with the app's luminance setting: a map tile and a chart canvas are dark in
 * both Night and Day, so these track the Night face only.
 *
 * **They are the dash palette, not a second one.** Every value below is a literal of a
 * token in [NightDashColors] or of the sport's severity convention, and the comment names
 * which. This file previously drifted: the chart line moved to the dash accent while the
 * map layers were left on the old five-colour teal/yellow world, so a lap trace and the
 * chart of that same lap disagreed about what colour "the subject" is. When a token in
 * TrackProColors changes, the matching literal here changes with it.
 *
 * The one deliberate exception is the speed heatmap, which lives in `SpeedColorUtils` and
 * runs green to red by category convention rather than by this palette.
 */
object DataVizColors {

    // ── Map: the track and its furniture ───────────────────
    /** The track itself - the subject of the map, so it takes `marking`. */
    const val trackLine = "#F2F4F3"

    /** Track edges and boundaries - structural, so `bezel` lifted just clear of the tile. */
    const val boundaryLine = "#3A3F42"

    /** Sector gates. Purple, because a sector line is a timing mark. (`accent`) */
    const val sectorMarker = "#B473E8"

    /** Start. Green, per the severity convention. (`deltaGood`) */
    const val startMarker = "#3FD07A"

    /** Finish. Red is reserved for fault elsewhere; on a map it is simply the end. (`danger`) */
    const val endMarker = "#E5453A"

    /** Dark ring behind map markers so they read against any tile. (`panel`) */
    const val darkOutline = "#0A0C0B"

    // ── Two overlaid lap traces ────────────────────────────
    /** The lap under inspection. (`accent`) */
    const val seriesPrimary = "#B473E8"

    /** The lap it is being compared against. (`deltaBad` - the reference, not a fault.) */
    const val seriesCompare = "#E8B33A"

    // ── Chart canvas ───────────────────────────────────────
    /** (`field`) */
    const val chartBackground = "#14171A"

    /** (`bezel`) */
    const val chartGrid = "#2A2F31"

    /** (`markingDim`) */
    const val chartAxisText = "#9AA1A4"

    /** (`accent`) */
    const val chartLine = "#B473E8"

    // ── Gauge sweep ────────────────────────────────────────
    // Climbs through the severity convention as speed rises, so the sweep itself says
    // "how hard are you going" without a legend.
    /** (`bgElevated`) */
    const val gaugeTrack = "#1E2225"

    /** (`deltaGood`) */
    const val gaugeLow = "#3FD07A"

    /** (`deltaBad`) */
    const val gaugeMid = "#E8B33A"

    /** (`danger`) */
    const val gaugeHigh = "#E5453A"

    /** (`textFaint`) */
    const val gaugeTick = "#5E6669"
}
