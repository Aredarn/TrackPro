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
import androidx.compose.foundation.shape.RoundedCornerShape
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
            SessionInfoSection(sessionData!!)

            Spacer(modifier = Modifier.height(12.dp))

            vehicleData?.let { VehicleInfoSection(it) }

            Spacer(modifier = Modifier.height(12.dp))

            if (lapTimes.isNotEmpty()) {
                LapSummarySection(lapTimes)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LapListSection(lapTimes)
        }
    }
}

@Composable
fun SessionInfoSection(session: SessionData) {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startTime = formatter.format(Date(session.startTime))
    val duration = session.endTime?.let {
        val diff = it - session.startTime
        "${diff / 1000 / 60} min"
    } ?: "Ongoing"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Event: ${session.eventType}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text("Start: $startTime",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text("Duration: $duration",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun VehicleInfoSection(vehicle: VehicleInformationData) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSecondary)
            Text("Engine: ${vehicle.engineType} | ${vehicle.horsepower} HP",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondary)
            Text("Drivetrain: ${vehicle.drivetrain} | Weight: ${vehicle.weight} kg",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondary)
        }
    }
}

@Composable
fun LapSummarySection(laps: List<LapTimeData>) {
    val bestLap = laps.minByOrNull { it.laptime.toLapTimeMillis() }
    val avgLap = laps.map { it.laptime.toLapTimeMillis() }.average().toLong()
    val avgLapFormatted = avgLap.toLapTimeString()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Best Lap", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text(bestLap?.laptime ?: "--:--", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Average", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text(avgLapFormatted, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Laps", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("${laps.size}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
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
