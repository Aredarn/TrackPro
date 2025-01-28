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
}

@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {})
    }
}