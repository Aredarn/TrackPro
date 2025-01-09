package com.example.trackpro.ManagerClasses

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ESP32Manager(
    private val url: String,
    var onDataReceived: (String) -> Unit = {},
    var onConnectionStatusChanged: (Boolean) -> Unit = {}
) {
    private val webSocketClient = ESPWebSocketClient(
        url,
        onMessageReceived = { message -> onDataReceived(message) }, // Pass incoming message to callback
        onConnectionStatusChanged = { isConnected -> onConnectionStatusChanged(isConnected) }
    )

    // Connect to the WebSocket server
    fun connect() {
        webSocketClient.connect()
    }

    // Send a message to the WebSocket server
    fun sendMessage(message: String) {
        webSocketClient.sendMessage(message)
    }

    // Disconnect the WebSocket connection
    fun disconnect() {
        webSocketClient.disconnect()
    }
}


