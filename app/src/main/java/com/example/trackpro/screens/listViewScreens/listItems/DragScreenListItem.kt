package com.example.trackpro.screens.listViewScreens.listItems

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.dataClasses.convertToLatLonOffsetList
import com.example.trackpro.screens.telemetricScreens.DragMetricCard
import com.example.trackpro.screens.telemetricScreens.DragMetricDisplay
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.ui.theme.TrackProTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.*


// Haversine distance between two GPS points (meters)
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}


@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf(emptyList<RawGPSData>()) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    var totalDist by remember { mutableDoubleStateOf(-1.0) }
    
    // X = cumulative meters or seconds, Y = speed (km/h)
    val dataPointsMeters = remember { mutableListOf<Entry>() }
    val dataPointsSeconds = remember { mutableListOf<Entry>() }
    var xAxisInMeters by remember { mutableStateOf(true) }
    
    // Fixed: Removed 'get()' as local variables don't support custom getters
    val dataPoints = if (xAxisInMeters) dataPointsMeters else dataPointsSeconds
    
    var metrics by remember { mutableStateOf(DragMetrics()) }
    val calculator = remember { DragTimeCalculation(sessionId, database) }

    var maxSpeed by remember { mutableDoubleStateOf(-1.0) }
    var avgSpeed by remember { mutableDoubleStateOf(-1.0) }
    var maxAcceleration by remember { mutableDoubleStateOf(-1.0) }

    // Elevation stats: net gain, total climb, total descent
    var elevationNet by remember { mutableDoubleStateOf(0.0) }
    var elevationGain by remember { mutableDoubleStateOf(0.0) }
    var elevationLoss by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val data = database.rawGPSDataDao().getGPSDataBySession(sessionId)
            if (data.isEmpty()) return@withContext

            // Build cumulative distance array for X axis
            val cumulativeDist = DoubleArray(data.size)
            for (i in 1 until data.size) {
                val prev = data[i - 1]
                val curr = data[i]
                val segDist = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
                cumulativeDist[i] = cumulativeDist[i - 1] + segDist
            }

            val t0 = data.first().timestamp
            dataPointsMeters.clear()
            dataPointsSeconds.clear()
            data.forEachIndexed { i, d ->
                d.speed?.let {
                    dataPointsMeters.add(Entry(cumulativeDist[i].toFloat(), it))
                    dataPointsSeconds.add(Entry(((d.timestamp - t0) / 1000f), it))
                }
            }

            val simplifiedData = convertToLatLonOffsetList(data)
            val calculatedMetrics = calculator.calculateFullSessionMetrics(data)
            val totalDistValue = dragTimeClass.totalDistance(simplifiedData)

            val speeds = data.mapNotNull { it.speed }
            val maxSpeedValue = speeds.maxOrNull()?.toDouble() ?: -1.0
            val avgSpeedValue = if (speeds.isNotEmpty()) speeds.average() else -1.0

            var maxAccel = -1.0
            for (i in 1 until data.size) {
                val dSpeed = (data[i].speed ?: continue) - (data[i - 1].speed ?: continue)
                val dTime = (data[i].timestamp - data[i - 1].timestamp) / 1000.0
                if (dTime > 0) {
                    val accel = dSpeed / dTime
                    if (accel > maxAccel) maxAccel = accel
                }
            }

            // Elevation: sum climbs and descents separately
            var gain = 0.0
            var loss = 0.0
            for (i in 1 until data.size) {
                val alt1 = data[i - 1].altitude ?: continue
                val alt2 = data[i].altitude ?: continue
                val delta = alt2 - alt1
                if (delta > 0) gain += delta else loss += delta
            }
            val firstAlt = data.firstOrNull { it.altitude != null }?.altitude
            val lastAlt  = data.lastOrNull  { it.altitude != null }?.altitude
            val net = if (firstAlt != null && lastAlt != null) lastAlt - firstAlt else 0.0

            withContext(Dispatchers.Main) {
                coordinates = data
                metrics = calculatedMetrics
                totalDist = totalDistValue
                maxSpeed = maxSpeedValue
                avgSpeed = avgSpeedValue
                maxAcceleration = maxAccel
                elevationGain = gain
                elevationLoss = loss
                elevationNet = net
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        // ── Top bar ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProColors.AccentRed)
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Text(
                text = "● SESSION OVERVIEW",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
        }

        // ── Compact stats panel ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProColors.BgCard)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    CompactStat(
                        label = "DURATION",
                        value = formatTime(totalTime),
                        textMuted = TrackProColors.TextMuted,
                        valueColor = TrackProColors.TextPrimary
                    )
                }
                CompactStat(
                    label = "DISTANCE",
                    value = when {
                        totalDist <= 0 -> "—"
                        totalDist >= 1000 -> String.format("%.2f km", totalDist / 1000.0)
                        else -> String.format("%.0f m", totalDist)
                    },
                    textMuted = TrackProColors.TextMuted,
                    valueColor = TrackProColors.TextPrimary
                )
            }

            Divider(color = TrackProColors.SectorLine, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DragMetricCard(DragMetricDisplay("TOP SPEED",  if (maxSpeed > 0) String.format("%.0f", maxSpeed) else "—", "KM/H", maxSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("AVG SPEED",  if (avgSpeed > 0) String.format("%.0f", avgSpeed) else "—", "KM/H", avgSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("MAX ACCEL",  if (maxAcceleration > 0) String.format("%.1f", maxAcceleration) else "—", "KM/H/S", maxAcceleration > 0), modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val hasElevation = elevationGain != 0.0 || elevationLoss != 0.0
                val netLabel  = if (elevationNet >= 0) "+%.0f m".format(elevationNet) else "%.0f m".format(elevationNet)
                val gainLabel = "+%.0f m".format(elevationGain)
                val lossLabel = "%.0f m".format(elevationLoss)
                DragMetricCard(DragMetricDisplay("ELEV NET",  if (hasElevation) netLabel  else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("ELEV ↑",    if (hasElevation) gainLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("ELEV ↓",    if (hasElevation) lossLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DragMetricCard(DragMetricDisplay("0-60",  formatMetric(metrics.time0to60),  "SEC", metrics.time0to60 != null),  modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("0-100", formatMetric(metrics.time0to100), "SEC", metrics.time0to100 != null), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("0-160", formatMetric(metrics.time0to160), "SEC", metrics.time0to160 != null), modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DragMetricCard(DragMetricDisplay("100-200",  formatMetric(metrics.time100to200),               "SEC",   metrics.time100to200 != null),   modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("¼ MILE",   formatMetric(metrics.quarterMileTime),            "SEC",   metrics.quarterMileTime != null), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("TRAP SPD", metrics.quarterMileSpeed?.toInt()?.toString() ?: "—", "KM/H", metrics.quarterMileSpeed != null), modifier = Modifier.weight(1f))
            }
        }

        Divider(color = TrackProColors.SectorLine, thickness = 1.dp)

        // ── Chart header ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProColors.BgCard)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPEED TRACE",
                color = TrackProColors.TextMuted,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(true to "METER", false to "SEC").forEach { (isMeters, label) ->
                    val active = xAxisInMeters == isMeters
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) TrackProColors.AccentRed else TrackProColors.SectorLine,
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            )
                            .clickable { xAxisInMeters = isMeters }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.Black else TrackProColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        Divider(color = TrackProColors.SectorLine, thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProColors.BgCard)
        ) {
            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupChartStyle()
                    }
                },
                update = { chart ->
                    val dataSet = LineDataSet(dataPoints, "Speed").apply {
                        setDrawValues(false)
                        setDrawCircles(false)
                        lineWidth = 2f
                        color = android.graphics.Color.parseColor("#E8001C")
                        setDrawFilled(true)
                        fillColor = android.graphics.Color.parseColor("#E8001C")
                        fillAlpha = 40
                    }
                    chart.data = LineData(dataSet)
                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return if (xAxisInMeters) "${value.toInt()}m" else "${value.toInt()}s"
                        }
                    }
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }
}

private fun LineChart.setupChartStyle() {
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(false)
        textColor = android.graphics.Color.parseColor("#6B7280")
        textSize = 8f
    }
    axisLeft.apply {
        textColor = android.graphics.Color.parseColor("#6B7280")
        gridColor = android.graphics.Color.parseColor("#1E2530")
        textSize = 8f
        axisMinimum = 0f
    }
    axisRight.isEnabled = false
    description.isEnabled = false
    legend.isEnabled = false
    setNoDataText("Calculating data...")
    setNoDataTextColor(android.graphics.Color.WHITE)
    setBackgroundColor(android.graphics.Color.parseColor("#0E1117"))
}

@Composable
private fun CompactStat(label: String, value: String, textMuted: Color, valueColor: Color) {
    Column {
        Text(text = label, color = textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {}, 1)
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    val millis = milliseconds % 1000
    return String.format("%02d:%02d.%02d", minutes, seconds, millis / 10)
}

private fun formatMetric(value: Double?): String {
    return value?.let { String.format("%.2f", it) } ?: "—"
}