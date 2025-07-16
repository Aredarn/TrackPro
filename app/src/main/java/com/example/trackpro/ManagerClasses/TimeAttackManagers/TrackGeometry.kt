package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.util.Log
import com.example.trackpro.DataClasses.TrackCoordinatesData
import kotlin.math.sqrt

object TrackGeometry {
    private const val TAG = "TrackGeometry"

    data class Vector(val x: Double, val y: Double) {
        operator fun plus(v: Vector) = Vector(x + v.x, y + v.y)
        operator fun minus(v: Vector) = Vector(x - v.x, y - v.y)
        operator fun times(scalar: Double) = Vector(x * scalar, y * scalar)
        fun cross(v: Vector) = x * v.y - y * v.x
        fun length() = sqrt(x * x + y * y)
        fun normalized() = this * (1.0 / length())
    }

    enum class CrossingDirection { ENTERING, EXITING }
    data class CrossingResult(val isValid: Boolean, val direction: CrossingDirection)

    fun calculateFinishLine(track: List<TrackCoordinatesData>): List<TrackCoordinatesData> {
        val startPoint = track.find { it.isStartPoint } ?: run {
            Log.w(TAG, "No start point found, using first two points")
            return if (track.size >= 2) listOf(track[0], track[1]) else track
        }

        val nearbyPoints = track.filter {
            it.id in (startPoint.id - 5)..(startPoint.id + 5) && it.id != startPoint.id
        }.take(10)

        if (nearbyPoints.isEmpty()) {
            Log.w(TAG, "Not enough points near start")
            return if (track.size >= 2) listOf(track[0], track[1]) else track
        }

        val avgDirection = nearbyPoints.fold(Vector(0.0, 0.0)) { acc, point ->
            acc + Vector(point.longitude - startPoint.longitude, point.latitude - startPoint.latitude)
        } * (1.0 / nearbyPoints.size)

        val perpendicular = Vector(-avgDirection.y, avgDirection.x).normalized()
        val lineLength = 12.0 / 111320.0
        val scaledPerpendicular = perpendicular * lineLength

        return listOf(
            startPoint.copy(
                id = -1,
                latitude = startPoint.latitude - scaledPerpendicular.y,
                longitude = startPoint.longitude - scaledPerpendicular.x,
                isStartPoint = false
            ),
            startPoint.copy(
                id = -2,
                latitude = startPoint.latitude + scaledPerpendicular.y,
                longitude = startPoint.longitude + scaledPerpendicular.x,
                isStartPoint = false
            )
        )
    }


    fun calculateSprintLines(
        track: List<TrackCoordinatesData>
    ): Pair<List<TrackCoordinatesData>, List<TrackCoordinatesData>> {
        if (track.size < 2) return emptyList<TrackCoordinatesData>() to emptyList()

        // Start line: first 2 points with perpendicular offset
        val startPoint = track.first()
        val startDirection = calculateDirection(startPoint, track[1])
        val startPerpendicular = Vector(-startDirection.y, startDirection.x).normalized()
        val startLineLength = 12.0 / 111320.0
        val startLine = listOf(
            startPoint.copy(
                id = -10,
                latitude = startPoint.latitude - startPerpendicular.y * startLineLength / 2,
                longitude = startPoint.longitude - startPerpendicular.x * startLineLength / 2
            ),
            startPoint.copy(
                id = -11,
                latitude = startPoint.latitude + startPerpendicular.y * startLineLength / 2,
                longitude = startPoint.longitude + startPerpendicular.x * startLineLength / 2
            )
        )

        // Finish line: last 2 points with perpendicular offset
        val finishPoint = track.last()
        val finishDirection = calculateDirection(track[track.size - 2], finishPoint)
        val finishPerpendicular = Vector(-finishDirection.y, finishDirection.x).normalized()
        val finishLineLength = 12.0 / 111320.0
        val finishLine = listOf(
            finishPoint.copy(
                id = -20,
                latitude = finishPoint.latitude - finishPerpendicular.y * finishLineLength / 2,
                longitude = finishPoint.longitude - finishPerpendicular.x * finishLineLength / 2
            ),
            finishPoint.copy(
                id = -21,
                latitude = finishPoint.latitude + finishPerpendicular.y * finishLineLength / 2,
                longitude = finishPoint.longitude + finishPerpendicular.x * finishLineLength / 2
            )
        )

        return startLine to finishLine
    }

    private fun calculateDirection(
        from: TrackCoordinatesData,
        to: TrackCoordinatesData
    ): Vector {
        return Vector(
            to.longitude - from.longitude,
            to.latitude - from.latitude
        ).normalized()
    }


    fun checkLineCrossing(
        prev: com.example.trackpro.ManagerClasses.RawGPSData,
        curr: com.example.trackpro.ManagerClasses.RawGPSData,
        line: List<TrackCoordinatesData>
    ): CrossingResult? {
        if (line.size < 2) return null

        val prevPos = Vector(prev.latitude, prev.longitude)
        val currPos = Vector(curr.latitude, curr.longitude)
        val lineStart = Vector(line[0].latitude, line[0].longitude)
        val lineEnd = Vector(line[1].latitude, line[1].longitude)

        val intersection = findIntersection(prevPos, currPos, lineStart, lineEnd)
        return intersection?.let {
            CrossingResult(true, determineDirection(prevPos, currPos, lineStart, lineEnd))
        }
    }

    private fun findIntersection(a1: Vector, a2: Vector, b1: Vector, b2: Vector): Vector? {
        val r = a2 - a1
        val s = b2 - b1
        val rxs = r.cross(s)
        if (rxs == 0.0) return null

        val qmp = b1 - a1
        val t = qmp.cross(s) / rxs
        val u = qmp.cross(r) / rxs
        return if (t in 0.0..1.0 && u in 0.0..1.0) a1 + r * t else null
    }

    private fun determineDirection(
        prevPos: Vector,
        currPos: Vector,
        lineStart: Vector,
        lineEnd: Vector
    ) = if ((currPos - prevPos).cross(lineEnd - lineStart) > 0) {
        CrossingDirection.ENTERING
    } else {
        CrossingDirection.EXITING
    }
}