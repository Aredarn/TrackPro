package com.example.trackpro.Screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESPDatabase
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.convertToLatLonOffsetList
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ui.theme.TrackProTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit


@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {

    val screenHeight = LocalConfiguration.current.screenHeightDp

    val context = LocalContext.current
    val database = remember { ESPDatabase.Companion.getInstance(context) }
    var coordinates: List<RawGPSData> by remember { mutableStateOf(emptyList<RawGPSData>()) }
    var coordinatesSimplified by remember { mutableStateOf(emptyList<LatLonOffset>()) }
    var dragTime by remember { mutableDoubleStateOf(-1.0) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    val scope = rememberCoroutineScope()
    var totalDist by remember { mutableDoubleStateOf(-1.0) }
    var quarterMileTime by remember { mutableDoubleStateOf(-1.0) }
    val dataPoints = remember { mutableListOf<Entry>() }
    val margin = 16f

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            val data = database.rawGPSDataDao().getGPSDataBySession(sessionId)

            data.forEachIndexed { index, data ->
                data.speed?.let { Entry(index.toFloat(), it) }
                    ?.let { dataPoints.add(it) }
            }


            val simplifiedData = convertToLatLonOffsetList(data)
            val dragTimeValue = dragTimeClass.timeFromZeroToHundred()
            val totalDistValue = dragTimeClass.totalDistance(simplifiedData)
            val quarterMile = dragTimeClass.quarterMile()


            withContext(Dispatchers.Main) {
                coordinates = data
                coordinatesSimplified = simplifiedData
                dragTime = dragTimeValue
                totalDist = totalDistValue
                quarterMileTime = quarterMile
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Session Overview",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Time of Creation:")
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    Text("Total Time: ${formatTime(totalTime)}")
                } else {
                    Text("No data available")
                }
                Text("Total Distance: $totalDist km")
                Text("0-100 Time: ${if (dragTime > 0) "$dragTime sec" else "No 0-100 detected"}")
                Text("1/4 Mile: ${if (quarterMileTime >0) "$quarterMileTime sec" else "No 1/4 mile run detected"}")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val pagerState = rememberPagerState { 2 } // Two pages: Canvas & Chart

        Column {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> {
                        val mapContext = LocalContext.current
                        var mapViewRef by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }

                        AndroidView(
                            factory = { ctx ->
                                org.maplibre.android.MapLibre.getInstance(ctx)
                                org.maplibre.android.maps.MapView(ctx).also { mv ->
                                    mapViewRef = mv
                                    mv.onCreate(null)
                                    mv.getMapAsync { map ->
                                        map.setStyle("https://tiles.openfreemap.org/styles/dark")
                                    }
                                }
                            },
                            update = { mv ->
                                if (coordinatesSimplified.isNotEmpty()) {
                                    mv.getMapAsync { map ->
                                        map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                                            // Convert your LatLonOffset list to MapLibre LatLng list
                                            val points = coordinatesSimplified.map {
                                                org.maplibre.android.geometry.LatLng(it.lat, it.lon)
                                            }

                                            // Add the track as a GeoJSON line
                                            val lineString = com.google.gson.JsonObject().apply {
                                                addProperty("type", "Feature")
                                                add("geometry", com.google.gson.JsonObject().apply {
                                                    addProperty("type", "LineString")
                                                    add("coordinates", com.google.gson.JsonArray().apply {
                                                        points.forEach { pt ->
                                                            add(com.google.gson.JsonArray().apply {
                                                                add(pt.longitude)
                                                                add(pt.latitude)
                                                            })
                                                        }
                                                    })
                                                })
                                                add("properties", com.google.gson.JsonObject())
                                            }

                                            style.addSource(
                                                org.maplibre.android.style.sources.GeoJsonSource(
                                                    "track-source",
                                                    lineString.toString()
                                                )
                                            )

                                            style.addLayer(
                                                org.maplibre.android.style.layers.LineLayer("track-layer", "track-source").apply {
                                                    setProperties(
                                                        org.maplibre.android.style.layers.PropertyFactory.lineColor("#FF0000"),
                                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(3f),
                                                        org.maplibre.android.style.layers.PropertyFactory.lineCap(
                                                            org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                                                        ),
                                                        org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                                                            org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                                                        )
                                                    )
                                                }
                                            )

                                            // Fit camera to track bounds
                                            val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                                .includes(points)
                                                .build()
                                            map.easeCamera(
                                                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 64),
                                                1000
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )

                        // Forward lifecycle to MapView
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                when (event) {
                                    androidx.lifecycle.Lifecycle.Event.ON_START -> mapViewRef?.onStart()
                                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapViewRef?.onStop()
                                    androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDestroy()
                                    else -> {}
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }
                    }
                    1 -> {
                        // Second page: Line Chart
                        AndroidView(
                            factory = { context ->
                                LineChart(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                    // Customize the chart
                                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                                    xAxis.setDrawGridLines(false)
                                    axisRight.isEnabled = false
                                    description.isEnabled = false
                                }
                            },
                            update = { chart ->
                                val dataSet = LineDataSet(dataPoints, "Speed (km/h)").apply {
                                    setDrawValues(false)
                                    setDrawCircles(false)
                                    lineWidth = 4f
                                    color = android.graphics.Color.RED
                                    setDrawFilled(true)
                                    fillColor = android.graphics.Color.parseColor("#80FF0000")
                                }

                                if (chart.data == null) {
                                    chart.data = LineData(dataSet)
                                } else {
                                    chart.data.clearValues()
                                    chart.data.addDataSet(dataSet)
                                }

                                chart.notifyDataSetChanged()
                                chart.postInvalidate()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((screenHeight / 2).dp)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {},1)
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    val millis = milliseconds % 1000

    return String.format("%02d:%02d.%02d", minutes, seconds, millis)
}