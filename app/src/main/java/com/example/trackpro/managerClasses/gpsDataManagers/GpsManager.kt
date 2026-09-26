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

    /**
     * The active provider's last link failure, or null when connected or when the source
     * is the phone GPS (which is not a stream link and fails differently).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lastFailure = gpsSource.flatMapLatest { source ->
        (providerFor(source) as? LineStreamGpsProvider)?.lastFailure
            ?: MutableStateFlow<String?>(null)
    }

    /** Restarts the active link so the user can retry without leaving the screen. */
    fun retryActiveProvider() {
        val active = providerFor(gpsSource.value)
        active.stop()
        active.start()
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
        // connected — covers a fresh connection, switching source onto an
        // already-live provider, and every automatic reconnect after a dropped
        // link (the ESP32 may have rebooted and come back at its default rate). distinctUntilChanged() MUST precede
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

    /**
     * Starts the phone GPS if it is the selected source and is not already running.
     *
     * PhoneGpsProvider.start() gives up quietly when the location permission is missing, and
     * nothing used to try again: the app starts its provider before the permission dialog is
     * answered, and the collector above only reacts when the source *changes*. So the first
     * grant - or a re-grant after Android auto-revokes the permission for an unused app -
     * left the app reading NO SIGNAL until the source was toggled or the app restarted.
     *
     * Safe to call speculatively: it does nothing unless the phone GPS is both selected and
     * stopped, and start() re-checks the permission itself. Only the phone provider is
     * covered, because it is the only one that can fail this way - the others are not
     * permission-gated at start, and restarting a live TCP or Bluetooth link on a guess
     * would be worse than leaving it alone.
     */
    fun retryPhoneGpsAfterPermission() {
        if (gpsSource.value != GpsProviderType.PHONE_GPS) return
        if (phoneProvider.connectionStatus.value) return
        phoneProvider.start()
    }

    fun stopActiveProvider() {
        allProviders.forEach { it.stop() }
    }

    fun cancel() {
        scope.cancel()
    }
}
