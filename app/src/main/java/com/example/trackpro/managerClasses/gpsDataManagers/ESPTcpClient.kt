package com.example.trackpro.managerClasses.gpsDataManagers

import android.util.Log
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
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ESPTcpClient(
    serverAddress: String,
    port: Int
) : CommandableGpsProvider {
    // Mutable (not the constructor params directly) so updateTarget() can
    // redirect a live singleton - e.g. switching between the real ESP32 and
    // a test simulator from Settings - without restarting the app.
    @Volatile private var serverAddress: String = serverAddress
    @Volatile private var port: Int = port

    // --- Observables (Singletons use these instead of callbacks) ---
    private val _connectionStatus = MutableStateFlow(false)
    override val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    override val gpsFlow: StateFlow<RawGPSData?> = _gpsFlow.asStateFlow()
    private val _confirmedRateHz = MutableStateFlow<Int?>(null)
    override val confirmedRateHz: StateFlow<Int?> = _confirmedRateHz.asStateFlow()

    override fun start() = connect()

    override fun stop() = disconnect()


    // --- Internal State ---
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var running = AtomicBoolean(false)
    @Volatile private var outputStream: OutputStream? = null
    private val writeMutex = Mutex()

    private val bufferPool = BufferPool(512, 10)

    // --- Core Methods ---

    // Redirects future connections to a different host/port. If currently
    // connected, reconnects immediately to the new target instead of waiting
    // for the next manual start().
    fun updateTarget(address: String, newPort: Int) {
        val changed = serverAddress != address || port != newPort
        serverAddress = address
        port = newPort
        if (changed && running.get()) {
            disconnect()
            connect()
        }
    }

    fun connect() {
        if (running.getAndSet(true)) return

        scope.launch {
            try {
                socket = Socket()
                // 5-second timeout for the initial connection attempt
                socket?.connect(InetSocketAddress(serverAddress, port), 5000)

                val inputStream = socket?.getInputStream() ?: throw Exception("Failed to get input stream")
                outputStream = socket?.getOutputStream()
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
                            // Server closed connection
                            break
                        }
                    } finally {
                        bufferPool.recycle(buffer)
                    }
                }
            } catch (e: Exception) {
                Log.e("ESPTcpClient", "Connection error: ${e.message}")
            } finally {
                disconnectInternal()
            }
        }
    }

    // Sends a command (e.g. "RATE:20\n") to the ESP32. Dispatches onto this
    // client's own IO scope so callers (Compose onClick handlers) never block,
    // and a Mutex serializes concurrent writers against each other - reading
    // and writing the same socket concurrently is safe by contract and needs
    // no lock between them.
    override fun sendCommand(command: String) {
        val out = outputStream ?: return
        scope.launch {
            writeMutex.withLock {
                try {
                    out.write(command.toByteArray(Charsets.US_ASCII))
                    out.flush()
                } catch (e: Exception) {
                    Log.e("ESPTcpClient", "sendCommand failed: ${e.message}")
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
            Log.w("ESPTcpClient", "GPS module rejected rate change")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                val raw = gpsJsonParser.decodeFromString<RawGPSDataRaw>(message)
                _gpsFlow.value = raw.toEntity()  // Use .value instead of .emit()
            } catch (e: Exception) {
                Log.e("ESPTcpClient", "JSON Parse Error: ${e.message} for input: $message")
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
            Log.e("ESPTcpClient", "Error closing socket: ${e.message}")
        }
        socket = null
        outputStream = null
        running.set(false)
        _connectionStatus.value = false
    }
}
