package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.Models.DragSessionWithVehicle
import com.example.trackpro.ViewModels.SessionViewModel
import com.example.trackpro.ViewModels.SessionViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DragTimesList : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            //To get all data dynamically from the DB
            val viewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(this))

            DragTimesListView(viewModel = viewModel, navController = navController)
        }
    }
}


@Composable
fun DragTimesListView(viewModel: SessionViewModel, navController: NavController) {
    val sessionList by viewModel.sessions.collectAsState()
    val sessionWithVehicleList by viewModel.sessionsWithVehicle.collectAsState()

    Log.d("Cars:" ,sessionWithVehicleList.toString())
    SessionListScreen(navController = navController, sessions = sessionList, sessionsWithVehicles = sessionWithVehicleList)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(navController: NavController, sessions: List<SessionData>, sessionsWithVehicles: List<DragSessionWithVehicle>) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Your Sessions") }) }
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
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(sessionsWithVehicles) { session ->
                        SessionCard(session, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(session: DragSessionWithVehicle, navController: NavController?) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startTimeFormatted = dateFormat.format(Date(session.startTime))
    val endTimeFormatted = session.endTime?.let { dateFormat.format(Date(it)) } ?: "..."

    Log.d("Car inside:", session.toString())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                if (navController == null) {
                    Log.e("Navigation Error", "navController is null!")
                } else {
                    navController.navigate("graph/${session.sessionId}")
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${session.manufacturer}${session.model} - ${session.year}",
                fontSize = 20.sp,
                fontWeight = FontWeight.W800
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Start: $startTimeFormatted", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Text("End: $endTimeFormatted", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/*
@Composable
fun SessionCard(session: SessionData, navController: NavController?) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startTimeFormatted = dateFormat.format(Date(session.startTime))
    val endTimeFormatted = session.endTime?.let { dateFormat.format(Date(it)) } ?: "In Progress"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                if (navController == null) {
                    Log.e("Navigation Error", "navController is null!")
                } else {
                    navController.navigate("graph/${session.id}")
                }
            },
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

*/