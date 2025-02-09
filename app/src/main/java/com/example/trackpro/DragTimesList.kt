package com.example.trackpro.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.ESPDatabase
import com.example.trackpro.DataClasses.SessionData
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.selects.SelectInstance

class DragTimesList() : ComponentActivity() {
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ESPDatabase.getInstance(applicationContext)
    }
}

@Composable
fun DragTimesListView() {
    LaunchedEffect(Unit) {
        // Database or UI-related operations can be added here
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(navController: NavController, sessions: List<SessionData>) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Your Sessions") })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sessions available", fontSize = 18.sp, color = Color.Gray)
                }
            } else {
                SessionList(sessions, navController)
            }
        }
    }
}

@Composable
fun SessionList(sessions: List<SessionData>, navController: NavController) {
    Column(modifier = Modifier.padding(16.dp)) {
        sessions.forEach { session ->
            SessionCard(session, navController)
        }
    }
}

@Composable
fun SessionCard(session: SessionData, navController: NavController) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startTimeFormatted = dateFormat.format(Date(session.startTime))
    val endTimeFormatted = session.endTime?.let { dateFormat.format(Date(it)) } ?: "Ongoing"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { /* Navigate to session details */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Event Type: ${session.eventType}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Start: $startTimeFormatted", fontSize = 14.sp, color = Color.DarkGray)
            Text("End: $endTimeFormatted", fontSize = 14.sp, color = Color.DarkGray)
        }
    }
}
