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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ui.theme.TrackProTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.ui.screens.DragTimesListView
import kotlinx.coroutines.launch



class TrackProApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = ESPDatabase.getInstance(applicationContext)

        setContent {
            TrackProTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onNavigateToGraph = { navController.navigate("graph") },
                            onNavigateToDragRace = {navController.navigate("drag")},
                            onNavigateToESPTestScreen = {navController.navigate("esptest")},
                            onNavigateToTrackScreen = {navController.navigate("track")},
                            onNavigateToDragTimesList = {navController.navigate("dragsessions")}
                        )
                    }
                    composable("graph") {
                        GraphScreen(onBack = { navController.popBackStack() })
                    }
                    composable("drag")
                    {
                        DragRaceScreen(
                            database = database,
                            onBack = {navController.popBackStack()}
                        )
                    }
                    composable("esptest")
                    {
                        ESPConnectionTestScreen()
                    }
                    composable("track")
                    {
                        TrackScreen()
                    }
                    composable("dragsessions")
                    {
                        DragTimesListView()
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen( onNavigateToGraph: () -> Unit,onNavigateToDragRace: () -> Unit,onNavigateToESPTestScreen:() -> Unit, onNavigateToTrackScreen: () -> Unit, onNavigateToDragTimesList:() -> Unit ) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()


    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text("TrackPro", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)

                    Text("", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    NavigationDrawerItem(
                        label = { Text("My drag sessions") },
                        selected = false,
                        onClick = {
                            onNavigateToDragTimesList()
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Shared sessions") },
                        selected = false,
                        onClick = { /* Handle click */ }
                    )
                    NavigationDrawerItem(
                        label = { Text("Top racers") },
                        selected = false,
                        onClick = { /* Handle click */ }
                    )


                    Text("Section 2", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // Use innerPadding here
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to TRACKPRO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "Ready to beat some records?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onNavigateToGraph,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = "Graph Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Graph")
                }

                Button(
                    onClick = onNavigateToDragRace,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = "Drag Race Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Drag Screen")
                }

                Button(
                    onClick = onNavigateToESPTestScreen,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = "ESP Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ESP Connection")
                }

                Button(
                    onClick = onNavigateToTrackScreen,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Timelapse, contentDescription = "Track Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lap timer / track builder")
                }
            }
        }
    }
}




@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {

    TrackProTheme {
        MainScreen(
            onNavigateToGraph = {},
            onNavigateToDragRace = {},
            onNavigateToESPTestScreen = {},
            onNavigateToTrackScreen = {},
            onNavigateToDragTimesList = {}
        )
    }
}
