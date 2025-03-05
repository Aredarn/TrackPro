package com.example.trackpro.ui.screens

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.ESPDatabase
import com.example.trackpro.DataClasses.SessionData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DragTimesList : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Initialize the NavController here
            val navController = rememberNavController()

            // the navController and the viewModel to DragTimesListView
            val viewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(this))
            DragTimesListView(viewModel = viewModel, navController = navController)
        }
    }
}


// ViewModel to handle session data retrieval
class SessionViewModel(private val database: ESPDatabase) : ViewModel() {
    private var _sessions = MutableStateFlow<List<SessionData>>(emptyList())
    val sessions = _sessions.asStateFlow()

    init {
        fetchSessions()
    }

    private fun fetchSessions() {
        viewModelScope.launch {
            _sessions.value = database.sessionDataDao().getAllSessions();
        }
    }
}

class SessionViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SessionViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}

@Composable
fun DragTimesListView(viewModel: SessionViewModel, navController: NavController) {
    val sessionList by viewModel.sessions.collectAsState()

    SessionListScreen(navController = navController, sessions = sessionList)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(navController: NavController, sessions: List<SessionData>) {
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
                    items(sessions) { session ->
                        SessionCard(session, navController)
                    }
                }
            }
        }
    }
}


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

