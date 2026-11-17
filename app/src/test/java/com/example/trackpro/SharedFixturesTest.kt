package com.example.trackpro

import com.example.trackpro.dao.LapSpeedSample
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.calculationClasses.DragRunAnalyzer
import com.example.trackpro.managerClasses.calculationClasses.DragSpeedScale
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeMillisOrNull
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.online.PayloadMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The cases in `shared/fixtures`, which the web app runs too. A failure here or there means the
 * two apps no longer agree; see PARITY.md.
 */
class SharedFixturesTest {

    private fun fixture(name: String): JsonObject {
        val dir = System.getProperty("trackpro.fixtures") ?: "../shared/fixtures"
        return Json.parseToJsonElement(File(dir, name).readText()).jsonObject
    }

    private fun JsonElement.doubleOrNullValue(): Double? = if (this is JsonNull) null else jsonPrimitive.doubleOrNull

    private fun JsonObject.number(key: String): Double? = getValue(key).doubleOrNullValue()

    private fun assertNear(message: String, expected: Double?, actual: Double?, within: Double) {
        if (expected == null) return assertNull(message, actual)
        assertEquals(message, expected, actual ?: Double.NaN, within)
    }

    // ── Lap times ─────────────────────────────────────────────

    @Test
    fun `lap times parse and format alike`() {
        val cases = fixture("lap-time.json")
        cases.getValue("parse").jsonArray.forEach {
            val case = it.jsonObject
            val text = case.getValue("text").jsonPrimitive.content
            assertEquals("parse \"$text\"", case.getValue("millis").jsonPrimitive.longOrNull, text.toLapTimeMillisOrNull())
        }
        cases.getValue("format").jsonArray.forEach {
            val case = it.jsonObject
            val millis = case.getValue("millis").jsonPrimitive.long
            assertEquals("format $millis", case.getValue("text").jsonPrimitive.content, millis.toLapTimeString())
        }
        cases.getValue("delta").jsonArray.forEach {
            val case = it.jsonObject
            val millis = case.getValue("millis").jsonPrimitive.long
            assertEquals("delta $millis", case.getValue("text").jsonPrimitive.content, millis.toLapDeltaString())
        }
    }

    // ── Lap top speed ─────────────────────────────────────────

    @Test
    fun `lap top speeds agree`() {
        fixture("lap-top-speed.json").getValue("laps").jsonArray.forEach {
            val case = it.jsonObject
            val samples = case.getValue("speeds").jsonArray.map { s -> LapSpeedSample(1L, s.doubleOrNullValue()?.toFloat()) }
            assertNear(case.getValue("name").jsonPrimitive.content, case.number("top"), PayloadMapper.topSpeeds(samples)[1L], 1e-9)
        }
    }

    // ── Drag ──────────────────────────────────────────────────

    private val drag by lazy { fixture("drag-run.json") }

    private val fixes by lazy {
        drag.getValue("fixes").jsonArray.map {
            val (timestamp, lat, lon, speed) = it.jsonArray.map { v -> v.jsonPrimitive.content.toDouble() }
            RawGPSData(
                sessionid = 1L, latitude = lat, longitude = lon, altitude = null,
                timestamp = timestamp.toLong(), speed = speed.toFloat(), fixQuality = 12
            )
        }
    }

    private fun tolerance(key: String) = drag.getValue("tolerance").jsonObject.number(key)!!

    @Test
    fun `drag splits agree in both unit systems`() {
        val seconds = tolerance("seconds")
        for ((name, scale) in listOf("metric" to DragSpeedScale.Metric, "imperial" to DragSpeedScale.Imperial)) {
            val expected = drag.getValue("metrics").jsonObject.getValue(name).jsonObject
            val metrics = DragTimeCalculation(1L, null, scale).calculateFullSessionMetrics(fixes)
            for ((group, splits) in listOf("standing" to metrics.standing, "rolling" to metrics.rolling)) {
                val want = expected.getValue(group).jsonObject
                assertEquals("$name $group labels", want.keys.toList(), splits.map { it.label })
                splits.forEach { assertNear("$name ${it.label}", want.number(it.label), it.seconds, seconds) }
            }
            if (name != "metric") continue
            assertNear("quarter mile", expected.number("quarterMileTime"), metrics.quarterMileTime, seconds)
            assertNear("trap speed", expected.number("quarterMileSpeed"), metrics.quarterMileSpeed?.toDouble(), tolerance("kmh"))
            assertNear("half mile", expected.number("halfMileTime"), metrics.halfMileTime, seconds)
            assertNear("max speed", expected.number("maxSpeed"), metrics.maxSpeed.toDouble(), tolerance("kmh"))
            assertNear("distance", expected.number("totalDistance"), metrics.totalDistance.toDouble(), tolerance("meters"))
            assertEquals("runs", expected.number("runCount")!!.toInt(), metrics.runCount)
        }
    }

    @Test
    fun `the drag comparison reads the pull alike`() {
        val pull = drag.getValue("pull").jsonObject
        val profile = DragRunAnalyzer.analyze(1L, fixes)!!
        pull.getValue("timeAt").jsonObject.forEach { (kmh, at) ->
            assertNear("time to $kmh", at.doubleOrNullValue(), profile.timeAt(kmh.toDouble()), tolerance("seconds"))
        }
        assertNear("peak", pull.number("peakKmh"), profile.peakKmh, tolerance("kmh"))
        val shifts = pull.getValue("shifts").jsonArray.map { it.jsonObject }
        assertEquals("shifts", shifts.size, profile.shifts.size)
        shifts.zip(profile.shifts).forEach { (want, got) ->
            assertNear("shift speed", want.number("speedKmh"), got.speedKmh, tolerance("kmh"))
            assertNear("shift cost", want.number("lostSeconds"), got.lostSeconds, tolerance("seconds"))
        }
    }
}
