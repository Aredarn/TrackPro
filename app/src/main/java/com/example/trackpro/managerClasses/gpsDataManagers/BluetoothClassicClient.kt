package com.example.trackpro.managerClasses.gpsDataManagers

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.models.CommandableGpsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// Bluetooth Classic (SPP) transport - an alternative to ESPTcpClient's WiFi TCP
// socket. Requires the ESP32 to already be paired via Android's own Bluetooth
// settings; this class only connects to an already-bonded device (no discovery
// UI), whose MAC address is read from SharedPreferences at connect time.
class BluetoothClassicClient(private val context: Context) : CommandableGpsProvider {

    private val _connectionStatus = MutableStateFlow(false)
    override val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    override val gpsFlow: StateFlow<RawGPSData?> = _gpsFlow.asStateFlow()
    private val _confirmedRateHz = MutableStateFlow<Int?>(null)
    override val confirmedRateHz: StateFlow<Int?> = _confirmedRateHz.asStateFlow()

    override fun start() = connect()

    override fun stop() = disconnect()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: BluetoothSocket? = null
    private var running = AtomicBoolean(false)
    @Volatile private var outputStream: OutputStream? = null
    private val writeMutex = Mutex()

    private val bufferPool = BufferPool(512, 10)

    // BLUETOOTH_CONNECT is only a real runtime permission from API 31 onward;
    // below that, the manifest-declared BLUETOOTH/BLUETOOTH_ADMIN permissions
    // are enough and there's nothing to check at runtime.
    fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (running.getAndSet(true)) return

        scope.launch {
            try {
                if (!hasBluetoothPermission()) throw IOException("Bluetooth permission not granted")

                val mac = context.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE)
                    .getString("device_mac", null)
                    ?: throw IOException("No Bluetooth device selected")

                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    ?: throw IOException("Bluetooth not supported on this device")
                val device = adapter.getRemoteDevice(mac)

                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = newSocket
                connectSocketWithTimeout(newSocket, 5000)

                val inputStream = newSocket.inputStream
                outputStream = newSocket.outputStream
                _connectionStatus.value = true

                val delimiter = "\n".toByteArray()
                val reader = DelimitedInputStreamReader(inputStream, delimiter)

                while (running.get()) {
                    val buffer = bufferPool.obtain()
                    try {
                        val bytesRead = reader.read(buffer)
                        if (bytesRead > 0) {
                            processChunk(buffer, bytesRead)
                        } else if (bytesRead == -1) {
                            // Peer closed connection
                            break
                        }
                    } finally {
                        bufferPool.recycle(buffer)
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothClassicClient", "Connection error: ${e.message}")
            } finally {
                disconnectInternal()
            }
        }
    }

    // BluetoothSocket.connect() is a blocking call with no built-in timeout
    // (unlike Socket.connect(addr, timeoutMs)) and can hang well past 5s on
    // some OEMs. withTimeoutOrNull alone only stops *waiting* on it - the
    // underlying blocking call keeps running until it returns on its own - so
    // on timeout we also force-close the socket, which unblocks a hung
    // connect() by making its file descriptor invalid.
    private suspend fun connectSocketWithTimeout(socket: BluetoothSocket, timeoutMs: Long) {
        val connected = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) { socket.connect() }
            true
        }
        if (connected == null) {
            runCatching { socket.close() }
            throw IOException("Bluetooth connect timed out after ${timeoutMs}ms")
        }
    }

    override fun sendCommand(command: String) {
        val out = outputStream ?: return
        scope.launch {
            writeMutex.withLock {
                try {
                    out.write(command.toByteArray(Charsets.US_ASCII))
                    out.flush()
                } catch (e: Exception) {
                    Log.e("BluetoothClassicClient", "sendCommand failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun processChunk(buffer: ByteArray, length: Int) {
        val message = buffer.decodeToString(0, length).trim()
        if (message.isEmpty()) return

        val ackedHz = parseRateAck(message)
        if (ackedHz != null) {
            _confirmedRateHz.value = ackedHz
            return
        }
        if (message == "RATE_ERR") {
            Log.w("BluetoothClassicClient", "GPS module rejected rate change")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                val raw = gpsJsonParser.decodeFromString<RawGPSDataRaw>(message)
                _gpsFlow.value = raw.toEntity()
            } catch (e: Exception) {
                Log.e("BluetoothClassicClient", "JSON Parse Error: ${e.message} for input: $message")
            }
        }
    }

    fun disconnect() {
        running.set(false)
        runCatching { socket?.close() }
        outputStream = null
        _connectionStatus.value = false
    }

    private fun disconnectInternal() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothClassicClient", "Error closing socket: ${e.message}")
        }
        socket = null
        outputStream = null
        running.set(false)
        _connectionStatus.value = false
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
}
