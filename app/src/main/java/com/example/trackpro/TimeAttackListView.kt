import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ESPDatabase
import com.example.trackpro.ViewModels.SessionViewModel
import com.example.trackpro.ViewModels.TrackViewModel
import com.example.trackpro.ViewModels.VehicleFULLViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAttackListViewScreen(
    navController: NavController,
    viewModel: SessionViewModel,
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleFULLViewModel,
    database: ESPDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allSessions by viewModel.sessions.collectAsState()
    val trackSessions = allSessions.filter { it.eventType != "DragSession" }
    val vehicles = vehicleViewModel.vehicles.collectAsState().value
    val tracks by trackViewModel.tracks.collectAsState()


    Log.d("trackSessions", tracks.toString())
    TrackProTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("track sessions", color = MaterialTheme.colorScheme.onBackground, fontStyle = MaterialTheme.typography.titleLarge.fontStyle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                if (trackSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No sessions available",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        items(trackSessions) { session ->
                            TrackSessionCard(
                                session = session,
                                track = tracks.find {
                                    val name = session.eventType.split(" ")
                                    Log.d("name", name.toString())
                                   it.trackName == name[0]
                                }!!,
                                vehicle = vehicles.find { it.vehicleId == session.vehicleId }!!,
                                navController = navController,
                                onDelete = { vehicleToDelete ->
                                    scope.launch(Dispatchers.IO) {
                                        // DeleteSession(context, database, vehicleToDelete.vehicleId)
                                    }
                                    Toast.makeText(
                                        context,
                                        "🚀 Session deleted successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackSessionCard(
    session: SessionData,
    vehicle: VehicleInformationData,
    track: TrackMainData,
    navController: NavController,
    onDelete: (SessionData) -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session)
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete Session?") },
            text = { Text("Are you sure you want to delete this session?") }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .combinedClickable(
                onClick = { navController.navigate("timeattacklistitem/${session.id}") },
                onLongClick = { showDeleteDialog = true }
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505))
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                //Upper row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TRACK SESSION",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Green
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .background(
                                color = Color(0xFF1B5E20), // darker green
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )


                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Instant.ofEpochMilli(session.startTime)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                                .format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.Green
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = " – ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.Green
                            )
                        )
                        Text(
                            text = session.endTime?.let {
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalTime()
                                    .format(timeFormatter)
                            } ?: "Ongoing",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.Green
                            ),
                            maxLines = 1
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(6.dp))

                //middle row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.trackName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${track.country} • ${track.type}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.LightGray
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Total Length: ${track.totalLength ?: "N/A"} km",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(6.dp))

                //bottom row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .padding(vertical = 6.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${vehicle.engineType} • ${vehicle.horsepower}hp • ${vehicle.drivetrain}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }
            /*
            IconButton(
                onClick = { onDelete(session) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Session",
                    tint = MaterialTheme.colorScheme.error
                )
            }*/
        }
    }
}
