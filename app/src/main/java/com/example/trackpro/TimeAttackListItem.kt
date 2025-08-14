package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.DataClasses.LapTimeData
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.VehicleInformationData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeAttackListItem :ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContent{
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
                vehicleData = database.vehicleInformationDAO().getVehicle(it.vehicleId).first() // Gets first emission
                Log.d("SessionData", "Session: $sessionData")

                lapTimes = database.lapTimeDataDAO().getLapsForSession(sessionId)
                Log.d("LapTimes", "Lap Times: $lapTimes")
            }
        }
    }



    if (sessionData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Session Info
            SessionInfoSection(sessionData!!)

            Spacer(modifier = Modifier.height(12.dp))

            // Vehicle Info
            vehicleData?.let { VehicleInfoSection(it) }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            if (lapTimes.isNotEmpty()) {
                LapSummarySection(lapTimes)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lap List
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
        colors = CardDefaults.cardColors(Color(0xFFE8EAF6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Event: ${session.eventType}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Start: $startTime", fontSize = 14.sp)
            Text("Duration: $duration", fontSize = 14.sp)
        }
    }
}

@Composable
fun VehicleInfoSection(vehicle: VehicleInformationData) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color(0xFFF1F8E9)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Engine: ${vehicle.engineType} | ${vehicle.horsepower} HP", fontSize = 14.sp)
            Text("Drivetrain: ${vehicle.drivetrain} | Weight: ${vehicle.weight} kg", fontSize = 14.sp)
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
        colors = CardDefaults.cardColors(Color(0xFFFFF8E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Best Lap", fontWeight = FontWeight.Bold)
                Text(bestLap?.laptime ?: "--:--", fontSize = 16.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Average", fontWeight = FontWeight.Bold)
                Text(avgLapFormatted, fontSize = 16.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Laps", fontWeight = FontWeight.Bold)
                Text("${laps.size}", fontSize = 16.sp)
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
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lap ${lap.lapnumber}", fontWeight = FontWeight.Bold)
                    Text(lap.laptime, color = Color(0xFF388E3C))
                }
            }
        }
    }
}

// Helper functions
fun String.toLapTimeMillis(): Long {
    val parts = this.split(":", ".", limit = 3) // both delimiters as literals
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