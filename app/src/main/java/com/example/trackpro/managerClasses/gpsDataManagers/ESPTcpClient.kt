package com.example.trackpro.managerClasses.gpsDataManagers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * GPS over the ESP32's Wi-Fi access point: newline-delimited JSON on a plain TCP socket.
 *
 * Only knows how to open that socket. Reading, parsing, commands and staying connected are
 * [LineStreamGpsProvider]'s.
 */
class ESPTcpClient(
    serverAddress: String,
    port: Int
) : LineStreamGpsProvider(tag = "ESPTcpClient") {

    // Mutable (not the constructor params directly) so updateTarget() can redirect a live
    // singleton - e.g. switching between the real ESP32 and a test simulator from Settings -
    // without restarting the app. Read afresh on every connection attempt.
    @Volatile private var serverAddress: String = serverAddress
    @Volatile private var port: Int = port

    /**
     * Points future connections at a different host/port, reconnecting straight away if a
     * link is running.
     *
     * This used to disconnect and connect back to back, which raced: the old connection's
     * cleanup ran after the new one had started, cleared the new socket out from under it,
     * and marked the provider stopped - so switching target often left it disconnected.
     */
    fun updateTarget(address: String, newPort: Int) {
        val changed = serverAddress != address || port != newPort
        serverAddress = address
        port = newPort
        if (changed) reconnectNow()
    }

    override suspend fun open(): Link {
        val socket = Socket()
        try {
            withContext(Dispatchers.IO) {
                socket.connect(InetSocketAddress(serverAddress, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        return object : Link {
            override val input: InputStream = socket.getInputStream()
            override val output: OutputStream = socket.getOutputStream()
            override fun close() = socket.close()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
