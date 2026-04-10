package com.example.trackpro.managerClasses

import android.util.Log
import com.example.trackpro.dataClasses.RawGPSData
import convertToUnixTimestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ESPTcpClient(
    private val serverAddress: String,
    private val port: Int
) {
    // --- Observables (Singletons use these instead of callbacks) ---
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    val gpsFlow: StateFlow<RawGPSData?> = _gpsFlow.asStateFlow()


    // --- Internal State ---
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var running = AtomicBoolean(false)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val bufferPool = BufferPool(512, 10)

    // --- Core Methods ---

    fun connect() {
        if (running.getAndSet(true)) return

        scope.launch {
            try {
                socket = Socket()
                // 5-second timeout for the initial connection attempt
                socket?.connect(InetSocketAddress(serverAddress, port), 5000)

                val inputStream = socket?.getInputStream() ?: throw Exception("Failed to get input stream")
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

    private suspend fun processChunk(buffer: ByteArray, length: Int) {
        val message = buffer.decodeToString(0, length).trim()
        if (message.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                try {
                    val raw = json.decodeFromString<RawGPSDataRaw>(message)
                    val parsed = RawGPSData(
                        sessionid = 0L,
                        latitude = raw.latitude,
                        longitude = raw.longitude,
                        altitude = raw.altitude,
                        speed = raw.speed,
                        fixQuality = raw.satellites,
                        timestamp = convertToUnixTimestamp(raw.timestamp)
                    )
                    _gpsFlow.emit(parsed)
                } catch (e: Exception) {
                    Log.e("ESPTcpClient", "JSON Parse Error: ${e.message} for input: $message")
                }
            }
        }
    }


    fun disconnect() {
        running.set(false)
        runCatching { socket?.close() }
        _connectionStatus.value = false
        scope.cancel()
    }

    private fun disconnectInternal() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("ESPTcpClient", "Error closing socket: ${e.message}")
        }
        socket = null
        running.set(false)
        _connectionStatus.value = false
    }

    // --- Helper Classes ---

    @Serializable
    private data class RawGPSDataRaw(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double = 0.0,
        val speed: Float = 0f,
        val satellites: Int = 0,
        val timestamp: String
    )

    class DelimitedInputStreamReader(
        private val input: InputStream,
        private val delimiter: ByteArray
    ) {
        private val buffer = ByteArrayOutputStream()

        fun read(target: ByteArray): Int {
            try {
                while (true) {
                    val byte = input.read()
                    if (byte == -1) return -1

                    buffer.write(byte)

                    if (endsWithDelimiter()) {
                        val fullData = buffer.toByteArray()
                        val length = fullData.size - delimiter.size

                        // Ensure we don't overflow the target buffer
                        val finalSize = if (length > target.size) target.size else length
                        System.arraycopy(fullData, 0, target, 0, finalSize)

                        buffer.reset()
                        return finalSize
                    }

                    // Emergency flush if buffer gets too large (corrupt stream protection)
                    if (buffer.size() > 2048) buffer.reset()
                }
            } catch (e: Exception) {
                return -1
            }
        }

        private fun endsWithDelimiter(): Boolean {
            val data = buffer.toByteArray()
            if (data.size < delimiter.size) return false
            for (i in delimiter.indices) {
                if (data[data.size - delimiter.size + i] != delimiter[i]) return false
            }
            return true
        }
    }

    class BufferPool(private val bufferSize: Int, poolSize: Int) {
        private val pool = ArrayDeque<ByteArray>(poolSize).apply {
            repeat(poolSize) { add(ByteArray(bufferSize)) }
        }

        @Synchronized
        fun obtain(): ByteArray = pool.removeFirstOrNull() ?: ByteArray(bufferSize)

        @Synchronized
        fun recycle(buffer: ByteArray) {
            if (pool.size < 10) pool.addLast(buffer)
        }
    }
}