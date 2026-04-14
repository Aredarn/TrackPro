package com.example.trackpro

import TrackProTheme
import android.Manifest
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.gpsDataManagers.ESPTcpClient
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.managerClasses.gpsDataManagers.GpsManager
import com.example.trackpro.managerClasses.gpsDataManagers.PhoneGpsProvider
import com.example.trackpro.screens.CarCreationScreen
import com.example.trackpro.screens.DragRaceScreen
import com.example.trackpro.screens.ESPConnectionTestScreen
import com.example.trackpro.screens.TimeAttackScreenView
import com.example.trackpro.screens.TrackBuilderScreen
import com.example.trackpro.screens.TrackScreen
import com.example.trackpro.screens.TrackVehicleSelectorScreen
import com.example.trackpro.screens.listViewScreens.CarListScreen
import com.example.trackpro.screens.listViewScreens.DragTimesListView
import com.example.trackpro.screens.listViewScreens.TimeAttackListViewScreen
import com.example.trackpro.screens.listViewScreens.TrackListScreen
import com.example.trackpro.screens.listViewScreens.listItems.CarViewScreen
import com.example.trackpro.screens.listViewScreens.listItems.GraphScreen
import com.example.trackpro.screens.listViewScreens.listItems.TimeAttackListItemScreen
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.SessionViewModelFactory
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.TrackViewModelFactory
import com.example.trackpro.viewModels.VehicleFULLViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModelFactory
import com.example.trackpro.viewModels.VehicleViewModel
import com.example.trackpro.viewModels.VehicleViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class TrackProApp : Application() {

    val database: ESPDatabase by lazy { ESPDatabase.getInstance(this) }
    val sessionManager: SessionManager by lazy { SessionManager.getInstance(database) }

    val espTcpClient: ESPTcpClient by lazy {
        val config = JsonReader.loadConfig(this)
        ESPTcpClient(serverAddress = config.first, port = config.second)
    }

    val phoneGpsProvider: PhoneGpsProvider by lazy {
        PhoneGpsProvider(this)
    }

    val useExternalGps = MutableStateFlow(true)

    val gpsManager: GpsManager by lazy {
        GpsManager(
            espProvider = espTcpClient,
            phoneProvider = phoneGpsProvider,
            useExternalGps = useExternalGps
        )
    }

    // ── Single source of truth ─────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGpsFlow: Flow<RawGPSData?> = useExternalGps.flatMapLatest { external ->
        if (external) espTcpClient.gpsFlow else phoneGpsProvider.gpsFlow
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStatus: Flow<Boolean> = useExternalGps.flatMapLatest { external ->
        if (external) espTcpClient.connectionStatus else phoneGpsProvider.connectionStatus
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Start the active provider immediately at app launch
        gpsManager.startActiveProvider()
    }

    override fun onTerminate() {
        super.onTerminate()
        gpsManager.stopActiveProvider()
    }
}

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fineGranted && !coarseGranted) {
            // User denied — phone GPS won't work, ESP32 still will
            Log.w("Permissions", "Location permission denied — phone GPS unavailable")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        val database = (application as TrackProApp).database
        val sessionManager = (application as TrackProApp).sessionManager
        val espTcpCLient = (application as TrackProApp).espTcpClient
        val app = application as TrackProApp
        val context = applicationContext




        //FIX SO ALL 4 USE THE SAME
        //DB params
        val vehicleViewModel = VehicleViewModelFactory(database).create(VehicleViewModel::class.java)
        val trackViewModel = TrackViewModelFactory(database).create(TrackViewModel::class.java)

        //Conetext params:
        val vehicleFULLViewModel = VehicleFULLViewModelFactory(context).create(VehicleFULLViewModel::class.java)
        val sessionViewModel = SessionViewModelFactory(context).create(SessionViewModel::class.java)


        setContent {
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
                        DragRaceScreen(database, sessionManager)
                    }
                    composable("esptest") {
                        ESPConnectionTestScreen()
                    }
                    composable(
                        "track/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
                        TrackScreen(trackId = trackId)
                    }
                    composable("dragsessions") {
                        DragTimesListView(viewModel = sessionViewModel, navController = navController)
                    }
                    composable("vehicles") {
                        CarListScreen(viewModel = vehicleFULLViewModel, navController = navController)
                    }
                    composable(
                        route = "graph/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                        GraphScreen(onBack = { navController.popBackStack() }, sessionId = sessionId)
                    }
                    composable(route = "trackbuilder") {
                        TrackBuilderScreen(database, onBack = { navController.popBackStack() })
                    }
                    composable(route = "tracklist") {
                        TrackListScreen(navController = navController, viewModel = trackViewModel)
                    }
                    composable(
                        route = "vehicle/{vehicleid}",
                        arguments = listOf(navArgument("vehicleid") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getLong("vehicleid") ?: 0L
                        CarViewScreen(vehicleId = vehicleId)
                    }
                    composable(
                        route = "timeattacklistitem/{sessionid}",
                        arguments = listOf(navArgument("sessionid") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionid") ?: 0L
                        TimeAttackListItemScreen(
                            navController = navController,
                            database = database,
                            sessionId = sessionId
                        )
                    }
                    composable(route = "createvehicle") {
                        CarCreationScreen(database)
                    }
                    composable(route = "timeattack/{vehicleId}/{trackId}") { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull() ?: -1L
                        val trackId = backStackEntry.arguments?.getString("trackId")?.toLongOrNull() ?: -1L
                        TimeAttackScreenView(vehicleId = vehicleId, trackId = trackId)
                    }
                    composable(route = "trackandvehicle") {
                        TrackVehicleSelectorScreen(trackViewModel = trackViewModel, vehicleViewModel, navController)
                    }
                    composable(route = "timeattacklist") {
                        TimeAttackListViewScreen(
                            navController = navController,
                            viewModel = sessionViewModel,
                            vehicleViewModel = vehicleFULLViewModel,
                            trackViewModel = trackViewModel,
                            database = database
                        )
                    }
                }
            }
        }
    }
}

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
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = TrackProColors.BgCard,
                drawerContentColor = TrackProColors.TextPrimary
            ) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.AccentRed)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Column {
                        Text(
                            text = "TRACKPRO",
                            color = Color.Black,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text = "Performance Telemetry",
                            color = Color.Black.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                DrawerSection(title = "SESSIONS") {
                    DrawerItem(
                        icon = Icons.Default.RocketLaunch,
                        label = "Drag Sessions",
                        tint = TrackProColors.AccentRed,
                        onClick = { onNavigateToDragTimesList(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.FlagCircle,
                        label = "Track Sessions",
                        tint = TrackProColors.AccentGreen,
                        onClick = { onNavigateToTimeAttackListView(); scope.launch { drawerState.close() } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = TrackProColors.SectorLine
                )

                DrawerSection(title = "MANAGEMENT") {
                    DrawerItem(
                        icon = Icons.Default.Timelapse,
                        label = "My Tracks",
                        tint = TrackProColors.AccentAmber,
                        onClick = { onNavigateToTrackListScreen(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.CarRepair,
                        label = "My Vehicles",
                        tint = TrackProColors.AccentAmber,
                        onClick = { onNavigateToVehicleList(); scope.launch { drawerState.close() } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = TrackProColors.SectorLine
                )

                DrawerSection(title = "SYSTEM") {
                    DrawerItem(
                        icon = Icons.Default.Wifi,
                        label = "ESP Connection",
                        tint = TrackProColors.TextMuted,
                        onClick = { onNavigateToESPTestScreen(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        tint = TrackProColors.TextMuted,
                        onClick = { scope.launch { drawerState.close() } }
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProColors.BgDeep)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu",
                                tint = TrackProColors.TextPrimary)
                        }
                        Text(
                            text = "TRACKPRO",
                            color = TrackProColors.AccentRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                        // Spacer to balance the row
                        Box(modifier = Modifier.size(48.dp))
                    }
                }

                HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

                // ── Hero section ──────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard)
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Column {
                        // Red accent line
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(3.dp)
                                .background(TrackProColors.AccentRed)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "READY TO\nBEAT RECORDS?",
                            color = TrackProColors.TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 36.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "GPS telemetry · Lap timing · Performance analysis",
                            color = TrackProColors.TextMuted,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

                // ── Action grid ───────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // Primary racing actions — full width
                    ActionCard(
                        icon = Icons.Default.RocketLaunch,
                        title = "DRAG TIMING",
                        subtitle = "0–100 · ¼ mile · speed trace",
                        accentColor = TrackProColors.AccentRed,
                        onClick = onNavigateToDragRace,
                        fullWidth = true
                    )

                    ActionCard(
                        icon = Icons.Default.FlagCircle,
                        title = "LAP TIMING",
                        subtitle = "Circuit & sprint · live delta · best lap",
                        accentColor = TrackProColors.AccentGreen,
                        onClick = onNavigateToTrackVehicleSelector,
                        fullWidth = true
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = TrackProColors.SectorLine
                    )

                    // Secondary actions — 2 column grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ActionCard(
                                icon = Icons.Default.CarRepair,
                                title = "ADD VEHICLES",
                                subtitle = "Create your own vehicles",
                                accentColor = TrackProColors.AccentAmber,
                                onClick = onNavigateToVehicleCreatorScreen,
                                halfWidth  = true
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ActionCard(
                                icon = Icons.Default.Timelapse,
                                title = "TRACK\nBUILDER",
                                subtitle = "Define tracks",
                                accentColor = TrackProColors.AccentAmber,
                                onClick = onNavigateToTrackBuilder,
                                halfWidth  = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ActionCard(
                                icon = Icons.Default.Wifi,
                                title = "ESP\nCONNECT",
                                subtitle = "Test connection",
                                accentColor = TrackProColors.TextMuted,
                                onClick = onNavigateToESPTestScreen,
                                halfWidth  = true
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ActionCard(
                                icon = Icons.Default.Settings,
                                title = "SETTINGS",
                                subtitle = "Coming soon",
                                accentColor = TrackProColors.TextMuted,
                                onClick = { },
                                halfWidth  = true,
                                disabled = true
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Version tag
                    Text(
                        text = "TrackPro · GPS Telemetry System",
                        color = TrackProColors.TextMuted.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                }
            }
        }
    }
}

// ── Action card ────────────────────────────────────────────

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    fullWidth: Boolean = false,
    halfWidth: Boolean = false,  // new flag
    disabled: Boolean = false
) {
    val alpha = if (disabled) 0.4f else 1f
    val iconSize = if (halfWidth) 16.dp else if (fullWidth) 22.dp else 18.dp
    val iconBoxSize = if (halfWidth) 32.dp else if (fullWidth) 44.dp else 36.dp
    val titleSize = if (halfWidth) 11.sp else if (fullWidth) 14.sp else 12.sp
    val subtitleSize = if (halfWidth) 9.sp else 10.sp
    val vertPadding = if (halfWidth) 14.dp else if (fullWidth) 18.dp else 14.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProColors.BgCard, RoundedCornerShape(10.dp))
            .border(1.dp, TrackProColors.SectorLine, RoundedCornerShape(10.dp))
            .then(if (!disabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(48.dp)
                .background(
                    accentColor.copy(alpha = alpha),
                    RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = vertPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .background(accentColor.copy(alpha = 0.1f * alpha), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(iconSize)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TrackProColors.TextPrimary.copy(alpha = alpha),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,  // reduced from 1.sp
                    lineHeight = (titleSize.value + 2).sp,
                    softWrap = true
                )
                Text(
                    text = subtitle,
                    color = TrackProColors.TextMuted.copy(alpha = alpha),
                    fontSize = subtitleSize,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!disabled && !halfWidth) {
                Text(
                    text = "→",
                    color = accentColor.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ── Drawer helpers ─────────────────────────────────────────

@Composable
private fun DrawerSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        color = TrackProColors.TextMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp)
    )
    content()
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(label, color = TrackProColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
