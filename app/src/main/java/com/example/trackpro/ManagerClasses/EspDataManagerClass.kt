package com.example.trackpro.ManagerClasses

import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ESP32Manager(
    private val ip: String,
    private val port: Int,
    var onDataReceived: (String) -> Unit = {},
    var onConnectionStatusChanged: (Boolean) -> Unit = {}
) {
    private var socket: Socket? = null
    private var input: BufferedReader? = null
    private var output: PrintWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        scope.launch {
            try {
                socket = Socket(ip, port)
                input = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                output = PrintWriter(socket!!.getOutputStream(), true)
                onConnectionStatusChanged(true)
                listenForData()
            } catch (e: Exception) {
                e.printStackTrace()
                onConnectionStatusChanged(false)
            }
        }
    }

    private fun listenForData() {
        scope.launch {
            try {
                var line: String?
                while (socket != null && socket!!.isConnected) {
                    line = input?.readLine()
                    line?.let { onDataReceived(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onConnectionStatusChanged(false)
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                output?.println(command)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                socket?.close()
                socket = null
                onConnectionStatusChanged(false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
