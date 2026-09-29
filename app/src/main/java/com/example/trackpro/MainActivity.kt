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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import com.example.trackpro.screens.profile.ProfileScreen
import com.example.trackpro.screens.profile.EditProfileScreen
import com.example.trackpro.screens.profile.AccountScreen
import com.example.trackpro.screens.history.HistorySection
import com.example.trackpro.screens.drive.DriveScreen
import com.example.trackpro.screens.history.HistoryScreen
import com.example.trackpro.screens.garage.GarageScreen
import com.example.trackpro.dao.VehicleUsage
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.DashTabBar
import com.example.trackpro.components.DashTab
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.example.trackpro.managerClasses.gpsDataManagers.ESPTcpClient
import com.example.trackpro.managerClasses.gpsDataManagers.BluetoothClassicClient
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.online.OnlineServices
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.online.ui.LeaderboardScreen
import com.example.trackpro.managerClasses.gpsDataManagers.GpsManager
import com.example.trackpro.managerClasses.gpsDataManagers.PhoneGpsProvider
import com.example.trackpro.models.GpsProviderType
import com.example.trackpro.managerClasses.timeAttackManagers.DeltaReference
import com.example.trackpro.screens.vehicleScreens.CarCreationScreen
import com.example.trackpro.screens.telemetricScreens.DragRaceScreen
import com.example.trackpro.screens.ESPConnectionTestScreen
import com.example.trackpro.screens.SettingsScreen
import com.example.trackpro.screens.telemetricScreens.TimeAttackScreenView
import com.example.trackpro.screens.TrackBuilderScreen
import com.example.trackpro.screens.TrackScreen
import com.example.trackpro.screens.TrackVehicleSelectorScreen
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
    val sessionManager: SessionManager by lazy {
        SessionManager.getInstance(
            database,
            currentGpsSource = { gpsSource.value.name },
            // A finished session is offered to TrackBoard as soon as there is a network.
            onSessionEnded = { if (online.auth.isSignedIn) SyncScheduler.syncSoon(this) }
        )
    }

    /** TrackBoard: account, leaderboard sharing and sync. Entirely optional. */
    val online: OnlineServices by lazy { OnlineServices(this, database) }
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

    // What the live delta is measured against. Remembered, and switchable from the HUD as
    // well as Settings - reaching Settings from a running session means leaving it.
    private val deltaPrefs by lazy { getSharedPreferences("delta_prefs", MODE_PRIVATE) }
    val deltaReference by lazy {
        val stored = deltaPrefs.getString("reference", null)
        val initial = runCatching { DeltaReference.valueOf(stored ?: "") }
            .getOrDefault(DeltaReference.SESSION_BEST)
        MutableStateFlow(initial)
    }

    fun setDeltaReference(reference: DeltaReference) {
        deltaPrefs.edit().putString("reference", reference.name).apply()
        deltaReference.value = reference
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
            // No default car any more. The seeder re-added a Lexus IS200 on every launch, so a
            // driver could never delete it, and with the garage backed up to an account it
            // would have been uploaded as theirs. An empty garage now opens on Add car.

            // Off the main thread: reading the stored sign-in touches the Keystore.
            if (online.auth.isSignedIn) {
                SyncScheduler.schedulePeriodic(this@TrackProApp)
                SyncScheduler.syncSoon(this@TrackProApp)
            }
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
        if (fineGranted || coarseGranted) {
            // The app starts its GPS provider in Application.onCreate, before this dialog is
            // answered, so a phone-GPS provider that gave up for want of permission has to
            // be told that it now has one.
            (application as TrackProApp).gpsManager.retryPhoneGpsAfterPermission()
        } else {
            // User denied — phone GPS won't work, ESP32 still will
            Log.w("Permissions", "Location permission denied — phone GPS unavailable")
        }
    }

    /**
     * Covers the permission being granted where no result callback fires: the user turning
     * it on in system settings, or Android restoring it after an auto-revoke. A no-op unless
     * the phone GPS is the selected source and is currently stopped.
     */
    override fun onResume() {
        super.onResume()
        (application as TrackProApp).gpsManager.retryPhoneGpsAfterPermission()
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
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                // The bar belongs to the four tab roots only. Every screen reached from a tab
                // is a step down with its own back, and the HUDs must never carry it.
                val selectedTab = MainTabs.firstOrNull { tab -> currentRoute?.substringBefore('?') == tab.route }

                fun openTab(route: String) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                Column(modifier = Modifier.fillMaxSize().background(TrackProTheme.colors.panel)) {
                NavHost(
                    navController = navController,
                    startDestination = "main",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("main") {
                        DriveScreen(
                            onStartTrack = { navController.navigate("trackandvehicle") },
                            onStartDrag = { navController.navigate("drag") },
                            onOpenRig = { navController.navigate("esptest") },
                            onOpenHistory = { section -> openTab("history?section=$section") },
                            onOpenCar = { id -> navController.navigate("vehicle/$id") },
                            onAddCar = { navController.navigate("createvehicle") }
                        )
                    }
                    composable(
                        "history?section={section}",
                        arguments = listOf(navArgument("section") { type = NavType.StringType; defaultValue = "track" })
                    ) { entry ->
                        HistoryScreen(
                            navController = navController,
                            sessionViewModel = sessionViewModel,
                            dragSessionViewModel = dragSessionViewModel,
                            trackViewModel = trackViewModel,
                            vehicleViewModel = vehicleFULLViewModel,
                            initial = if (entry.arguments?.getString("section") == "drag") HistorySection.Drag else HistorySection.Track
                        )
                    }
                    composable("garage") {
                        GarageScreen(
                            navController = navController,
                            vehicleViewModel = vehicleFULLViewModel,
                            trackViewModel = trackViewModel
                        )
                    }
                    composable("profile") {
                        ProfileScreen(navController = navController)
                    }
                    composable("account") {
                        AccountScreen(navController = navController)
                    }
                    composable("profile/edit") {
                        EditProfileScreen(navController = navController)
                    }
                    composable("drag") {
                        DragRaceScreen(vehicleFULLViewModel, onBack = { navController.popBackStack() })
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
                        TrackScreen(
                            trackId = trackId,
                            onBack = { navController.popBackStack() },
                            onOpenLeaderboard = { navController.navigate("leaderboard/$trackId") }
                        )
                    }
                    composable(
                        "leaderboard/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        LeaderboardScreen(
                            trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L,
                            onBack = { navController.popBackStack() }
                        )
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
                    composable(
                        route = "vehicle/{vehicleid}",
                        arguments = listOf(navArgument("vehicleid") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getLong("vehicleid") ?: 0L
                        CarViewScreen(
                            vehicleId = vehicleId,
                            onBack = { navController.popBackStack() },
                            onSignIn = { navController.navigate("account") },
                            onOpenTrack = { navController.navigate("track/$it") }
                        )
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
                    composable(route = "settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onRequestBluetoothPermission = { requestBluetoothPermissionIfNeeded() }
                        )
                    }
                    composable("lap_detail/{sessionId}/{lapId}") { backStackEntry ->
                        LapDetailScreen(
                            navController = navController,
                            database      = database,
                            sessionId     = backStackEntry.arguments?.getString("sessionId")?.toLong() ?: -1L,
                            primaryLapId  = backStackEntry.arguments?.getString("lapId")?.toLong()     ?: -1L
                        )
                    }
                }
                if (selectedTab != null) {
                    DashTabBar(
                        tabs = MainTabs,
                        selectedRoute = selectedTab.route,
                        onSelect = { tab -> if (tab != selectedTab) openTab(tab.route) }
                    )
                }
                }
            }
        }
    }
}

/** The four places the app has. Everything else is reached from one of them. */
private val MainTabs = listOf(
    DashTab("main", "Drive", Icons.Default.Speed),
    DashTab("history", "History", Icons.Default.History),
    DashTab("garage", "Garage", Icons.Default.DirectionsCar),
    DashTab("profile", "Profile", Icons.Default.Person),
)
