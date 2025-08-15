package com.example.trackpro

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ui.theme.TrackProTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trackpro.ManagerClasses.TimeAttackManagers.TimingMode
import com.example.trackpro.ViewModels.SessionViewModel
import com.example.trackpro.ViewModels.SessionViewModelFactory
import com.example.trackpro.ViewModels.TrackViewModel
import com.example.trackpro.ViewModels.TrackViewModelFactory
import com.example.trackpro.ViewModels.VehicleFULLViewModel
import com.example.trackpro.ViewModels.VehicleFULLViewModelFactory
import kotlinx.coroutines.launch

class TrackProApp : Application() {

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val database = ESPDatabase.getInstance(applicationContext)
            val sessionViewModel: SessionViewModel =
                viewModel(factory = SessionViewModelFactory(applicationContext))
            val trackViewModel: TrackViewModel =
                viewModel(factory = TrackViewModelFactory(applicationContext))
            val vehicleFULLViewModel: VehicleFULLViewModel =
                viewModel(factory = VehicleFULLViewModelFactory(applicationContext))

            TrackProTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onNavigateToDragRace = { navController.navigate("drag") },
                            onNavigateToESPTestScreen = { navController.navigate("esptest") },
                            onNavigateToTrackListScreen = { navController.navigate("tracklist") },
                            onNavigateToTrackBuilder = { navController.navigate("trackbuilder") },
                            onNavigateToDragTimesList = { navController.navigate("dragsessions") },
                            onNavigateToVehicleCreatorScreen = { navController.navigate("createvehicle") },
                            onNavigateToVehicleList = { navController.navigate("vehicles") },
                            onNavigateToTrackVehicleSelector = { navController.navigate("trackandvehicle") },
                            onNavigateToTimeAttackListView = { navController.navigate("timeattacklist") }
                        )
                    }
                    composable("drag") {
                        DragRaceScreen(
                            database = database,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("esptest") {
                        ESPConnectionTestScreen()
                    }
                    composable(
                        "track/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
                        TrackScreen(onBack = { navController.popBackStack() }, trackId = trackId)
                    }
                    composable("dragsessions") {
                        DragTimesListView(
                            viewModel = sessionViewModel,
                            navController = navController
                        )
                    }
                    composable("vehicles")
                    {
                        CarListScreen(
                            viewModel = vehicleFULLViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "graph/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                        GraphScreen(
                            onBack = { navController.popBackStack() },
                            sessionId = sessionId
                        )
                    }
                    composable(
                        route = "trackbuilder"
                    ) {
                        TrackBuilderScreen(database, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "tracklist"
                    )
                    {
                        TrackListScreen(navController = navController, viewModel = trackViewModel)
                    }
                    composable(
                        route = "vehicle/{vehicleid}",
                        arguments = listOf(navArgument("vehicleid") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getLong("vehicleid") ?: 0L
                        CarViewScreen(
                            onBack = { navController.popBackStack() },
                            vehicleId = vehicleId
                        )
                    }
                    composable(
                        route = "timeattacklistitem/{sessionid}",
                        arguments = listOf(navArgument("sessionid") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionid = backStackEntry.arguments?.getLong("sessionid") ?: 0L
                        TimeAttackListItemScreen(
                            navController = navController,
                            database = database,
                            sessionId = sessionid,
                        )
                    }
                    composable(
                        route = "createvehicle"
                    )
                    {
                        CarCreationScreen(database) { }
                    }
                    composable(
                        route = "timeattack/{vehicleId}/{trackId}"
                    ) { backStackEntry ->

                        val vehicleId =
                            backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull() ?: -1L
                        val trackId =
                            backStackEntry.arguments?.getString("trackId")?.toLongOrNull() ?: -1L

                        TimeAttackScreenView(
                            vehicleId = vehicleId,
                            trackId = trackId,
                            database = database,
                            onBack = {}
                        )
                    }

                    composable(route = "trackandvehicle") {
                        TrackVehicleSelectorScreenWrapper(navController = navController)
                    }
                    composable(route = "timeattacklist")
                    {
                        TimeAttackListViewScreen(navController = navController, viewModel = sessionViewModel,vehicleViewModel = vehicleFULLViewModel,database = database)
                    }

                }

            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDragRace: () -> Unit,
    onNavigateToESPTestScreen: () -> Unit,
    onNavigateToTrackListScreen: () -> Unit,
    onNavigateToTrackBuilder: () -> Unit,
    onNavigateToDragTimesList: () -> Unit,
    onNavigateToVehicleCreatorScreen: () -> Unit,
    onNavigateToVehicleList: () -> Unit,
    onNavigateToTrackVehicleSelector: () -> Unit,
    onNavigateToTimeAttackListView: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "TrackPro",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    NavigationDrawerItem(
                        label = { Text("My drag sessions") },
                        selected = false,
                        onClick = {
                            onNavigateToDragTimesList()
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("My track sessions") },
                        selected = false,
                        onClick = {
                            onNavigateToTimeAttackListView()
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text("My tracks") },
                        selected = false,
                        onClick = {
                            onNavigateToTrackListScreen()
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("My vehicles") },
                        selected = false,
                        onClick = {
                            onNavigateToVehicleList()
                        }
                    )

                    Text(
                        "Section 2",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        //icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick = { /* Handle click */ }
                    )
                    NavigationDrawerItem(
                        label = { Text("Help and feedback") },
                        selected = false,
                        //icon = {, contentDescription = null) },
                        onClick = { /* Handle click */ },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            val colorRaceMode = Color(0xFF2B2F42)       // Steel Gray
            val colorVehicle = Color(0xFF414770)        // Muted Indigo
            val colorTrack = Color(0xFF3A3F58)          // Charcoal Blue
            val colorESP = Color(0xFF2F4858)            // Dark Teal
            val contentColor = Color.White

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to TRACKPRO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colorRaceMode
                    ),
                    modifier = Modifier.padding(8.dp)
                )

                Text(
                    text = "Ready to beat records?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                val buttonModifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 12.dp)

                val shape = RoundedCornerShape(14.dp)
                val elevation = ButtonDefaults.elevatedButtonElevation(6.dp)

                @Composable
                fun RacingButton(
                    icon: ImageVector,
                    label: String,
                    onClick: () -> Unit,
                    background: Color,
                    content: Color
                ) {
                    Button(
                        onClick = onClick,
                        modifier = buttonModifier,
                        shape = shape,
                        elevation = elevation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = background,
                            contentColor = content
                        )
                    ) {
                        Icon(icon, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }

                // 🏁 Race Mode
                RacingButton(
                    Icons.Default.RocketLaunch,
                    "Drag Screen",
                    onNavigateToDragRace,
                    colorRaceMode,
                    Color.White
                )
                RacingButton(
                    Icons.Default.FlagCircle,
                    "Lap Timing",
                    onNavigateToTrackVehicleSelector,
                    colorRaceMode,
                    Color.White
                )

                // 🔧 Vehicle Setup
                RacingButton(
                    Icons.Default.CarRepair,
                    "Add Your Vehicle",
                    onNavigateToVehicleCreatorScreen,
                    colorVehicle,
                    Color.White
                )

                // 🛠 Track Management
                RacingButton(
                    Icons.Default.Timelapse,
                    "Track Builder",
                    onNavigateToTrackBuilder,
                    colorTrack,
                    Color.White
                )

                // 📡 Connectivity
                RacingButton(
                    Icons.Default.Wifi,
                    "ESP Connection",
                    onNavigateToESPTestScreen,
                    colorESP,
                    Color.White
                )
            }

        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TrackProTheme {
        MainScreen(
            onNavigateToDragRace = {},
            onNavigateToESPTestScreen = {},
            onNavigateToTrackListScreen = {},
            onNavigateToTrackBuilder = {},
            onNavigateToDragTimesList = {},
            onNavigateToVehicleCreatorScreen = {},
            onNavigateToVehicleList = {},
            onNavigateToTrackVehicleSelector = {},
            onNavigateToTimeAttackListView = {}
        )
    }
}
