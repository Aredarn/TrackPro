import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DragRaceScreen() {
    var elapsedTime by remember { mutableStateOf(0L) } // Elapsed time in milliseconds
    var currentSpeed by remember { mutableStateOf(0.0) } // Current speed in km/h
    var distanceCovered by remember { mutableStateOf(0.0) } // Distance in meters
    var isRunning by remember { mutableStateOf(false) }

    // Mock GPS data for speed and distance simulation
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning) {
                delay(100) // Update every 100ms
                elapsedTime += 100
                currentSpeed = simulateSpeed(elapsedTime)
                distanceCovered += currentSpeed * (100 / 3600.0) // Convert speed to distance
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timer Display
        // Timer Display
        Text(
            text = "Elapsed Time: ${elapsedTime / 1000.0} seconds",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Speed Display
        Text(
            text = "Current Speed: ${String.format("%.2f", currentSpeed)} km/h",
            fontSize = 18.sp
        )

        // Distance Display
        Text(
            text = "Distance Covered: ${String.format("%.2f", distanceCovered)} meters",
            fontSize = 18.sp
        )

        // Start/Stop Button
        Button(
            onClick = { isRunning = !isRunning },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color.Red else Color.Green
            )
        ) {
            Text(
                text = if (isRunning) "Stop" else "Start",
                color = Color.White
            )
        }

        // Race Progress Visualization (Simple Bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Color.Gray, RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((distanceCovered / 1000 * 300).dp) // Scale distance for bar width
                    .background(Color.Blue, RoundedCornerShape(10.dp))
            )
        }
    }
}

// Mock function to simulate speed (replace with real GPS data later)
fun simulateSpeed(elapsedTime: Long): Double {
    return when {
        elapsedTime < 5000 -> elapsedTime / 500.0 * 60 // Accelerate to 60 km/h in 5 seconds
        elapsedTime < 10000 -> 60.0 + (elapsedTime - 5000) / 500.0 * 40 // Accelerate to 100 km/h
        else -> 100.0 // Constant speed
    }
}



@Preview(showBackground = true)
@Composable
fun DragScreenPreview()
{
    DragRaceScreen()

}


