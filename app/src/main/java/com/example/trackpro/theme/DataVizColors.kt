package com.example.trackpro.theme

/**
 * Fixed hex colors for MapLibre/MPAndroidChart/Canvas draw calls. These run outside any
 * @Composable context (Style/Layer objects, View callbacks, DrawScope), so they can't read
 * TrackProTheme.colors. Track polylines, chart grids and the speedometer gauge always
 * render against a dark map tile / dark chart background regardless of the app's
 * light/dark setting, so these intentionally don't switch with the app theme - values
 * mirror the dark-mode palette.
 *
 * Sourced from the same 5-color palette: #16262E / #2E4756 / #3C7A89 / #9FA2B2 / #FEEA00.
 * Teal carries the routine lines, yellow marks the things worth looking at (the speed
 * trace, sector boundaries, the compared lap).
 */
object DataVizColors {
    const val trackLine = "#3C7A89"
    const val startMarker = "#2FBF71"
    const val endMarker = "#EF4E3A"
    const val sectorMarker = "#FEEA00"
    const val boundaryLine = "#9FA2B2"

    /** Two overlaid GPS traces on the lap-compare map: teal reference vs yellow compare. */
    const val seriesPrimary = "#3C7A89"
    const val seriesCompare = "#FEEA00"

    const val chartBackground = "#1D3038"
    const val chartGrid = "#2E4756"
    const val chartAxisText = "#9FA2B2"
    const val chartLine = "#FEEA00"

    // Gauge sweeps teal -> yellow -> red as speed climbs, so the palette itself encodes
    // "how hard are you going" without needing a legend.
    const val gaugeTrack = "#2E4756"
    const val gaugeLow = "#3C7A89"
    const val gaugeMid = "#FEEA00"
    const val gaugeHigh = "#EF4E3A"
    const val gaugeTick = "#6E7385"

    /** Near-black outline for map markers/gauge hub - reads as a dark ring against any fill. */
    const val darkOutline = "#16262E"
}
