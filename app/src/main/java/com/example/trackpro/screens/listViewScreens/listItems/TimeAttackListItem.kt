package com.example.trackpro.screens.listViewScreens.listItems

import TrackProTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.theme.TrackProColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeAttackListItem : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackProTheme { // Wrap in your theme
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
            .background(TrackProColors.BgDeep)
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TrackProColors.AccentGreen,
                        modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("LOADING SESSION", color = TrackProColors.TextMuted,
                        fontSize = 10.sp, letterSpacing = 3.sp)
                }
            }
        } else if (sessionData == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("SESSION NOT FOUND", color = TrackProColors.TextMuted,
                    fontSize = 12.sp, letterSpacing = 3.sp)
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
                trend.contains("IMPROVING") -> TrackProColors.AccentGreen
                trend.contains("FADING")    ->TrackProColors.AccentRed
                else                        -> TrackProColors.AccentAmber
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Top bar
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProColors.AccentGreen)
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("● SESSION DETAIL", color = Color.Black, fontSize = 11.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text("${lapTimes.size} LAPS", color = Color.Black,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                    }
                }

                // ── Session + vehicle header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProColors.BgCard)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = session.eventType,
                            color = TrackProColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        if (vehicle != null) {
                            Text(
                                text = "${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                                color = TrackProColors.TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${vehicle.engineType} · ${vehicle.horsepower}hp · ${vehicle.drivetrain}",
                                color = TrackProColors.TextMuted.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        Text(
                            text = formatter.format(Date(session.startTime)),
                            color = TrackProColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                }

                // ── Key performance metrics
                item {
                    SectionHeader("KEY METRICS", TrackProColors.TextMuted, TrackProColors.SectorLine)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProColors.BgCard)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn("BEST LAP", bestLap?.laptime ?: "—", TrackProColors.AccentGreen, TrackProColors.TextMuted)
                        MetricColumn("AVERAGE", avgMs.toLapTimeString(), TrackProColors.TextPrimary, TrackProColors.TextMuted)
                        MetricColumn("WORST", worstMs.toLapTimeString(),
                            if (lapMillis.size > 1) TrackProColors.AccentRed else TrackProColors.TextPrimary, TrackProColors.TextMuted)
                    }
                    HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                }

                // ── Session stats row
                item {
                    SectionHeader("SESSION STATS", TrackProColors.TextMuted, TrackProColors.SectorLine)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProColors.BgCard)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatRowItem(
                            label = "TOTAL SESSION TIME",
                            value = sessionDuration.toLapTimeString(),
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted
                        )
                        StatRowItem(
                            label = "TOP SPEED (SESSION)",
                            value = String.format("%.1f km/h", topSpeedOverall),
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted
                        )
                        StatRowItem(
                            label = "LAP COUNT",
                            value = "${lapTimes.size}",
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted
                        )
                        StatRowItem(
                            label = "CONSISTENCY (σ)",
                            value = consistency,
                            textPrimary = if (consistency != "—" &&
                                consistency.replace("%","").toDoubleOrNull()?.let { it < 1.0 } == true)
                                TrackProColors.AccentGreen else TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted
                        )
                        StatRowItem(
                            label = "GAP: BEST → WORST",
                            value = if (lapMillis.size > 1)
                                "+${(worstMs - bestMs).toLapTimeString()}" else "—",
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted
                        )
                        StatRowItem(
                            label = "PERFORMANCE TREND",
                            value = trend,
                            textPrimary = trendColor,
                            textMuted = TrackProColors.TextMuted
                        )
                    }
                    HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                }

                // ── Lap-by-lap breakdown
                item {
                    SectionHeader("LAP BREAKDOWN", TrackProColors.TextMuted, TrackProColors.SectorLine)
                }

                if (lapTimes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NO LAPS RECORDED", color = TrackProColors.TextMuted,
                                fontSize = 11.sp, letterSpacing = 3.sp)
                        }
                    }
                } else {
                    items(lapTimes) { lap ->
                        val isBest = lap.id == bestLap?.id
                        val isWorst = lap.id == worstLap?.id && lapTimes.size > 1
                        val lapMs = lap.laptime.toLapTimeMillis()
                        val deltaMs = lapMs - bestMs
                        val topSpeed = topSpeedPerLap[lap.lapnumber] ?: 0f

                        LapRow(
                            lap = lap,
                            isBest = isBest,
                            isWorst = isWorst,
                            deltaMs = deltaMs,
                            topSpeed = topSpeed,
                            bgCard = TrackProColors.BgCard,
                            bgElevated = TrackProColors.BgElevated,
                            accentGreen = TrackProColors.AccentGreen,
                            accentRed = TrackProColors.AccentRed,
                            accentAmber = TrackProColors.AccentAmber,
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted,
                            sectorLine = TrackProColors.SectorLine
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
    bgCard: Color,
    bgElevated: Color,
    accentGreen: Color,
    accentRed: Color,
    accentAmber: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color
) {
    val accentColor = when {
        isBest  -> accentGreen
        isWorst -> accentRed
        else    -> textMuted
    }
    val badge = when {
        isBest  -> "BEST"
        isWorst -> "SLOW"
        else    -> "LAP"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                if (isBest) accentGreen.copy(alpha = 0.05f) else bgCard,
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isBest) 1.dp else 0.dp,
                color = if (isBest) accentGreen.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: lap number + badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = String.format("%02d", lap.lapnumber),
                    color = accentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badge, color = accentColor, fontSize = 8.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            // Center: top speed
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOP SPEED", color = textMuted, fontSize = 8.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (topSpeed > 0) String.format("%.0f", topSpeed) else "—",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (topSpeed > 0) {
                    Text("km/h", color = textMuted, fontSize = 8.sp)
                }
            }

            // Right: lap time + delta
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = lap.laptime,
                    color = accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                if (!isBest && deltaMs > 0) {
                    Text(
                        text = "+${deltaMs.toLapTimeString()}",
                        color = accentRed.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isBest) {
                    Text(
                        text = "REFERENCE",
                        color = accentGreen.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(40.dp)
                .background(accentColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
        )
    }
}

// ── Shared sub-components ──────────────────────────────────

@Composable
private fun SectionHeader(title: String, textMuted: Color, sectorLine: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0C11))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
    }
    HorizontalDivider(color = sectorLine, thickness = 1.dp)
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: Color, textMuted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatRowItem(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textMuted, fontSize = 10.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(value, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}



// Helpers
fun String.toLapTimeMillis(): Long {
    val parts = this.split(":", ".", limit = 3)
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val millis  = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return minutes * 60_000 + seconds * 1_000 + millis
}

fun Long.toLapTimeString(): String {
    val minutes = this / 60_000
    val seconds = (this % 60_000) / 1_000
    val millis = this % 1_000
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
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
