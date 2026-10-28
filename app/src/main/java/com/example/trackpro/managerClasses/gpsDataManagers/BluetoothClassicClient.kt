package com.example.trackpro.managerClasses.gpsDataManagers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// Bluetooth Classic (SPP) transport - an alternative to ESPTcpClient's WiFi TCP
// socket. Requires the ESP32 to already be paired via Android's own Bluetooth
// settings; this class only connects to an already-bonded device (no discovery
// UI), whose MAC address is read from SharedPreferences at connect time.
//
// Only knows how to open that socket. Reading, parsing, commands and staying
// connected are LineStreamGpsProvider's.
class BluetoothClassicClient(private val context: Context) :
    LineStreamGpsProvider(tag = "BluetoothClassicClient") {

    // BLUETOOTH_CONNECT is only a real runtime permission from API 31 onward;
    // below that, the manifest-declared BLUETOOTH/BLUETOOTH_ADMIN permissions
    // are enough and there's nothing to check at runtime.
    fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    // Everything is looked up afresh on each attempt - permission, the selected MAC, the
    // adapter - so a retry picks up a device chosen, or a permission granted, since the
    // last one failed.
    @SuppressLint("MissingPermission")
    override suspend fun open(): Link {
        if (!hasBluetoothPermission()) throw IOException("Bluetooth permission not granted")

        val mac = context.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE)
            .getString("device_mac", null)
            ?: throw IOException("No Bluetooth device selected")

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw IOException("Bluetooth not supported on this device")
        val device = adapter.getRemoteDevice(mac)

        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        connectSocketWithTimeout(socket, CONNECT_TIMEOUT_MS)

        return object : Link {
            override val input: InputStream = socket.inputStream
            override val output: OutputStream = socket.outputStream
            override fun close() = socket.close()
        }
    }

    // BluetoothSocket.connect() is a blocking call with no built-in timeout
    // (unlike Socket.connect(addr, timeoutMs)) and can hang well past 5s on
    // some OEMs. withTimeoutOrNull cannot interrupt it: structured concurrency
    // waits for the blocking call inside withContext to return on its own. So a
    // separate timer closes the socket instead, which unblocks a hung connect()
    // by making its file descriptor invalid.
    @SuppressLint("MissingPermission")
    private suspend fun connectSocketWithTimeout(socket: BluetoothSocket, timeoutMs: Long) = coroutineScope {
        val timedOut = AtomicBoolean(false)
        val timer = launch {
            delay(timeoutMs)
            timedOut.set(true)
            runCatching { socket.close() }
        }
        try {
            withContext(Dispatchers.IO) { socket.connect() }
        } catch (e: Exception) {
            runCatching { socket.close() }
            if (timedOut.get()) throw IOException("Bluetooth connect timed out after ${timeoutMs}ms")
            throw e
        } finally {
            timer.cancel()
        }
    }

    // Used by the Settings screen to populate the device picker - only devices
    // already paired via Android's own Bluetooth settings show up here. Must
    // check the permission itself rather than trust the caller: this can be
    // queried by a Composable during recomposition right after the user taps
    // "Bluetooth" and before the (async) permission dialog result comes back -
    // calling straight into getBondedDevices() there previously crashed with
    // a SecurityException.
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
