package com.example.trackpro.ManagerClasses

import android.content.Context
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
}
