package com.example.trackpro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.trackpro.DataClasses.VehicleInformationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CarScreen : ComponentActivity()
{

}


@Composable
fun CarViewScreen(onBack: () -> Unit, vehicleId: Int)
{

    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var vehicleInfo by remember { mutableStateOf<VehicleInformationData?>(null) }

    LaunchedEffect(vehicleId) {
        scope.launch(Dispatchers.IO)
        {
            database.vehicleInformationDAO().getVehicle(vehicleId).collect { vehicle ->
                vehicleInfo = vehicle
            }
        }
    }


}

/*
@Preview
fun PreviewCarViewScreen()
{
    CarViewScreen()

}
*/