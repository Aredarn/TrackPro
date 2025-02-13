package com.example.trackpro
import android.graphics.Color
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.example.trackpro.ui.theme.TrackProTheme
import com.yourpackage.ui.components.SevenSegmentView


@Composable
fun GraphScreen(onBack: () -> Unit) {
    var number by remember { mutableStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(50.dp)
    ) {
        SevenSegmentView(
            number = number,
            digitsNumber = 4,
            segmentsSpace = 1.dp,
            segmentWidth = 8.dp,
            digitsSpace = 16.dp,
            activeColor = androidx.compose.ui.graphics.Color.Green,
            modifier = Modifier.height(100.dp)
        )

        Button(
            onClick = { number++ }
        ) {
            Text("Increase counter")
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