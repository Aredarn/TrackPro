package com.example.trackpro

import DarkColors
import LightColors
import TrackProTheme
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.DataClasses.LapTimeData
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.theme.Teal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()

    var sessionData by remember { mutableStateOf<SessionData?>(null) }
    var vehicleData by remember { mutableStateOf<VehicleInformationData?>(null) }
    var lapTimes by remember { mutableStateOf<List<LapTimeData>>(emptyList()) }

    LaunchedEffect(sessionId) {
        coroutineScope.launch {
            sessionData = database.sessionDataDao().getSessionById(sessionId)
            sessionData?.let {
                vehicleData = database.vehicleInformationDAO().getVehicle(it.vehicleId).first()
                Log.d("SessionData", "Session: $sessionData")

                lapTimes = database.lapTimeDataDAO().getLapsForSession(sessionId)
                Log.d("LapTimes", "Lap Times: $lapTimes")
            }
        }
    }

    if (sessionData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Teal)
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {


            if (sessionData != null && vehicleData != null) {
                TimeAttackSessionDetails(
                    session = sessionData!!,
                    vehicle = vehicleData!!,
                    laps = lapTimes
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LapListSection(lapTimes)
        }
    }
}

@Composable
fun TimeAttackSessionDetails(
    session: SessionData,
    vehicle: VehicleInformationData,
    laps: List<LapTimeData>
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // --- Session Info ---
            SessionInfoContent(session)

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            // --- Vehicle Info ---
            VehicleInfoContent(vehicle)

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            // --- Lap Summary ---
            LapSummaryContent(laps)
        }
    }
}

@Composable
private fun SessionInfoContent(session: SessionData) {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startTime = formatter.format(Date(session.startTime))
    val duration = session.endTime?.let {
        val diff = it - session.startTime
        "${diff / 1000 / 60} min"
    } ?: "Ongoing"

    Column {
        Text(
            text = session.eventType,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Start • $startTime",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
        Text(
            text = "Duration • $duration",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
private fun VehicleInfoContent(vehicle: VehicleInformationData) {
    Column {
        Text(
            text = "${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Engine • ${vehicle.engineType} | ${vehicle.horsepower} HP",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
        Text(
            text = "Drivetrain • ${vehicle.drivetrain} | ${vehicle.weight} kg",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
private fun LapSummaryContent(laps: List<LapTimeData>) {
    val bestLap = laps.minByOrNull { it.laptime.toLapTimeMillis() }
    val avgLap = laps.map { it.laptime.toLapTimeMillis() }.average().toLong()
    val avgLapFormatted = avgLap.toLapTimeString()

    Row(
        Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏁 Best Lap", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(bestLap?.laptime ?: "--:--", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📊 Average", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(avgLapFormatted, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔢 Laps", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("${laps.size}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun LapListSection(laps: List<LapTimeData>) {
    LazyColumn {
        items(laps) { lap ->
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lap ${lap.lapnumber}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(lap.laptime, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
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
