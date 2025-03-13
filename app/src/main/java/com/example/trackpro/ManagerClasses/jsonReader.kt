package com.example.trackpro.ManagerClasses

import android.content.Context
import com.example.trackpro.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

object JsonReader {

    private var ipAddress: String? = null
    private var port: Int? = null

    fun loadConfig(context: Context): Pair<String, Int> {
        if (ipAddress != null && port != null) {
            return Pair(ipAddress!!, port!!)
        }

        val resourceId = context.resources.getIdentifier("config", "raw", context.packageName)
        val inputStream = context.resources.openRawResource(resourceId)
        val jsonText = inputStream.bufferedReader().use { it.readText() }

        val jsonObject = JSONObject(jsonText)
        ipAddress = jsonObject.getString("ip_address")
        port = jsonObject.getInt("port")

        return Pair(ipAddress!!, port!!)
    }


    fun loadJsonOptions(context: Context): VehicleOptions {
        val jsonString = context.resources.openRawResource(R.raw.vehicle_options).bufferedReader().use { it.readText() }
        return Json.decodeFromString(jsonString)
    }


    @Serializable
    data class VehicleOptions(
        val engineTypes: List<String>,
        val drivetrains: List<String>,
        val fuelTypes: List<String>,
        val tireTypes: List<String>,
        val transmissions: List<String>,
        val suspensionTypes: List<String>
    )
}
