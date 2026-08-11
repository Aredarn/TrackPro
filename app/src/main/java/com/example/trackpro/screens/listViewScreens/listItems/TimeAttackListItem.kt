package com.example.trackpro.screens.listViewScreens.listItems

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.pressable
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.managerClasses.utilities.toLapTimeMillis
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date

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

    var sessionData by remember { mutableStateOf<SessionData?>(null) }
    var vehicleData by remember { mutableStateOf<VehicleInformationData?>(null) }
    var lapTimes by remember { mutableStateOf<List<LapTimeData>>(emptyList()) }
    // GPS data per lap — map of lapNumber -> list of GPS points for that lap
    var lapGpsData by remember { mutableStateOf<Map<Int, List<LapInfoData>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }


    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            sessionData = database.sessionDataDao().getSessionById(sessionId)
            sessionData?.let { session ->
                vehicleData = database.vehicleInformationDAO().getVehicle(session.vehicleId).first()
                lapTimes = database.lapTimeDataDAO().getLapsForSession(sessionId)
                // Load GPS points for each lap for speed analysis
                val gpsMap = mutableMapOf<Int, List<LapInfoData>>()
                lapTimes.forEach { lap ->
                    gpsMap[lap.lapnumber] = database.lapInfoDataDAO().getLapData(lap.id)
                }
                lapGpsData = gpsMap
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
                        modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading session", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
                }
            }
        } else if (sessionData == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Session not found", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
            }
        } else {
            val session = sessionData!!
            val vehicle = vehicleData

            // Derived analytics
            val lapMillis = lapTimes.map { it.laptime.toLapTimeMillis() }
            val bestLap = lapTimes.minByOrNull { it.laptime.toLapTimeMillis() }
            val worstLap = lapTimes.maxByOrNull { it.laptime.toLapTimeMillis() }
            val bestMs = lapMillis.minOrNull() ?: 0L
            val avgMs = if (lapMillis.isNotEmpty()) lapMillis.average().toLong() else 0L
            val worstMs = lapMillis.maxOrNull() ?: 0L
            val sessionDuration = session.endTime?.let { it - session.startTime } ?: 0L
            // Consistency: std deviation of lap times as % of best lap (lower = more consistent)
            val consistency = if (lapMillis.size > 1) {
                val mean = lapMillis.average()
                val stdDev = Math.sqrt(lapMillis.map { (it - mean) * (it - mean) }.average())
                val pct = (stdDev / mean * 100)
                String.format("%.1f%%", pct)
            } else "—"
            // Top speed per lap from GPS
            val topSpeedOverall = lapGpsData.values.flatten()
                .mapNotNull { it.spd }.maxOrNull() ?: 0f
            val topSpeedPerLap = lapTimes.associate { lap ->
                lap.lapnumber to (lapGpsData[lap.lapnumber]?.mapNotNull { it.spd }?.maxOrNull() ?: 0f)
            }
            // Improvement trend: compare first half avg vs second half avg
            val trend = if (lapMillis.size >= 4) {
                val half = lapMillis.size / 2
                val firstHalfAvg = lapMillis.take(half).average()
                val secondHalfAvg = lapMillis.drop(half).average()
                val diff = secondHalfAvg - firstHalfAvg
                when {
                    diff < -500 -> "IMPROVING ↑"
                    diff > 500  -> "FADING ↓"
                    else        -> "CONSISTENT →"
                }
            } else "—"
            val trendColor = when {
                trend.contains("IMPROVING") -> TrackProTheme.colors.deltaGood
                trend.contains("FADING")    -> TrackProTheme.colors.deltaBad
                else                        -> TrackProTheme.colors.textMuted
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Top bar
                item {
                    AppTopBar(
                        title = "Session Detail",
                        accent = TrackProTheme.colors.accent,
                        trailing = {
                            Text("${lapTimes.size} laps", style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                        }
                    )
                }

                // ── Session + vehicle header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProTheme.colors.bgCard)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = session.eventType,
                            style = TrackProType.titleLarge,
                            color = TrackProTheme.colors.textPrimary
                        )
                        if (vehicle != null) {
                            Text(
                                text = "${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                                style = TrackProType.body,
                                color = TrackProTheme.colors.textMuted
                            )
                            Text(
                                text = "${vehicle.engineType} · ${vehicle.horsepower}hp · ${vehicle.drivetrain}",
                                style = TrackProType.body.atSize(11.sp),
                                color = TrackProTheme.colors.textMuted.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = DateFormatterUtil.getDateTimeFormat().format(Date(session.startTime)),
                            style = TrackProType.body.atSize(11.sp),
                            color = TrackProTheme.colors.textMuted
                        )
                    }
                    HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                }

                // ── Key performance metrics
                item {
                    SectionLabel("Key Metrics", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProTheme.colors.bgCard)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCell(label = "Best Lap", value = bestLap?.laptime ?: "—", valueColor = TrackProTheme.colors.deltaGood, size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                        StatCell(label = "Average", value = avgMs.toLapTimeString(), size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                        StatCell(
                            label = "Worst",
                            value = worstMs.toLapTimeString(),
                            valueColor = if (lapMillis.size > 1) TrackProTheme.colors.deltaBad else TrackProTheme.colors.textPrimary,
                            size = StatCellSize.Large,
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                    }
                    HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                }

                // ── Session stats row
                item {
                    SectionLabel("Session Stats", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProTheme.colors.bgCard)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        StatRowItem(
                            label = "Total Session Time",
                            value = sessionDuration.toLapTimeString(),
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                        StatRowItem(
                            label = "Top Speed (Session)",
                            value = "${UnitFormatter.formatSpeedPrecise(topSpeedOverall.toDouble(), useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)}",
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                        StatRowItem(
                            label = "Lap Count",
                            value = "${lapTimes.size}",
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                        StatRowItem(
                            label = "Consistency (σ)",
                            value = consistency,
                            textPrimary = if (consistency != "—" &&
                                consistency.replace("%","").toDoubleOrNull()?.let { it < 1.0 } == true)
                                TrackProTheme.colors.deltaGood else TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                        StatRowItem(
                            label = "Gap: Best → Worst",
                            value = if (lapMillis.size > 1)
                                "+${(worstMs - bestMs).toLapTimeString()}" else "—",
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                        StatRowItem(
                            label = "Performance Trend",
                            value = trend,
                            textPrimary = trendColor,
                            textMuted = TrackProTheme.colors.textMuted
                        )
                    }
                    HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                }

                // ── Lap-by-lap breakdown
                item {
                    SectionLabel("Lap Breakdown", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                }

                if (lapTimes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No laps recorded", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
                        }
                    }
                } else {
                    items(lapTimes) { lap ->
                        val isBest = lap.id == bestLap?.id
                        val isWorst = lap.id == worstLap?.id && lapTimes.size > 1
                        val lapMs = lap.laptime.toLapTimeMillis()
                        val deltaMs = lapMs - bestMs
                        val topSpeed = topSpeedPerLap[lap.lapnumber] ?: 0f
                        Box(modifier = Modifier.pressable(onClick = {
                            navController.navigate("lap_detail/$sessionId/${lap.id}")
                        })) {
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
        isBest  -> "Best"
        isWorst -> "Slow"
        else    -> "Lap"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 4.dp)
            .background(
                if (isBest) goodColor.copy(alpha = 0.05f) else bgCard,
                TrackProShapes.card
            )
            .border(
                width = if (isBest) 1.dp else 0.dp,
                color = if (isBest) goodColor.copy(alpha = 0.3f) else Color.Transparent,
                shape = TrackProShapes.card
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: lap number + badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = String.format("%02d", lap.lapnumber),
                    style = TrackProType.statValue.atSize(18.sp),
                    color = accentColor
                )
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), TrackProShapes.badge)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badge.uppercase(), style = TrackProType.label.atSize(8.sp), color = accentColor)
                }
            }

            // Center: top speed
            StatCell(
                label = "Top Speed",
                value = if (topSpeed > 0) UnitFormatter.formatSpeed(topSpeed, useMetric) else "—",
                unit = if (topSpeed > 0) UnitFormatter.speedUnitLabel(useMetric).lowercase() else null,
                size = StatCellSize.Small,
                horizontalAlignment = Alignment.CenterHorizontally
            )

            // Right: lap time + delta
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = lap.laptime,
                    style = TrackProType.statValue.atSize(17.sp),
                    color = accentColor
                )
                if (!isBest && deltaMs > 0) {
                    Text(
                        text = "+${deltaMs.toLapTimeString()}",
                        style = TrackProType.body.atSize(11.sp),
                        color = badColor.copy(alpha = 0.8f)
                    )
                } else if (isBest) {
                    Text(
                        text = "Reference",
                        style = TrackProType.label.atSize(9.sp),
                        color = goodColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(2.dp)
                .height(36.dp)
                .background(accentColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
        )
    }
}

@Composable
private fun StatRowItem(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = TrackProType.label, color = textMuted)
        Text(value, style = TrackProType.body, color = textPrimary)
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
