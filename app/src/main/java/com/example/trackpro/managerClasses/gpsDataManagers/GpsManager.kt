package com.example.trackpro.managerClasses.gpsDataManagers

import com.example.trackpro.models.CommandableGpsProvider
import com.example.trackpro.models.GpsProvider
import com.example.trackpro.models.GpsProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class GpsManager(
    private val wifiProvider: ESPTcpClient,
    private val bluetoothProvider: BluetoothClassicClient,
    private val phoneProvider: PhoneGpsProvider,
    private val gpsSource: StateFlow<GpsProviderType>,
    private val selectedRateHz: StateFlow<Int>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun providerFor(source: GpsProviderType): GpsProvider = when (source) {
        GpsProviderType.WIFI -> wifiProvider
        GpsProviderType.BLUETOOTH -> bluetoothProvider
        GpsProviderType.PHONE_GPS -> phoneProvider
    }

    private val allProviders: List<GpsProvider> = listOf(wifiProvider, bluetoothProvider, phoneProvider)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGpsFlow = gpsSource.flatMapLatest { providerFor(it).gpsFlow }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStatus = gpsSource.flatMapLatest { providerFor(it).connectionStatus }

    // ESP32-confirmed rate for the active provider; null for phone GPS (not
    // commandable) or before any RATE_OK reply has been seen yet.
    @OptIn(ExperimentalCoroutinesApi::class)
    val confirmedRateHz = gpsSource.flatMapLatest { source ->
        (providerFor(source) as? CommandableGpsProvider)?.confirmedRateHz
            ?: MutableStateFlow<Int?>(null)
    }

    fun sendCommandToActive(cmd: String) {
        (providerFor(gpsSource.value) as? CommandableGpsProvider)?.sendCommand(cmd)
    }

    init {
        // React to source changes — stop everything else, start the selected one
        scope.launch {
            gpsSource.collect { selected ->
                val active = providerFor(selected)
                allProviders.filter { it !== active }.forEach { it.stop() }
                active.start()
            }
        }

        // Re-assert the desired rate whenever the active provider becomes
        // connected — covers both a fresh connection and switching source onto
        // an already-live provider. distinctUntilChanged() MUST precede
        // filter{it}: when switching sources the flattened sequence is
        // true(old)->false(new starting)->true(new connected); filtering first
        // would hide the intervening false, so distinctUntilChanged would then
        // see true,true back-to-back and swallow the second reconnect's re-send.
        scope.launch {
            connectionStatus
                .distinctUntilChanged()
                .filter { it }
                .collect { sendCommandToActive("RATE:${selectedRateHz.value}\n") }
        }
    }

    fun startActiveProvider() {
        providerFor(gpsSource.value).start()
    }

    fun stopActiveProvider() {
        allProviders.forEach { it.stop() }
    }

    fun cancel() {
        scope.cancel()
    }
}
