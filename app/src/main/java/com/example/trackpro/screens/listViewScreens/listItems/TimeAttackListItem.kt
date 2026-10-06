package com.example.trackpro.screens.listViewScreens.listItems

import com.example.trackpro.components.pluralResource
import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.components.specLabel
import com.example.trackpro.components.SectionTitle
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.IconCircle
import com.example.trackpro.components.DashGroup
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.pressable
import com.example.trackpro.theme.markingDim
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.VoidStamp
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellDivider
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.managerClasses.utilities.WeatherService
import com.example.trackpro.managerClasses.utilities.timed
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class TimeAttackListItem : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackProTheme {
                TimeAttackListItemScreen(
                    navController = rememberNavController(),
                    database = Room.inMemoryDatabaseBuilder(
                        LocalContext.current,
                        ESPDatabase::class.java
                    ).build(),
                    sessionId = 1
                )
            }
        }
    }
}


@Composable
fun TimeAttackListItemScreen(
    navController: NavController,
    database: ESPDatabase,
    sessionId: Long
) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()

    val scope = rememberCoroutineScope()
    var sessionData by remember { mutableStateOf<SessionData?>(null) }
    var vehicleData by remember { mutableStateOf<VehicleInformationData?>(null) }
    var lapTimes by remember { mutableStateOf<List<LapTimeData>>(emptyList()) }
    // GPS data per lap — map of lapNumber -> list of GPS points for that lap
    var lapGpsData by remember { mutableStateOf<Map<Int, List<LapInfoData>>>(emptyMap()) }
    var lapSectors by remember { mutableStateOf<Map<Long, List<SectorTimeData>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }


    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            sessionData = database.sessionDataDao().getSessionById(sessionId)
            sessionData?.let { session ->
                vehicleData = database.vehicleInformationDAO().getVehicle(session.vehicleId).first()
                lapTimes = database.lapTimeDataDAO().getLapsForSession(sessionId)
                // Load GPS points for each lap for speed analysis
                val gpsMap = mutableMapOf<Int, List<LapInfoData>>()
                val sectorMap = mutableMapOf<Long, List<SectorTimeData>>()
                lapTimes.forEach { lap ->
                    gpsMap[lap.lapnumber] = database.lapInfoDataDAO().getLapData(lap.id)
                    sectorMap[lap.id] = database.sectorTimeDataDAO().getSectorTimesForLap(lap.id)
                }
                lapGpsData = gpsMap
                lapSectors = sectorMap
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TrackProTheme.colors.accent,
                        modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(R.string.session_loading), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                }
            }
        } else if (sessionData == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.session_not_found), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
            }
        } else {
            val session = sessionData!!
            val vehicle = vehicleData

            // Derived analytics.
            //
            // Everything below is built on timed() rather than the raw rows: a lap that is
            // still in progress (or was left in progress by a session the OS killed) has no
            // time, and counting it as one made it the session's "best lap" at 00:00.00 and
            // pulled the average and consistency figures toward zero.
            val timedLaps = lapTimes.timed()
            val lapMillis = timedLaps.map { it.millis }
            val bestLap = timedLaps.minByOrNull { it.millis }?.lap
            val worstLap = timedLaps.maxByOrNull { it.millis }?.lap
            val bestMs = lapMillis.minOrNull() ?: 0L
            val avgMs = if (lapMillis.isNotEmpty()) lapMillis.average().toLong() else 0L
            val worstMs = lapMillis.maxOrNull() ?: 0L
            val sessionDuration = session.endTime?.let { it - session.startTime } ?: 0L
            // Consistency: standard deviation of the lap times as a percentage of their
            // mean - the coefficient of variation. Lower is more consistent. Measured
            // against the mean rather than the best lap, which is what an earlier version
            // of this comment claimed: dividing by the best lap would make the figure
            // depend on a single outlying lap rather than on the spread.
            val consistency = if (lapMillis.size > 1) {
                val mean = lapMillis.average()
                val stdDev = Math.sqrt(lapMillis.map { (it - mean) * (it - mean) }.average())
                val pct = (stdDev / mean * 100)
                String.format(Locale.US, "%.1f%%", pct)
            } else "—"
            // Top speed per lap from GPS
            val topSpeedOverall = lapGpsData.values.flatten()
                .mapNotNull { it.spd }.maxOrNull() ?: 0f
            val topSpeedPerLap = timedLaps.associate { (lap, _) ->
                lap.lapnumber to (lapGpsData[lap.lapnumber]?.mapNotNull { it.spd }?.maxOrNull() ?: 0f)
            }
            // Predicted (theoretical) best: the quickest time set in each sector, combined
            // into one lap that was never actually driven.
            //
            // Sector splits are only recorded when a sector *gate* is crossed - the finish
            // line resets the timer without emitting one (see CircuitTimingManager). So the
            // final segment, from the last gate to the line, is never persisted. Summing
            // only the stored sectors would therefore produce a "best lap" shorter than any
            // real one. That last sector is recovered here as
            // (lap time - sum of that lap's recorded sectors).
            //
            // Laps that did not record the full set of gates are skipped rather than
            // partially counted, otherwise a lap abandoned early would contribute a
            // deceptively quick opening sector and nothing else.
            val gateCount = lapSectors.values.maxOfOrNull { it.size } ?: 0
            val completeLapSectors: List<List<Long>> =
                if (gateCount == 0) emptyList()
                else timedLaps.mapNotNull { (lap, lapMs) ->
                    val splits = lapSectors[lap.id].orEmpty().sortedBy { it.sectorIndex }
                    if (splits.size != gateCount) return@mapNotNull null
                    val recorded = splits.sumOf { it.splitTimeMs }
                    val finalSector = lapMs - recorded
                    if (finalSector <= 0L) null
                    else splits.map { it.splitTimeMs } + finalSector
                }
            val predictedBestMs: Long? =
                if (completeLapSectors.isEmpty()) null
                else (0..gateCount).sumOf { idx -> completeLapSectors.minOf { it[idx] } }
            // Only positive when the theoretical lap actually beats the real best, which is
            // the normal case but not guaranteed - a single lap session has no gain to show.
            val timeOnTableMs: Long? =
                predictedBestMs?.let { bestMs - it }?.takeIf { it > 0L }

            // Improvement trend: compare first half avg vs second half avg
            val trend = if (lapMillis.size >= 4) {
                val half = lapMillis.size / 2
                val firstHalfAvg = lapMillis.take(half).average()
                val secondHalfAvg = lapMillis.drop(half).average()
                val diff = secondHalfAvg - firstHalfAvg
                when {
                    diff < -500 -> -1
                    diff > 500  -> 1
                    else        -> 0
                }
            } else null
            val trendColor = when (trend) {
                -1   -> TrackProTheme.colors.deltaGood
                1    -> TrackProTheme.colors.deltaBad
                else -> TrackProTheme.colors.textMuted
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Top bar
                item {
                    AppTopBar(
                        title = stringResource(R.string.session_title),
                        onBack = { navController.popBackStack() },
                        accent = TrackProTheme.colors.accent,
                        trailing = {
                            Text(pluralResource(R.plurals.count_laps, timedLaps.size), style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                        }
                    )
                }

                // ── Session + vehicle header
                item {
                    PaddockCard(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                        if (session.voided) {
                            VoidStamp()
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        Text(
                            text = session.eventType,
                            style = TrackProType.titleLarge,
                            color = TrackProTheme.colors.textPrimary
                        )
                        Text(
                            text = DateFormatterUtil.getDateTimeFormat().format(Date(session.startTime)),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.textMuted
                        )
                        if (vehicle != null) {
                            Spacer(Modifier.height(Spacing.md))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(TrackProShapes.control)
                                    .background(TrackProTheme.colors.bgElevated)
                                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconCircle(Icons.Filled.DirectionsCar, size = 32.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                                        style = TrackProType.titleMedium,
                                        color = TrackProTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "${specLabel(vehicle.engineType)} · ${vehicle.horsepower} hp · ${vehicle.drivetrain}",
                                        style = TrackProType.label,
                                        color = TrackProTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Key performance metrics
                item {
                    DashGroup(stringResource(R.string.session_key_metrics)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(label = stringResource(R.string.session_best_lap), value = bestLap?.laptime ?: "—", valueColor = TrackProTheme.colors.deltaGood, size = StatCellSize.Large)
                            StatCell(label = stringResource(R.string.session_average), value = avgMs.toLapTimeString(), size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                            StatCell(
                                label = stringResource(R.string.session_slowest),
                                value = worstMs.toLapTimeString(),
                                valueColor = if (worstMs > bestMs) TrackProTheme.colors.deltaBad else TrackProTheme.colors.textPrimary,
                                size = StatCellSize.Large,
                                horizontalAlignment = Alignment.End
                            )
                        }
                    }
                }

                // ── Predicted best (theoretical lap)
                if (predictedBestMs != null) {
                    item {
                        DashGroup(stringResource(R.string.session_predicted_best)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatCell(
                                    label = stringResource(R.string.session_theoretical_lap),
                                    value = predictedBestMs.toLapTimeString(),
                                    valueColor = TrackProTheme.colors.accent,
                                    size = StatCellSize.Large
                                )
                                StatCell(
                                    label = stringResource(R.string.session_time_on_table),
                                    value = timeOnTableMs
                                        ?.let { String.format(Locale.US, "-%.2f s", it / 1000.0) } ?: "—",
                                    valueColor = if (timeOnTableMs != null)
                                        TrackProTheme.colors.deltaGood
                                    else TrackProTheme.colors.textMuted,
                                    size = StatCellSize.Large,
                                    horizontalAlignment = Alignment.End
                                )
                            }
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                text = stringResource(R.string.session_predicted_hint, completeLapSectors.size, timedLaps.size),
                                style = TrackProType.label,
                                color = TrackProTheme.colors.markingDim
                            )
                        }
                    }
                }

                // ── Session stats
                item {
                    DashGroup(stringResource(R.string.session_stats)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatRowItem(
                                label = stringResource(R.string.session_total_time),
                                value = sessionDuration.toLapTimeString(),
                                textPrimary = TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                            StatRowItem(
                                label = stringResource(R.string.car_top_speed),
                                value = "${UnitFormatter.formatSpeedPrecise(topSpeedOverall.toDouble(), useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)}",
                                textPrimary = TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                            StatRowItem(
                                label = stringResource(R.string.session_laps),
                                value = "${timedLaps.size}",
                                textPrimary = TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                            StatRowItem(
                                label = stringResource(R.string.session_consistency),
                                value = consistency,
                                textPrimary = if (consistency != "—" &&
                                    consistency.replace("%","").toDoubleOrNull()?.let { it < 1.0 } == true)
                                    TrackProTheme.colors.deltaGood else TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                            StatRowItem(
                                label = stringResource(R.string.session_best_to_slowest),
                                value = if (lapMillis.size > 1)
                                    "+${(worstMs - bestMs).toLapTimeString()}" else "—",
                                textPrimary = TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                            StatRowItem(
                                label = stringResource(R.string.session_trend),
                                value = when (trend) {
                                    -1 -> stringResource(R.string.trend_improving)
                                    1 -> stringResource(R.string.trend_fading)
                                    0 -> stringResource(R.string.trend_consistent)
                                    else -> "—"
                                },
                                textPrimary = trendColor,
                                textMuted = TrackProTheme.colors.textMuted
                            )
                        }
                    }
                }

                // ── Conditions (only for sessions where weather was captured)
                val capturedTempC = session.weatherTempC
                if (capturedTempC != null) {
                    item {
                        val wet = WeatherService.isWet(session.weatherPrecipitationMm, session.weatherCode)
                        DashGroup(stringResource(R.string.session_conditions)) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                StatRowItem(
                                    label = stringResource(R.string.session_weather),
                                    value = stringResource(WeatherService.describeCodeRes(session.weatherCode)),
                                    textPrimary = TrackProTheme.colors.textPrimary,
                                    textMuted = TrackProTheme.colors.textMuted
                                )
                                StatRowItem(
                                    label = stringResource(R.string.session_surface),
                                    value = if (wet) stringResource(R.string.session_wet) else stringResource(R.string.session_dry),
                                    // Wet vs dry is the single biggest caveat on any lap-time
                                    // comparison, so it gets colour rather than blending in.
                                    textPrimary = if (wet) TrackProTheme.colors.accent else TrackProTheme.colors.deltaGood,
                                    textMuted = TrackProTheme.colors.textMuted
                                )
                                StatRowItem(
                                    label = stringResource(R.string.session_air_temp),
                                    value = UnitFormatter.formatTemperature(capturedTempC, useMetric),
                                    textPrimary = TrackProTheme.colors.textPrimary,
                                    textMuted = TrackProTheme.colors.textMuted
                                )
                                session.weatherHumidityPct?.let {
                                    StatRowItem(
                                        label = stringResource(R.string.session_humidity),
                                        value = "$it%",
                                        textPrimary = TrackProTheme.colors.textPrimary,
                                        textMuted = TrackProTheme.colors.textMuted
                                    )
                                }
                                session.weatherWindKph?.let { wind ->
                                    StatRowItem(
                                        label = stringResource(R.string.session_wind),
                                        value = "${UnitFormatter.formatSpeedPrecise(wind, useMetric)} " +
                                                "${UnitFormatter.speedUnitLabel(useMetric)} " +
                                                WeatherService.windCompass(session.weatherWindDirDeg),
                                        textPrimary = TrackProTheme.colors.textPrimary,
                                        textMuted = TrackProTheme.colors.textMuted
                                    )
                                }
                                session.weatherPressureHpa?.let {
                                    StatRowItem(
                                        label = stringResource(R.string.session_pressure),
                                        value = String.format(Locale.US, "%.0f hPa", it),
                                        textPrimary = TrackProTheme.colors.textPrimary,
                                        textMuted = TrackProTheme.colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Lap-by-lap breakdown
                item {
                    SectionTitle(stringResource(R.string.session_laps))
                }

                if (timedLaps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.session_no_timed_laps), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                        }
                    }
                } else {
                    items(timedLaps) { (lap, lapMs) ->
                        val isBest = lap.id == bestLap?.id
                        val isWorst = lap.id == worstLap?.id && timedLaps.size > 1
                        val deltaMs = lapMs - bestMs
                        val topSpeed = topSpeedPerLap[lap.lapnumber] ?: 0f
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.gutter, vertical = Spacing.xs)
                                .clip(TrackProShapes.control)
                                .pressable(onClick = {
                                    navController.navigate("lap_detail/$sessionId/${lap.id}")
                                }, scale = 0.98f)
                        ) {
                            LapRow(
                                lap = lap,
                                isBest = isBest,
                                isWorst = isWorst,
                                deltaMs = deltaMs,
                                topSpeed = topSpeed,
                                useMetric = useMetric,
                                bgCard = TrackProTheme.colors.bgCard,
                                bgElevated = TrackProTheme.colors.bgElevated,
                                goodColor = TrackProTheme.colors.deltaGood,
                                badColor = TrackProTheme.colors.deltaBad,
                                textPrimary = TrackProTheme.colors.textPrimary,
                                textMuted = TrackProTheme.colors.textMuted,
                                sectorLine = TrackProTheme.colors.sectorLine
                            )
                        }
                    }
                }

                // ── Void / restore
                // Voiding is reversible by design: the run is kept, so putting it back has
                // to be as easy as taking it out. A destructive-feeling action that cannot
                // be undone would push people toward deleting instead, which is worse.
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.xl)
                    ) {
                        DashAction(
                            label = if (session.voided) stringResource(R.string.session_restore) else stringResource(R.string.session_void),
                            detail = if (session.voided)
                                stringResource(R.string.session_restore_hint)
                            else stringResource(R.string.session_void_hint),
                            compact = true,
                            accent = if (session.voided) TrackProTheme.colors.accent
                            else TrackProTheme.colors.danger,
                            onClick = {
                                val target = !session.voided
                                scope.launch(Dispatchers.IO) {
                                    database.sessionDataDao().setVoided(session.id, target)
                                    val reloaded = database.sessionDataDao().getSessionById(sessionId)
                                    withContext(Dispatchers.Main) { sessionData = reloaded }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Lap row ────────────────────────────────────────────────

@Composable
private fun LapRow(
    lap: LapTimeData,
    isBest: Boolean,
    isWorst: Boolean,
    deltaMs: Long,
    topSpeed: Float,
    useMetric: Boolean,
    bgCard: Color,
    bgElevated: Color,
    goodColor: Color,
    badColor: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color
) {
    val accentColor = when {
        isBest  -> goodColor
        isWorst -> badColor
        else    -> textMuted
    }
    val badge = when {
        isBest  -> stringResource(R.string.lap_badge_best)
        isWorst -> stringResource(R.string.session_slowest)
        else    -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isBest) goodColor.copy(alpha = 0.08f) else bgCard, TrackProShapes.control)
            .border(
                width = 1.dp,
                color = if (isBest) goodColor.copy(alpha = 0.35f) else sectorLine,
                shape = TrackProShapes.control
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: lap number + what makes it notable
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lap_number, lap.lapnumber),
                style = TrackProType.titleMedium,
                color = textPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (badge != null) {
                    LapTag(badge, accentColor)
                }
                // GPS dropped out during this lap, so its time may be two laps merged into
                // one. Flagged rather than hidden - the driver knows whether it was.
                if (lap.signalGap) {
                    LapTag(stringResource(R.string.lap_gps_gap), TrackProTheme.colors.danger)
                }
                if (badge == null && !lap.signalGap) {
                    Text(
                        text = if (topSpeed > 0) stringResource(R.string.lap_top_speed, UnitFormatter.formatSpeed(topSpeed, useMetric), UnitFormatter.speedUnitLabel(useMetric)) else stringResource(R.string.lap_no_speed),
                        style = TrackProType.label,
                        color = textMuted
                    )
                }
            }
        }

        // Right: lap time + delta
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = lap.laptime,
                style = TrackProType.statValue,
                color = if (isBest) goodColor else textPrimary
            )
            Text(
                text = when {
                    isBest -> stringResource(R.string.lap_reference)
                    deltaMs > 0 -> deltaMs.toLapDeltaString()
                    else -> "—"
                },
                style = TrackProType.label,
                color = when {
                    isBest -> goodColor
                    deltaMs > 0 -> badColor
                    else -> textMuted
                }
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TrackProTheme.colors.markingDim
        )
    }
}

@Composable
private fun LapTag(text: String, color: Color) {
    Text(
        text = text,
        style = TrackProType.label,
        color = color,
        modifier = Modifier
            .padding(top = 2.dp)
            .background(color.copy(alpha = 0.14f), TrackProShapes.badge)
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    )
}

@Composable
private fun StatRowItem(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TrackProType.body, color = textMuted)
        Text(value, style = TrackProType.titleMedium, color = textPrimary)
    }
}

// Preview
@Preview
@Composable
fun TimeAttackListItemPreviewScreen() {
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    TimeAttackListItemScreen(
        navController = rememberNavController(),
        database = fakeDatabase,
        sessionId = 1
    )
}
