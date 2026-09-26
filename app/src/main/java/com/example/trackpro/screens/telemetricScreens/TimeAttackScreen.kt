package com.example.trackpro.screens.telemetricScreens

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.timeAttackManagers.CompletedLap
import com.example.trackpro.managerClasses.timeAttackManagers.DeltaReference
import com.example.trackpro.managerClasses.timeAttackManagers.SectorSplit
import com.example.trackpro.managerClasses.timeAttackManagers.TimingMode
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.pressable
import com.example.trackpro.components.KeepScreenOn
import com.example.trackpro.components.rememberHaptics
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.SessionSummary
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.Readout
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.components.rememberTrend
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellDivider
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.viewModels.TimeAttackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimeAttackScreenView(
    trackId: Long? = null,
    vehicleId: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val vm: TimeAttackViewModel = viewModel(
        factory = TimeAttackViewModelFactory(context)
    )

    // The session is live the whole time this screen is up, and a mounted phone is never
    // touched - left to its normal timeout it slept mid-lap.
    KeepScreenOn()

    // ── Collect all state ──────────────────────────────────
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)

    val currentTime  by vm.currentTime.collectAsState()
    val bestTime     by vm.bestTime.collectAsState()
    val lastTime     by vm.lastTime.collectAsState()
    val delta        by vm.delta.collectAsState()
    val liveDelta    by vm.liveDelta.collectAsState()
    val eventCount   by vm.eventCount.collectAsState()
    val stintStart   by vm.stintStart.collectAsState()
    val fullTrack    by vm.fullTrack.collectAsState()
    val finishLine   by vm.finishLine.collectAsState()
    val startLine    by vm.startLine.collectAsState()
    val driver       by vm.driverPosition.collectAsState()
    val timingMode   by vm.timingMode.collectAsState()
    val lapSplits    by vm.currentLapSplits.collectAsState()
    val completedLaps by vm.completedLaps.collectAsState()
    val activeReference by vm.activeReference.collectAsState()
    val preferredReference by vm.preferredReference.collectAsState()

    // Prefer the continuously-updating delta (tracked by distance into the lap); fall back
    // to the static per-lap delta before a best-lap reference exists (e.g. lap 1).
    val effectiveDelta = liveDelta ?: delta
    val isLiveDelta = liveDelta != null
    val isCircuit = timingMode is TimingMode.Circuit
    val deltaCaption = if (isCircuit) {
        captionForDelta(isLiveDelta, activeReference, preferredReference)
    } else {
        if (isLiveDelta) "Delta to best - live" else "Delta to best"
    }

    val linesToShow by remember(timingMode, startLine, finishLine) {
        derivedStateOf {
            if (timingMode is TimingMode.Sprint) startLine + finishLine else finishLine
        }
    }

    LaunchedEffect(finishLine.size) {
        Log.d("TimeAttackScreen", "Finish line size: ${finishLine.size}")
        //Finish line coords:
        Log.d("TimeAttackScreen", "Finish line coords: $finishLine")
    }

    // Initialisation can fail - a missing track, a database error - and this used to be
    // logged and swallowed, dropping the driver into a live timing HUD with no track
    // loaded and no indication anything was wrong.
    var initError by remember { mutableStateOf<String?>(null) }

    // ── Init track + session FIRST ─────────────────────────
    LaunchedEffect(trackId) {
        if (trackId == null || vehicleId == null) {
            return@LaunchedEffect
        }

        try {
            val track = withContext(Dispatchers.IO) {
                app.database.trackMainDao().getTrack(trackId).firstOrNull()
            }

            val mode = when (track?.type?.lowercase()) {
                "sprint" -> TimingMode.Sprint
                else     -> TimingMode.Circuit
            }

            vm.loadTrack(trackId, mode)
            // Both calls are no-ops if this screen has merely been rebuilt (rotation,
            // returning from background) rather than genuinely started.
            vm.ensureSession(trackId, vehicleId)
        } catch (e: Exception) {
            Log.e("TimeAttackScreen", "Initialization error: ${e.message}", e)
            initError = e.message ?: "Could not load the track or start the session"
        }
    }

    // The single most useful haptic in the app: a lap closing is confirmed by feel, so
    // the driver doesn't have to look away from the track to know it registered.
    // Keyed on the counter itself, so it fires exactly once per lap.
    val haptics = rememberHaptics()
    LaunchedEffect(eventCount) {
        if (eventCount > 0) haptics.perform(Haptic.Confirm)
    }

    val gpsPoints = fullTrack //+ linesToShow
    val driverPos = driver ?: LatLonOffset(0.0, 0.0)

    // With the map off this becomes a pure driver HUD, and the map's place is taken by a
    // pit board of the session's last laps. Saved rather than remembered so it survives
    // rotation - someone who turned the map off does not want it back every time the
    // phone shifts orientation in a windscreen mount.
    var mapVisible by rememberSaveable { mutableStateOf(true) }


    // Leaving a live session used to be an unguarded back tap on a 48dp arrow in the
    // corner your hand reaches for when adjusting a mount - and it saved silently, with no
    // acknowledgement that a whole track day had been recorded. Now the exit is deliberate
    // and it ends on a summary.
    var confirmEnd by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }

    if (showSummary) {
        SessionSummary(
            headline = bestTime,
            headlineCaption = "Best lap",
            rows = listOf(
                (if (timingMode is TimingMode.Circuit) "Laps" else "Runs") to "$eventCount",
                "Last" to lastTime,
                "Sectors logged" to "${lapSplits.size}"
            ),
            onKeep = { vm.keepAndEnd(); onBack() },
            onVoid = { vm.voidAndEnd(); onBack() }
        )
        return
    }

    if (confirmEnd) {
        ConfirmDeleteDialog(
            title = "End session?",
            message = "Timing stops and the session is written to the archive. You can " +
                "void it on the next screen if it should not count.",
            confirmLabel = "End session",
            dismissLabel = "Keep driving",
            onConfirm = { confirmEnd = false; showSummary = true },
            onDismiss = { confirmEnd = false }
        )
    }

    if (initError != null) {
        // A blocking face, not a toast: every number on the HUD behind it would be
        // meaningless, so the screen refuses to pretend it is timing anything.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProTheme.colors.panel)
        ) {
            AppTopBar(title = "Session failed", onBack = onBack, accent = TrackProTheme.colors.danger)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        "COULD NOT START TIMING",
                        style = TrackProType.titleMedium,
                        color = TrackProTheme.colors.danger
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        initError ?: "",
                        style = TrackProType.body,
                        color = TrackProTheme.colors.markingDim,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    DashAction(
                        label = "Back to panel",
                        onClick = onBack,
                        compact = true,
                        modifier = Modifier.width(200.dp)
                    )
                }
            }
        }
        return
    }

    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> TimeAttackLandscapeLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = effectiveDelta,
            isLiveDelta = isLiveDelta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected,
            linesToShow = linesToShow,
            lapSplits   = lapSplits,
            deltaCaption = deltaCaption,
            // Sprint has no live delta to point anywhere, so no switch.
            showReferenceSwitch = isCircuit,
            preferredReference = preferredReference,
            onReferenceChange = vm::setDeltaReference,
            completedLaps = completedLaps,
            mapVisible  = mapVisible,
            onToggleMap = { mapVisible = it },
            // Guarded: ending a live session is the app's most destructive-by-omission
            // action, and this arrow sits where a hand lands adjusting a mount.
            onBack      = { confirmEnd = true }
        )
        else -> TimeAttackPortraitLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = effectiveDelta,
            isLiveDelta = isLiveDelta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected,
            linesToShow = linesToShow,
            lapSplits   = lapSplits,
            deltaCaption = deltaCaption,
            // Sprint has no live delta to point anywhere, so no switch.
            showReferenceSwitch = isCircuit,
            preferredReference = preferredReference,
            onReferenceChange = vm::setDeltaReference,
            completedLaps = completedLaps,
            mapVisible  = mapVisible,
            onToggleMap = { mapVisible = it },
            // Guarded: ending a live session is the app's most destructive-by-omission
            // action, and this arrow sits where a hand lands adjusting a mount.
            onBack      = { confirmEnd = true }
        )
    }

}
// ── Portrait ───────────────────────────────────────────────

@Composable
fun TimeAttackPortraitLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    isLiveDelta: Boolean = false,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean,
    linesToShow : List<TrackCoordinatesData>,
    lapSplits: List<SectorSplit> = emptyList(),
    deltaCaption: String = "Delta to best",
    showReferenceSwitch: Boolean = false,
    preferredReference: DeltaReference = DeltaReference.SESSION_BEST,
    onReferenceChange: (DeltaReference) -> Unit = {},
    completedLaps: List<CompletedLap> = emptyList(),
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val deltaColor = if (delta <= 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
    val modeColor  = TrackProTheme.colors.accent
    // Gaining pushes the bar forward, so a lower-is-better delta is negated before
    // it reaches the bar. Two seconds fills it; past that the exact figure has
    // stopped being actionable and pinning is the honest response.
    val deltaFraction = (-delta / 2.0).coerceIn(-1.0, 1.0).toFloat()
    // Rising means gaining: the mark tracks -delta, so it points up when the gap to the
    // reference is closing. 50ms of hysteresis - below that it is GPS noise, not driving.
    val deltaTrend = rememberTrend(-delta.toFloat(), threshold = 0.05f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {

        AppTopBar(
            title = modeLabel,
            onBack = onBack,
            accent = modeColor,
            trailing = { HudTrailing(isConnected, mapVisible, onToggleMap) }
        )

        // -- Delta: the largest thing on the screen --------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.field)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Readout(
                value = String.format("%+.3f", delta),
                caption = deltaCaption,
                valueColor = deltaColor,
                trend = deltaTrend,
                valueSize = if (mapVisible) 60.sp else 84.sp,
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$eventCount",
                            style = TrackProType.displayNumeric.atSize(28.sp),
                            color = TrackProTheme.colors.marking
                        )
                        Text(
                            text = eventName,
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            SegmentBar(
                signedFraction = deltaFraction,
                activeColor = deltaColor,
                segments = 25,
                height = if (mapVisible) 18.dp else 26.dp
            )
            if (showReferenceSwitch) {
                Spacer(Modifier.height(8.dp))
                DeltaReferenceSwitch(preferredReference, onReferenceChange)
            }
        }

        Bezel()

        // -- Clocks ---------------------------------------
        Row(modifier = Modifier.fillMaxWidth()) {
            Instrument(
                label = "Current $eventName",
                value = currentTime,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            Bezel(vertical = true, modifier = Modifier.height(62.dp))
            Instrument(
                label = "Best",
                value = bestTime,
                valueColor = TrackProTheme.colors.accent,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            Bezel(vertical = true, modifier = Modifier.height(62.dp))
            Instrument(
                label = "Last",
                value = lastTime,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Bezel()

        if (lapSplits.isNotEmpty()) {
            SectorSplitsRow(splits = lapSplits)
            Bezel()
        }

        if (mapVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrackProTheme.colors.panel)
            ) {
                if (gpsPoints.isNotEmpty()) {
                    MapLibreTrackView(
                        gpsPoints = gpsPoints,
                        driverPosition = driver,
                        modifier = Modifier.fillMaxSize(),
                        linesToShow
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "AWAITING GPS",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }
            }
        } else {
            RecentLapsPanel(
                laps = completedLaps,
                eventName = eventName,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Bezel()
            Row(modifier = Modifier.fillMaxWidth()) {
                StintTimerCell(stintStart = stintStart, modifier = Modifier.weight(1f))
            }
            Bezel()
        }
    }
}



// ── Landscape ──────────────────────────────────────────────

@Composable
fun TimeAttackLandscapeLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    isLiveDelta: Boolean = false,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean,
    linesToShow: List<TrackCoordinatesData>,
    lapSplits: List<SectorSplit> = emptyList(),
    deltaCaption: String = "Delta to best",
    showReferenceSwitch: Boolean = false,
    preferredReference: DeltaReference = DeltaReference.SESSION_BEST,
    onReferenceChange: (DeltaReference) -> Unit = {},
    completedLaps: List<CompletedLap> = emptyList(),
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val deltaColor = if (delta <= 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
    val modeColor  = TrackProTheme.colors.accent
    // Gaining pushes the bar forward, so a lower-is-better delta is negated before
    // it reaches the bar. Two seconds fills it; past that the exact figure has
    // stopped being actionable and pinning is the honest response.
    val deltaFraction = (-delta / 2.0).coerceIn(-1.0, 1.0).toFloat()
    // Rising means gaining: the mark tracks -delta, so it points up when the gap to the
    // reference is closing. 50ms of hysteresis - below that it is GPS noise, not driving.
    val deltaTrend = rememberTrend(-delta.toFloat(), threshold = 0.05f)

    // The right pane is the map or, with the map off, the lap board. Either way the
    // instruments keep the same column, so toggling the map never reflows the numbers a
    // driver has learned the position of.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxSize()
        ) {
            AppTopBar(
                title = modeLabel,
                onBack = onBack,
                accent = modeColor,
                trailing = { HudTrailing(isConnected, mapVisible, onToggleMap) }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.field)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Readout(
                    value = String.format("%+.3f", delta),
                    caption = deltaCaption,
                    valueColor = deltaColor,
                    trend = deltaTrend,
                    valueSize = 56.sp,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$eventCount",
                                style = TrackProType.displayNumeric.atSize(26.sp),
                                color = TrackProTheme.colors.marking
                            )
                            Text(
                                text = eventName,
                                style = TrackProType.label,
                                color = TrackProTheme.colors.markingDim
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                SegmentBar(
                    signedFraction = deltaFraction,
                    activeColor = deltaColor,
                    segments = 25,
                    height = 16.dp
                )
                if (showReferenceSwitch) {
                    Spacer(Modifier.height(6.dp))
                    DeltaReferenceSwitch(preferredReference, onReferenceChange)
                }
            }

            Bezel()

            Row(modifier = Modifier.fillMaxWidth()) {
                Instrument(
                    label = "Current $eventName",
                    value = currentTime,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                Bezel(vertical = true, modifier = Modifier.height(58.dp))
                Instrument(
                    label = "Best",
                    value = bestTime,
                    valueColor = TrackProTheme.colors.accent,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Bezel()
            Row(modifier = Modifier.fillMaxWidth()) {
                Instrument(
                    label = "Last",
                    value = lastTime,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                Bezel(vertical = true, modifier = Modifier.height(58.dp))
                StintTimerCell(stintStart = stintStart, modifier = Modifier.weight(1f))
            }
            Bezel()

            if (lapSplits.isNotEmpty()) {
                SectorSplitsRow(splits = lapSplits)
            }
        }

        Bezel(vertical = true)

        Box(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxSize()
                .background(TrackProTheme.colors.panel)
        ) {
            when {
                !mapVisible -> RecentLapsPanel(
                    laps = completedLaps,
                    eventName = eventName,
                    modifier = Modifier.fillMaxSize()
                )
                gpsPoints.isNotEmpty() -> MapLibreTrackView(
                    gpsPoints = gpsPoints,
                    driverPosition = driver,
                    modifier = Modifier.fillMaxSize(),
                    linesToShow
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "AWAITING GPS",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                }
            }
        }
    }
}



// ── Shared sub-components ──────────────────────────────────

/**
 * Top-bar trailing block: signal state plus the map switch.
 *
 * The map is both the most expensive thing on this screen - a MapLibre surface redrawing
 * on every GPS tick - and the thing a driver needs least mid-session. Switching it off
 * leaves only what gets read at speed, with the lap board in the map's place.
 */
@Composable
private fun HudTrailing(
    isConnected: Boolean,
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit
) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isConnected) "LIVE" else "NO SIGNAL",
            style = TrackProType.label,
            color = if (isConnected) TrackProTheme.colors.deltaGood else TrackProTheme.colors.textFaint
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = "MAP",
            style = TrackProType.label,
            color = if (mapVisible) TrackProTheme.colors.textPrimary else TrackProTheme.colors.textFaint
        )
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = mapVisible,
            onCheckedChange = {
                // A discrete commit that changes the whole screen - earns a haptic.
                haptics.perform(Haptic.Selection)
                onToggleMap(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TrackProTheme.colors.onAccent,
                checkedTrackColor = TrackProTheme.colors.accent,
                checkedBorderColor = TrackProTheme.colors.accent,
                uncheckedThumbColor = TrackProTheme.colors.textMuted,
                uncheckedTrackColor = TrackProTheme.colors.bgElevated,
                uncheckedBorderColor = TrackProTheme.colors.sectorLine
            )
        )
    }
}

/**
 * The placard under the delta: what the number is measured against.
 *
 * Says what is *actually* in use, which is not always what was chosen - a track best only
 * exists once one has been set in the direction this session is running, and until then the
 * session best stands in. A driver reading "-0.4" needs to know which of the two it is
 * against, or the number means nothing.
 */
private fun captionForDelta(
    isLive: Boolean,
    active: DeltaReference?,
    preferred: DeltaReference
): String = when {
    active == DeltaReference.TRACK_BEST ->
        if (isLive) "Delta to track best - live" else "Delta to track best"
    active == DeltaReference.SESSION_BEST && preferred == DeltaReference.TRACK_BEST ->
        "Delta to session best - no track best yet"
    active == DeltaReference.SESSION_BEST ->
        if (isLive) "Delta to session best - live" else "Delta to session best"
    // Nothing to measure against yet: before the first crossing, or lap 1 of a session
    // measured against the session best.
    preferred == DeltaReference.TRACK_BEST -> "Delta to track best"
    else -> "Delta to session best"
}

/**
 * Flips what the live delta is measured against. On the HUD rather than only in Settings
 * because reaching Settings from here means leaving the screen, which ends the session.
 *
 * A labelled control rather than a tap on the delta itself: the readout is the thing most
 * likely to be brushed while adjusting a windscreen mount, and a reference that changes
 * unnoticed would make every number after it misleading.
 */
@Composable
private fun DeltaReferenceSwitch(
    preferred: DeltaReference,
    onChange: (DeltaReference) -> Unit
) {
    val other = when (preferred) {
        DeltaReference.SESSION_BEST -> DeltaReference.TRACK_BEST
        DeltaReference.TRACK_BEST -> DeltaReference.SESSION_BEST
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .pressable(onClick = { onChange(other) }, haptic = Haptic.Selection),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "COMPARE TO",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            listOf(
                DeltaReference.SESSION_BEST to "SESSION BEST",
                DeltaReference.TRACK_BEST to "TRACK BEST"
            ).forEach { (reference, label) ->
                Text(
                    text = label,
                    style = TrackProType.label,
                    color = if (reference == preferred) TrackProTheme.colors.accent
                    else TrackProTheme.colors.textFaint
                )
            }
        }
    }
}

@Composable
private fun StintTimerCell(stintStart: Long, modifier: Modifier = Modifier) {
    var stintTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(stintStart) {
        while (true) {
            delay(500)
            val elapsed = SystemClock.elapsedRealtime() - stintStart
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            stintTime = String.format("%02d:%02d:%02d", h, m, s)
        }
    }
    Instrument(label = "Stint", value = stintTime, valueSize = 22.sp, modifier = modifier)
}

@Composable
private fun SectorSplitsRow(splits: List<SectorSplit>) {
    // The labanotation discipline the direction inherited: a sector's block is as wide as
    // the time it took, so a slow sector is visibly longer before any digit is read. The
    // numbers stay underneath for the deliberate read; the widths are the glance.
    val total = splits.sumOf { it.splitMs }.coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        splits.forEach { split ->
            val gained = (split.deltaMs ?: 0L) <= 0L
            val color = when {
                split.deltaMs == null -> TrackProTheme.colors.markingDim
                gained -> TrackProTheme.colors.deltaGood
                else -> TrackProTheme.colors.deltaBad
            }
            Column(
                modifier = Modifier.weight(
                    (split.splitMs.toFloat() / total.toFloat()).coerceAtLeast(0.08f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(color)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "S${split.sectorIndex + 1}",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
                Text(
                    text = String.format("%.2f", split.splitMs / 1000.0),
                    style = TrackProType.statValue.atSize(14.sp),
                    color = color
                )
            }
        }
    }
}

/**
 * The pit board: the session's last laps, newest on top, in the map's place.
 *
 * Fitted rather than scrolled. A driver never scrolls, so the board shows exactly as many
 * whole rows as the space holds and the rows that no longer fit are the ones that have
 * stopped mattering. Whole rows only - a half-clipped lap time reads as a wrong lap time.
 */
@Composable
private fun RecentLapsPanel(
    laps: List<CompletedLap>,
    eventName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(TrackProTheme.colors.panel)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LAST ${eventName}S",
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim
            )
            Text(
                text = "GAP TO BEST",
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim
            )
        }
        Bezel()

        if (laps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO ${eventName}S YET",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
        } else {
            FittedLapRows(
                laps = laps,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * Lays out rows newest-first until the next one would not fit, then stops.
 *
 * Subcomposed so that only the rows actually shown are ever built: a long session has
 * dozens of laps and the board holds a handful. Slots are keyed by lap number, so a row
 * keeps its identity as new laps push it down the board.
 */
@Composable
private fun FittedLapRows(laps: List<CompletedLap>, modifier: Modifier = Modifier) {
    // The manager's own best is the *first* lap to set a time, so ties resolve the same
    // way here and the row tagged BEST is the one the Best clock is showing.
    val best = laps.minByOrNull { it.timeMs } ?: return

    // This screen recomposes on every GPS tick. Remembering the policy against the lap
    // list means a tick that closed no lap hands SubcomposeLayout the same policy instance
    // and it does not re-measure - the board only does work when a lap actually lands.
    val measurePolicy = remember(laps) {
        val newestFirst = laps.asReversed()
        val policy: SubcomposeMeasureScope.(Constraints) -> MeasureResult = { constraints ->
            val width = constraints.maxWidth
            val limit = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
            val rowConstraints = Constraints(minWidth = width, maxWidth = width)

            val placed = ArrayList<Placeable>()
            var used = 0
            for (lap in newestFirst) {
                val row = subcompose(lap.number) {
                    LapRow(
                        lap = lap,
                        isBest = lap.number == best.number,
                        gapToBestMs = lap.timeMs - best.timeMs
                    )
                }.first().measure(rowConstraints)
                if (used + row.height > limit) break
                placed += row
                used += row.height
            }

            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else used
            layout(width, height) {
                var y = 0
                placed.forEach { row ->
                    row.placeRelative(0, y)
                    y += row.height
                }
            }
        }
        policy
    }

    SubcomposeLayout(modifier = modifier, measurePolicy = measurePolicy)
}

private const val SLOT_LEAD = "lead"
private const val SLOT_GAP = "gap"
private const val SLOT_SPLIT = "split"

/**
 * One line of the board: number, time, the sector splits if there is room for all of them,
 * and the gap to the session best.
 *
 * Splits are all-or-nothing. A row that fits S1 and S2 but drops S3 reads as a lap with
 * two sectors, which is a lie the driver has no way to detect at a glance; a row with no
 * splits reads as exactly what it is. So they are measured first and placed only if every
 * one fits between the time and the gap - which is most tracks in portrait, and any track
 * in landscape.
 */
@Composable
private fun LapRow(lap: CompletedLap, isBest: Boolean, gapToBestMs: Long) {
    Column {
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.field)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            content = {
                Row(
                    modifier = Modifier.layoutId(SLOT_LEAD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lap.number}",
                        style = TrackProType.statValue.atSize(14.sp),
                        color = TrackProTheme.colors.markingDim,
                        modifier = Modifier.width(34.dp)
                    )
                    Text(
                        text = lap.timeMs.toLapTimeString(),
                        style = TrackProType.statValue.atSize(18.sp),
                        color = if (isBest) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                    )
                    // GPS dropped out during this lap. In the fault colour because it may be
                    // two laps merged into one - see CompletedLap.signalGap.
                    if (lap.signalGap) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "GPS GAP",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.danger
                        )
                    }
                }
                if (isBest) {
                    Text(
                        text = "BEST",
                        style = TrackProType.label.atSize(11.sp),
                        color = TrackProTheme.colors.accent,
                        modifier = Modifier.layoutId(SLOT_GAP)
                    )
                } else {
                    Text(
                        text = String.format("+%.2f", gapToBestMs / 1000.0),
                        style = TrackProType.statValue.atSize(16.sp),
                        color = TrackProTheme.colors.markingDim,
                        modifier = Modifier.layoutId(SLOT_GAP)
                    )
                }
                lap.splits.forEach { split ->
                    SplitMark(split = split, modifier = Modifier.layoutId(SLOT_SPLIT))
                }
            }
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val loose = Constraints()
            val edge = 14.dp.roundToPx()
            val between = 10.dp.roundToPx()

            val gap = measurables.first { it.layoutId == SLOT_GAP }.measure(loose)
            val lead = measurables.first { it.layoutId == SLOT_LEAD }
                .measure(Constraints(maxWidth = (width - gap.width - edge).coerceAtLeast(0)))
            val splits = measurables.filter { it.layoutId == SLOT_SPLIT }.map { it.measure(loose) }

            val splitsWidth = splits.sumOf { it.width } + between * (splits.size - 1).coerceAtLeast(0)
            val showSplits = splits.isNotEmpty() &&
                    lead.width + edge + splitsWidth + edge + gap.width <= width

            val height = maxOf(
                lead.height,
                gap.height,
                if (showSplits) splits.maxOf { it.height } else 0
            )
            layout(width, height) {
                lead.placeRelative(0, (height - lead.height) / 2)
                gap.placeRelative(width - gap.width, (height - gap.height) / 2)
                if (showSplits) {
                    var x = lead.width + edge
                    splits.forEach { split ->
                        split.placeRelative(x, (height - split.height) / 2)
                        x += split.width + between
                    }
                }
            }
        }
        Bezel()
    }
}

/** A sector split on the board, coloured the way it was on the live strip when it closed. */
@Composable
private fun SplitMark(split: SectorSplit, modifier: Modifier = Modifier) {
    val color = when {
        split.deltaMs == null -> TrackProTheme.colors.markingDim
        split.deltaMs <= 0L -> TrackProTheme.colors.deltaGood
        else -> TrackProTheme.colors.deltaBad
    }
    Text(
        text = String.format("%.2f", split.splitMs / 1000.0),
        style = TrackProType.statValue.atSize(12.sp),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

// ── Reusable sub-components ────────────────────────────────
@Composable
fun MapLibreTrackView(
    gpsPoints: List<TrackCoordinatesData>,
    driverPosition: LatLonOffset,
    modifier: Modifier = Modifier,
    linesToShow: List<TrackCoordinatesData>
) {
    val driverSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val mapReady = remember { mutableStateOf<MapLibreMap?>(null) }

    // 1. Only update the Driver Source (No Camera Movement)
    LaunchedEffect(driverPosition) {
        Log.d("MapLibreTrackView", "Driver position changed: ${driverPosition.lat}, ${driverPosition.lon}")
        val src = driverSource.value ?: run {
            Log.w("MapLibreTrackView", "Driver source is null!")
            return@LaunchedEffect
        }
        if (driverPosition.lat == 0.0 && driverPosition.lon == 0.0) {
            Log.w("MapLibreTrackView", "Driver position is 0,0, skipping")
            return@LaunchedEffect
        }

        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${driverPosition.lon},${driverPosition.lat}]},"properties":{}}"""
        Log.d("MapLibreTrackView", "Updating driver GeoJSON: $geojson")
        src.setGeoJson(geojson)
    }

    // 2. Separate Effect to fit the camera to the track whenever the track data changes
    LaunchedEffect(gpsPoints) {
        val map = mapReady.value ?: return@LaunchedEffect
        if (gpsPoints.isNotEmpty()) {
            fitCameraToTrack(map, gpsPoints)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapReady.value = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        // Disable gestures so the user doesn't accidentally move away from the track
                        map.uiSettings.setAllGesturesEnabled(false)

                        if (gpsPoints.isNotEmpty()) {
                            drawTrackOnStyle(style, gpsPoints,linesToShow)
                            fitCameraToTrack(map, gpsPoints) // Initial fit
                        }

                        // Driver position marker
                        val src = GeoJsonSource(
                            "driver-src",
                            """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}"""
                        )
                        style.addSource(src)
                        style.addLayer(
                            CircleLayer("driver-layer", "driver-src").apply {
                                setProperties(
                                    PropertyFactory.circleColor(DataVizColors.boundaryLine),
                                    PropertyFactory.circleRadius(6f),
                                    PropertyFactory.circleStrokeColor(DataVizColors.trackLine),
                                    PropertyFactory.circleStrokeWidth(2f)
                                )
                            }
                        )
                        driverSource.value = src
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun fitCameraToTrack(map: MapLibreMap, points: List<TrackCoordinatesData>) {
    if (points.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    points.forEach {
        boundsBuilder.include(LatLng(it.latitude, it.longitude))
    }

    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(
            boundsBuilder.build(),
            100 // Padding in pixels from the edges of the view
        ), 1000 // Animation duration
    )
}

private fun drawTrackOnStyle(
    style: Style,
    trackPath: List<TrackCoordinatesData>,
    timingLines: List<TrackCoordinatesData>
) {
    if (trackPath.size < 2) return

    // ── 1. Determine Drawing Mode ──
    // Sprint logic usually has 4 points (2 for start, 2 for finish).
    // Circuit logic usually has 2 points (just the finish line).
    val isSprint = timingLines.size > 2

    val displayPath = if (isSprint) {
        val start = timingLines.first()
        val finish = timingLines.last()
        extractSprintSegment(trackPath, start, finish)
    } else {
        // It's a Circuit: Draw the full path from the DB
        trackPath
    }

    if (displayPath.size < 2) return

    // ── 2. Draw the Main Track Line ──
    val trackCoords = displayPath.joinToString(",") {
        "[${it.longitude},${it.latitude}]"
    }

    val trackGeoJson = """
        {
            "type":"Feature",
            "geometry":{ "type":"LineString", "coordinates":[ $trackCoords ] },
            "properties":{}
        }
    """.trimIndent()

    // Add Source (Remove old one if it exists to avoid crashes on update)
    style.getSource("track-src")?.let { style.removeSource(it) }
    style.getLayer("track-layer")?.let { style.removeLayer(it) }

    style.addSource(GeoJsonSource("track-src", trackGeoJson))
    style.addLayer(
        LineLayer("track-layer", "track-src").apply {
            setProperties(
                PropertyFactory.lineColor(DataVizColors.trackLine),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
    )

    // ── 3. Draw Track Boundaries ──
    // Use the displayPath to calculate boundaries so they match the track
    drawBoundaries(style, displayPath)

    // ── 4. Draw Start/Finish Markers ──
    drawMarkers(style, timingLines)
}

private fun drawBoundaries(style: Style, path: List<TrackCoordinatesData>) {
    val trackWidth = 4.0 // meters
    val sides = listOf("left" to -trackWidth/2, "right" to trackWidth/2)

    sides.forEach { (side, offset) ->
        val boundaryCoords = calculateParallelLine(path, offset)
        val id = "$side-boundary"

        val geojson = """
            {
                "type":"Feature",
                "geometry":{
                    "type":"LineString",
                    "coordinates": ${boundaryCoords.map { "[${it.first}, ${it.second}]" }}
                }
            }
        """.trimIndent()

        style.getSource(id)?.let { style.removeSource(it) }
        style.getLayer("$id-layer")?.let { style.removeLayer(it) }

        style.addSource(GeoJsonSource(id, geojson))
        style.addLayer(LineLayer("$id-layer", id).apply {
            setProperties(
                PropertyFactory.lineColor(DataVizColors.boundaryLine),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        })
    }
}

private fun drawMarkers(style: Style, timingLines: List<TrackCoordinatesData>) {
    val markers = if (timingLines.size >= 4) {
        listOf(timingLines[0], timingLines.last()) // Sprint: Show both
    } else if (timingLines.isNotEmpty()) {
        listOf(timingLines.first()) // Circuit: Show just the finish line
    } else emptyList()

    markers.forEachIndexed { i, pt ->
        val id = "marker-$i"
        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.longitude},${pt.latitude}]}}"""

        style.getSource(id)?.let { style.removeSource(it) }
        style.addSource(GeoJsonSource(id, geojson))
        style.addLayer(CircleLayer("$id-layer", id).apply {
            setProperties(
                PropertyFactory.circleColor(DataVizColors.boundaryLine),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(DataVizColors.trackLine)
            )
        })
    }
}

private fun findClosestIndex(
    track: List<TrackCoordinatesData>,
    target: TrackCoordinatesData
): Int {
    return track.indices.minByOrNull { i ->
        val dLat = track[i].latitude - target.latitude
        val dLon = track[i].longitude - target.longitude
        dLat * dLat + dLon * dLon
    } ?: 0
}

private fun extractSprintSegment(
    track: List<TrackCoordinatesData>,
    start: TrackCoordinatesData,
    finish: TrackCoordinatesData
): List<TrackCoordinatesData> {

    if (track.isEmpty()) return emptyList()

    val startIndex = findClosestIndex(track, start)
    val finishIndex = findClosestIndex(track, finish)

    return if (startIndex <= finishIndex) {
        track.subList(startIndex, finishIndex + 1)
    } else {
        track.subList(finishIndex, startIndex + 1)
    }
}

// Helper function to calculate parallel lines for track boundaries
private fun calculateParallelLine(
    points: List<TrackCoordinatesData>,
    offsetMeters: Double
): List<Pair<Double, Double>> {
    if (points.size < 2) return emptyList()

    return points.mapIndexed { index, point ->
        val bearing = when {
            index == 0 -> {
                // First point: use bearing to next point
                calculateBearing(point, points[index + 1])
            }
            index == points.lastIndex -> {
                // Last point: use bearing from previous point
                calculateBearing(points[index - 1], point)
            }
            else -> {
                // Middle points: average bearing
                val bearingFrom = calculateBearing(points[index - 1], point)
                val bearingTo = calculateBearing(point, points[index + 1])
                (bearingFrom + bearingTo) / 2.0
            }
        }

        // Calculate perpendicular offset (90 degrees to the right)
        val perpBearing = bearing + 90.0
        offsetPoint(point.latitude, point.longitude, offsetMeters, perpBearing)
    }
}

private fun calculateBearing(from: TrackCoordinatesData, to: TrackCoordinatesData): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))

    return (bearing + 360) % 360
}

private fun offsetPoint(lat: Double, lon: Double, distanceMeters: Double, bearing: Double): Pair<Double, Double> {
    val earthRadius = 6371000.0 // meters
    val angularDistance = distanceMeters / earthRadius
    val bearingRad = Math.toRadians(bearing)
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)

    val newLatRad = asin(
        sin(latRad) * cos(angularDistance) +
                cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )

    val newLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(newLatRad)
    )

    return Pair(Math.toDegrees(newLonRad), Math.toDegrees(newLatRad))
}

class TimeAttackViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeAttackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeAttackViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}