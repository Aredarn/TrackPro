package com.example.trackpro

import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.Readout
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.theme.segmentOff
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.timed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.TrackSeeder
import com.example.trackpro.managerClasses.VehicleSeeder
import com.example.trackpro.managerClasses.gpsDataManagers.ESPTcpClient
import com.example.trackpro.managerClasses.gpsDataManagers.BluetoothClassicClient
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.managerClasses.gpsDataManagers.GpsManager
import com.example.trackpro.managerClasses.gpsDataManagers.PhoneGpsProvider
import com.example.trackpro.models.GpsProviderType
import com.example.trackpro.screens.vehicleScreens.CarCreationScreen
import com.example.trackpro.screens.telemetricScreens.DragRaceScreen
import com.example.trackpro.screens.ESPConnectionTestScreen
import com.example.trackpro.screens.SettingsScreen
import com.example.trackpro.screens.telemetricScreens.TimeAttackScreenView
import com.example.trackpro.screens.TrackBuilderScreen
import com.example.trackpro.screens.TrackScreen
import com.example.trackpro.screens.TrackVehicleSelectorScreen
import com.example.trackpro.screens.listViewScreens.CarListScreen
import com.example.trackpro.screens.listViewScreens.DragTimesListView
import com.example.trackpro.screens.listViewScreens.TimeAttackListViewScreen
import com.example.trackpro.screens.listViewScreens.TrackListScreen
import com.example.trackpro.screens.listViewScreens.lapDetail.LapDetailScreen
import com.example.trackpro.screens.listViewScreens.listItems.CarViewScreen
import com.example.trackpro.screens.listViewScreens.listItems.GraphScreen
import com.example.trackpro.screens.listViewScreens.listItems.TimeAttackListItemScreen
import com.example.trackpro.viewModels.DragSessionViewModel
import com.example.trackpro.viewModels.DragSessionViewModelFactory
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.SessionViewModelFactory
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.TrackViewModelFactory
import com.example.trackpro.viewModels.VehicleFULLViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModelFactory
import com.example.trackpro.viewModels.VehicleViewModel
import com.example.trackpro.viewModels.VehicleViewModelFactory
import com.example.trackpro.components.pressableRow
import com.example.trackpro.components.pressable
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class TrackProApp : Application() {

    val database: ESPDatabase by lazy { ESPDatabase.getInstance(this) }
    val sessionManager: SessionManager by lazy { SessionManager.getInstance(database) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val espTcpClient: ESPTcpClient by lazy {
        val config = JsonReader.loadConfig(this)
        ESPTcpClient(serverAddress = config.first, port = config.second)
    }

    // Lets Settings redirect the WiFi connection to a test simulator (e.g.
    // esp32_simulator.py on a dev machine) instead of the real ESP32's fixed
    // AP address, without editing config.json and rebuilding. Port always
    // comes from config.json (the simulator listens on the same 4210 the
    // firmware does) - only the host is swappable.
    private val espTargetPrefs by lazy { getSharedPreferences("esp_target_prefs", MODE_PRIVATE) }
    val useTestServer by lazy { MutableStateFlow(espTargetPrefs.getBoolean("use_test_server", false)) }
    val testServerAddress by lazy { MutableStateFlow(espTargetPrefs.getString("test_server_address", "") ?: "") }

    fun setUseTestServer(enabled: Boolean) {
        espTargetPrefs.edit().putBoolean("use_test_server", enabled).apply()
        useTestServer.value = enabled
        applyEspTarget()
    }

    fun setTestServerAddress(address: String) {
        espTargetPrefs.edit().putString("test_server_address", address).apply()
        testServerAddress.value = address
        if (useTestServer.value) applyEspTarget()
    }

    private fun applyEspTarget() {
        val (realIp, port) = JsonReader.loadConfig(this)
        val target = if (useTestServer.value && testServerAddress.value.isNotBlank()) {
            testServerAddress.value
        } else {
            realIp
        }
        espTcpClient.updateTarget(target, port)
    }

    val bluetoothClassicClient: BluetoothClassicClient by lazy {
        BluetoothClassicClient(this)
    }

    val phoneGpsProvider: PhoneGpsProvider by lazy {
        PhoneGpsProvider(this)
    }

    // Persisted like useDarkTheme/useMetricUnits below (unlike the old useExternalGps,
    // which reset to WiFi every launch) — avoids surprising the user mid-track-day.
    private val gpsSourcePrefs by lazy { getSharedPreferences("gps_source_prefs", MODE_PRIVATE) }
    val gpsSource by lazy {
        val stored = gpsSourcePrefs.getString("source", GpsProviderType.WIFI.name)
        val initial = runCatching { GpsProviderType.valueOf(stored ?: GpsProviderType.WIFI.name) }
            .getOrDefault(GpsProviderType.WIFI)
        MutableStateFlow(initial)
    }

    fun setGpsSource(source: GpsProviderType) {
        gpsSourcePrefs.edit().putString("source", source.name).apply()
        gpsSource.value = source
    }

    private val ratePrefs by lazy { getSharedPreferences("gps_rate_prefs", MODE_PRIVATE) }
    val selectedRateHz by lazy { MutableStateFlow(ratePrefs.getInt("rate_hz", 10)) }

    fun setRateHz(hz: Int) {
        ratePrefs.edit().putInt("rate_hz", hz).apply()
        selectedRateHz.value = hz
        gpsManager.sendCommandToActive("RATE:$hz\n")
    }

    private val btDevicePrefs by lazy { getSharedPreferences("bluetooth_prefs", MODE_PRIVATE) }
    val selectedBtDeviceMac by lazy { MutableStateFlow(btDevicePrefs.getString("device_mac", null)) }

    fun setSelectedBtDevice(mac: String) {
        btDevicePrefs.edit().putString("device_mac", mac).apply()
        selectedBtDeviceMac.value = mac
        // Bluetooth is usually already the active source by the time a device is
        // picked (the picker only shows once it is) - that first connect attempt
        // already failed with no MAC set, and nothing else would ever retry it.
        if (gpsSource.value == GpsProviderType.BLUETOOTH) {
            bluetoothClassicClient.stop()
            bluetoothClassicClient.start()
        }
    }

    private val themePrefs by lazy { getSharedPreferences("theme_prefs", MODE_PRIVATE) }
    // Defaults to the printed sheet rather than the illuminated board. The product's
    // primary scene is a phone mounted in direct sunlight, where paper outreads a lit
    // panel; the board is what live recording switches to. Anyone who has already set a
    // preference keeps it - this default only applies to a fresh install.
    val useDarkTheme by lazy { MutableStateFlow(themePrefs.getBoolean("dark_theme", false)) }

    fun setDarkTheme(enabled: Boolean) {
        themePrefs.edit().putBoolean("dark_theme", enabled).apply()
        useDarkTheme.value = enabled
    }

    private val unitPrefs by lazy { getSharedPreferences("unit_prefs", MODE_PRIVATE) }
    val useMetricUnits by lazy { MutableStateFlow(unitPrefs.getBoolean("metric_units", true)) }

    fun setMetricUnits(enabled: Boolean) {
        unitPrefs.edit().putBoolean("metric_units", enabled).apply()
        useMetricUnits.value = enabled
    }

    val gpsManager: GpsManager by lazy {
        GpsManager(
            wifiProvider = espTcpClient,
            bluetoothProvider = bluetoothClassicClient,
            phoneProvider = phoneGpsProvider,
            gpsSource = gpsSource,
            selectedRateHz = selectedRateHz
        )
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Apply a persisted test-server redirect (if any) before the first
        // connection attempt, so a restart doesn't briefly dial the real ESP32
        // before switching over.
        applyEspTarget()
        // Start the active provider immediately at app launch
        gpsManager.startActiveProvider()

        // Sync bundled premade tracks on every launch (not just first install) so existing
        // users pick up newly-added ones too; name-deduped, so this is always safe to re-run.
        applicationScope.launch(Dispatchers.IO) {
            TrackSeeder.syncPremadeTracks(this@TrackProApp, database)
            VehicleSeeder.syncDefaultVehicles(database)
        }
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

    // Requested contextually (only when the user opens the Bluetooth device
    // picker in Settings), unlike the eager location request above — Bluetooth
    // is opt-in/rare, location is core to the app on every launch.
    private val bluetoothPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] != true) {
            Log.w("Permissions", "Bluetooth permission denied — Bluetooth GPS source unavailable")
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
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
        val context = applicationContext


        val vehicleViewModel = VehicleViewModelFactory(database).create(VehicleViewModel::class.java)
        val trackViewModel = TrackViewModelFactory(database).create(TrackViewModel::class.java)

        val vehicleFULLViewModel = VehicleFULLViewModelFactory(context).create(VehicleFULLViewModel::class.java)
        val sessionViewModel = SessionViewModelFactory(context).create(SessionViewModel::class.java)
        val dragSessionViewModel = DragSessionViewModelFactory(context).create(DragSessionViewModel::class.java)


        setContent {
            val useDarkTheme by (application as TrackProApp).useDarkTheme.collectAsState()
            TrackProTheme(darkTheme = useDarkTheme) {
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
                            onNavigateToTimeAttackListView = { navController.navigate("timeattacklist") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("drag") {
                        DragRaceScreen(database, sessionManager, vehicleFULLViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("esptest") {
                        ESPConnectionTestScreen(
                            onNavigateToSettings = { navController.navigate("settings") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "track/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
                        TrackScreen(trackId = trackId, onBack = { navController.popBackStack() })
                    }
                    composable("dragsessions") {
                        DragTimesListView(viewModel = dragSessionViewModel, navController = navController)
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
                        CarViewScreen(vehicleId = vehicleId, onBack = { navController.popBackStack() })
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
                        CarCreationScreen(database, onBack = { navController.popBackStack() })
                    }
                    composable(route = "timeattack/{vehicleId}/{trackId}") { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull() ?: -1L
                        val trackId = backStackEntry.arguments?.getString("trackId")?.toLongOrNull() ?: -1L
                        TimeAttackScreenView(vehicleId = vehicleId, trackId = trackId, onBack = { navController.popBackStack() })
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
                        )
                    }
                    composable(route = "settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onRequestBluetoothPermission = { requestBluetoothPermissionIfNeeded() }
                        )
                    }
                    // In your NavHost setup
                    composable("lap_detail/{sessionId}/{lapId}") { backStackEntry ->
                        LapDetailScreen(
                            navController = navController,
                            database      = database,
                            sessionId     = backStackEntry.arguments?.getString("sessionId")?.toLong() ?: -1L,
                            primaryLapId  = backStackEntry.arguments?.getString("lapId")?.toLong()     ?: -1L
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
    onNavigateToTimeAttackListView: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = TrackProTheme.colors.bgCard,
                drawerContentColor = TrackProTheme.colors.textPrimary
            ) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgElevated)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(TrackProTheme.colors.accent, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TRACKPRO",
                                style = TrackProType.titleLarge,
                                color = TrackProTheme.colors.textPrimary
                            )
                            Text(
                                text = "Performance Telemetry",
                                style = TrackProType.body.atSize(11.sp),
                                color = TrackProTheme.colors.textFaint
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                DrawerSection(title = "SESSIONS") {
                    DrawerItem(
                        icon = Icons.Default.RocketLaunch,
                        label = "Drag Sessions",
                        tint = TrackProTheme.colors.accentMuted,
                        onClick = { onNavigateToDragTimesList(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.FlagCircle,
                        label = "Track Sessions",
                        tint = TrackProTheme.colors.accentMuted,
                        onClick = { onNavigateToTimeAttackListView(); scope.launch { drawerState.close() } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = TrackProTheme.colors.sectorLine
                )

                DrawerSection(title = "MANAGEMENT") {
                    DrawerItem(
                        icon = Icons.Default.Timelapse,
                        label = "My Tracks",
                        tint = TrackProTheme.colors.accentMuted,
                        onClick = { onNavigateToTrackListScreen(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.CarRepair,
                        label = "My Vehicles",
                        tint = TrackProTheme.colors.accentMuted,
                        onClick = { onNavigateToVehicleList(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.AddCircle,
                        label = "Add Vehicle",
                        tint = TrackProTheme.colors.accentMuted,
                        onClick = { onNavigateToVehicleCreatorScreen(); scope.launch { drawerState.close() } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = TrackProTheme.colors.sectorLine
                )

                DrawerSection(title = "SYSTEM") {
                    DrawerItem(
                        icon = Icons.Default.Wifi,
                        label = "ESP Connection",
                        tint = TrackProTheme.colors.textMuted,
                        onClick = { onNavigateToESPTestScreen(); scope.launch { drawerState.close() } }
                    )
                    DrawerItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        tint = TrackProTheme.colors.textMuted,
                        onClick = { onNavigateToSettings();scope.launch { drawerState.close() } }
                    )
                }
            }
        }
    ) {
        DashBoard(
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNavigateToDragRace = onNavigateToDragRace,
            onNavigateToTrackVehicleSelector = onNavigateToTrackVehicleSelector,
            onNavigateToDragTimesList = onNavigateToDragTimesList,
            onNavigateToTimeAttackListView = onNavigateToTimeAttackListView,
            onNavigateToVehicleList = onNavigateToVehicleList,
            onNavigateToTrackListScreen = onNavigateToTrackListScreen,
            onNavigateToESPTestScreen = onNavigateToESPTestScreen,
            onNavigateToTrackBuilder = onNavigateToTrackBuilder,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

// ── Action card ────────────────────────────────────────────

// ── Status board ────────────────────────────────────

/**
 * The dash at rest.
 *
 * This screen is read parked, never at speed, so it carries no at-speed legibility tax -
 * that constraint belongs to the two HUDs alone. What it owes instead is that you learn
 * something every time you open it: what the rig is doing right now, and what the last
 * session did.
 *
 * Only two things earned a place here, and both are live from the database. There is no
 * lifetime-totals wall and no per-track record table; they were considered and cut,
 * because a number you never act on is decoration with a value in it.
 *
 * A fresh install renders the identical panel with dashes on every face. That is the
 * whole empty state - no separate onboarding screen to design, maintain, or drift.
 */
@Composable
private fun DashBoard(
    onOpenDrawer: () -> Unit,
    onNavigateToDragRace: () -> Unit,
    onNavigateToTrackVehicleSelector: () -> Unit,
    onNavigateToDragTimesList: () -> Unit,
    onNavigateToTimeAttackListView: () -> Unit,
    onNavigateToVehicleList: () -> Unit,
    onNavigateToTrackListScreen: () -> Unit,
    onNavigateToESPTestScreen: () -> Unit,
    onNavigateToTrackBuilder: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp

    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val vehicles by app.database.vehicleInformationDAO().getAllVehicles().collectAsState(initial = emptyList())
    val sessions by app.database.sessionDataDao().getAllSessions().collectAsState(initial = emptyList())

    // The most recent session and its best lap. Resolved off the main thread; until it
    // lands every face shows a dash, which is also the honest empty state.
    var lastSession by remember { mutableStateOf<SessionData?>(null) }
    var lastBestLap by remember { mutableStateOf<LapTimeData?>(null) }
    var lastLapCount by remember { mutableStateOf(0) }
    var lastTrackName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessions) {
        val recent = sessions.maxByOrNull { it.startTime }
        lastSession = recent
        if (recent == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            // timed() rather than filtering on the IN PROGRESS string: that filter missed
            // INVALID laps, which then parsed as 00:00.00 and showed up here as the best lap.
            val done = app.database.lapTimeDataDAO().getLapsForSession(recent.id).timed()
            lastLapCount = done.size
            lastBestLap = done.minByOrNull { it.millis }?.lap
            lastTrackName = recent.trackId?.let { id ->
                runCatching { app.database.trackMainDao().getTrack(id).first().trackName }.getOrNull()
            }
        }
    }

    val vehicle: VehicleInformationData? = vehicles.firstOrNull()
    val dash = "\u2014"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Link bar ───────────────────────────────────
        // The rig's state as the panel's top edge, the way a dash puts its shift lights
        // there: a strip you catch without looking at it directly.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = TrackProTheme.colors.marking,
                    modifier = Modifier.size(20.dp)
                )
            }
            SegmentBar(
                signedFraction = if (isConnected) 1f else 0f,
                activeColor = if (isConnected) TrackProTheme.colors.deltaGood
                else TrackProTheme.colors.segmentOff,
                bidirectional = false,
                segments = 16,
                height = 10.dp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isConnected) "LINKED" else "NO LINK",
                style = TrackProType.label,
                color = if (isConnected) TrackProTheme.colors.deltaGood
                else TrackProTheme.colors.markingDim
            )
        }

        Bezel()

        // ── Mode entry ─────────────────────────────────
        // The two things this app exists to do, first on the panel and the only elements
        // carrying a lit ground and an accent hairline. Everything below is a readout.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.panel)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashAction(
                label = "Track",
                detail = "Circuit · sprint · live delta",
                onClick = onNavigateToTrackVehicleSelector
            )
            DashAction(
                label = "Drag",
                detail = "0–100 · quarter mile · splits",
                onClick = onNavigateToDragRace
            )
        }

        Bezel()

        // ── Car placard ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressableRow(onClick = onNavigateToVehicleList)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (vehicle != null) "${vehicle.manufacturer} ${vehicle.model}".uppercase()
                    else "NO VEHICLE",
                    style = TrackProType.titleLarge.atSize(20.sp),
                    color = TrackProTheme.colors.marking
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (vehicle != null) listOfNotNull(
                        vehicle.year.takeIf { it > 0 }?.toString(),
                        vehicle.horsepower.takeIf { it > 0 }?.let { "$it HP" },
                        vehicle.drivetrain.takeIf { it.isNotBlank() },
                        vehicle.weight.takeIf { it > 0.0 }?.let { "${it.toInt()} KG" }
                    ).joinToString("  ·  ") else "Add one to record a session",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
        }

        Bezel()

        // ── Last session ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.field)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Readout(
                value = lastBestLap?.laptime ?: dash,
                caption = "Best lap · last session",
                valueColor = if (lastBestLap != null) TrackProTheme.colors.accent
                else TrackProTheme.colors.markingDim,
                valueSize = 46.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (lastTrackName ?: lastSession?.eventType ?: "No sessions recorded").uppercase(),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (lastSession != null) "$lastLapCount LAPS" else dash,
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
        }

        Bezel()

        // ── Secondary ──────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            Instrument(
                label = "Track records",
                value = "${sessions.count { it.trackId != null }}",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToTimeAttackListView)
            )
            Bezel(vertical = true, modifier = Modifier.height(58.dp))
            Instrument(
                label = "Drag records",
                value = "${sessions.count { it.trackId == null }}",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToDragTimesList)
            )
        }
        Bezel()
        Row(modifier = Modifier.fillMaxWidth()) {
            Instrument(
                label = "Tracks",
                value = "›",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToTrackListScreen)
            )
            Bezel(vertical = true, modifier = Modifier.height(58.dp))
            Instrument(
                label = "Builder",
                value = "›",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToTrackBuilder)
            )
            Bezel(vertical = true, modifier = Modifier.height(58.dp))
            Instrument(
                label = "Rig",
                value = "›",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToESPTestScreen)
            )
            Bezel(vertical = true, modifier = Modifier.height(58.dp))
            Instrument(
                label = "Setup",
                value = "›",
                modifier = Modifier.weight(1f).pressableRow(onClick = onNavigateToSettings)
            )
        }
        Bezel()
        Spacer(Modifier.height(20.dp))
    }
}





// ── Drawer helpers ─────────────────────────────────────────

@Composable
private fun DrawerSection(title: String, content: @Composable () -> Unit) {
    SectionLabel(
        text = title,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
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
            .pressableRow(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(tint.copy(alpha = 0.12f), TrackProShapes.control),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        }
        Text(label, style = TrackProType.body, color = TrackProTheme.colors.textPrimary)
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
            onNavigateToTimeAttackListView = {},
            onNavigateToSettings = {}
        )
    }
}
