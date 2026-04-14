package com.example.trackpro.managerClasses.gpsDataManagers

import com.example.trackpro.managerClasses.gpsDataManagers.ESPTcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class GpsManager(
    private val espProvider: ESPTcpClient,
    private val phoneProvider: PhoneGpsProvider,
    private val useExternalGps: StateFlow<Boolean>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGpsFlow = useExternalGps.flatMapLatest { isExternal ->
        if (isExternal) espProvider.gpsFlow else phoneProvider.gpsFlow
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStatus = useExternalGps.flatMapLatest { isExternal ->
        if (isExternal) espProvider.connectionStatus else phoneProvider.connectionStatus
    }

    init {
        // React to toggle changes — stop old, start new
        scope.launch {
            useExternalGps.collect { isExternal ->
                if (isExternal) {
                    phoneProvider.stop()
                    espProvider.start()
                } else {
                    espProvider.stop()
                    phoneProvider.start()
                }
            }
        }
    }

    fun startActiveProvider() {
        if (useExternalGps.value) espProvider.start() else phoneProvider.start()
    }

    fun stopActiveProvider() {
        espProvider.stop()
        phoneProvider.stop()
    }

    fun cancel() {
        scope.cancel()
    }
}