package com.example.trackpro
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.compose.ui.tooling.preview.Preview
import com.example.trackpro.ui.theme.TrackProTheme


@Composable
fun GraphScreen(onBack: () -> Unit) {
    // State to hold graph data
    val dataPoints = remember { mutableStateListOf<Entry>() }

    // Dummy function to simulate ESP32 data updates
    LaunchedEffect(Unit) {
        for (i in 0..20) {
            // Simulate receiving new data every 500ms
            kotlinx.coroutines.delay(500)
            dataPoints.add(Entry(i.toFloat(), i * (10..200).random().toFloat()))
        }
    }

    // Draw the graph using MPAndroidChart
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
            // Update chart data
            val dataSet = LineDataSet(dataPoints, "Speed (km/h)")
            dataSet.setDrawValues(false)
            dataSet.setDrawCircles(false)

            chart.data = LineData(dataSet)
            chart.invalidate() // Refresh the chart
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {})
    }
}