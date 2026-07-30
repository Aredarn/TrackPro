package com.example.trackpro.managerClasses.timeAttackManagers

import android.util.Log
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import kotlin.math.sqrt

object TrackGeometry {
    private const val TAG = "TrackGeometry"
    private const val FINISH_LINE_WIDTH_METERS = 12.0
    private const val METERS_PER_DEGREE_LATITUDE = 111320.0

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

        return buildGateLine(track, startPoint, idBase = -1) ?: run {
            Log.w(TAG, "Not enough points near start")
            if (track.size >= 2) listOf(track[0], track[1]) else track
        }
    }

    /**
     * Computes a perpendicular gate line (2 points) through each point on the track that was
     * marked as a sector split during track building, ordered by sectorIndex. Tracks with no
     * marked sector points (e.g. the bundled seed tracks) simply return an empty list.
     */
    fun calculateSectorLines(track: List<TrackCoordinatesData>): List<List<TrackCoordinatesData>> {
        val sectorPoints = track.filter { it.isSectorPoint }
            .sortedBy { it.sectorIndex ?: Int.MAX_VALUE }

        return sectorPoints.mapIndexedNotNull { index, point ->
            buildGateLine(track, point, idBase = -100 - (index * 2L))
        }
    }

    /**
     * Builds a perpendicular gate line through [atPoint], oriented to the track's local
     * direction of travel around it (same construction as the finish/sector lines), or null
     * if there aren't enough nearby points to establish a direction.
     */
    private fun buildGateLine(
        track: List<TrackCoordinatesData>,
        atPoint: TrackCoordinatesData,
        idBase: Long
    ): List<TrackCoordinatesData>? {
        val nearbyPoints = track.filter {
            it.id in (atPoint.id - 5)..(atPoint.id + 5) && it.id != atPoint.id
        }.take(10)

        if (nearbyPoints.isEmpty()) return null

        val avgDirection = nearbyPoints.fold(Vector(0.0, 0.0)) { acc, point ->
            acc + Vector(point.longitude - atPoint.longitude, point.latitude - atPoint.latitude)
        } * (1.0 / nearbyPoints.size)

        val perpendicular = Vector(-avgDirection.y, avgDirection.x).normalized()
        val lineLength = FINISH_LINE_WIDTH_METERS / METERS_PER_DEGREE_LATITUDE
        val scaledPerpendicular = perpendicular * lineLength

        return listOf(
            atPoint.copy(
                id = idBase,
                latitude = atPoint.latitude - scaledPerpendicular.y,
                longitude = atPoint.longitude - scaledPerpendicular.x,
                isStartPoint = false,
                isSectorPoint = false
            ),
            atPoint.copy(
                id = idBase - 1,
                latitude = atPoint.latitude + scaledPerpendicular.y,
                longitude = atPoint.longitude + scaledPerpendicular.x,
                isStartPoint = false,
                isSectorPoint = false
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
        val startLineLength = FINISH_LINE_WIDTH_METERS / METERS_PER_DEGREE_LATITUDE
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
        val finishLineLength = FINISH_LINE_WIDTH_METERS / METERS_PER_DEGREE_LATITUDE
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
        prev: RawGPSData,
        curr: RawGPSData,
        line: List<TrackCoordinatesData>
    ): CrossingResult? {
        if (line.size < 2) return null

        // Standardize to (X = Lon, Y = Lat)
        val prevPos = Vector(prev.longitude, prev.latitude)
        val currPos = Vector(curr.longitude, curr.latitude)
        val lineStart = Vector(line[0].longitude, line[0].latitude)
        val lineEnd = Vector(line[1].longitude, line[1].latitude)

        val intersection = findIntersection(prevPos, currPos, lineStart, lineEnd)

        // Debug Log: If you see this in Logcat, the geometry is working!
        if (intersection != null) {
            Log.d("TrackGeometry", "INTERSECTION DETECTED at $intersection")
        }

        return intersection?.let {
            val direction = determineDirection(prevPos, currPos, lineStart, lineEnd)
            // Lines are built with the perpendicular rotated from the track's forward
            // direction of travel, which makes ENTERING correspond to a crossing in that
            // forward direction. Only forward crossings should count, otherwise a car
            // overshooting the line and rolling back across it would register a second
            // (bogus) crossing.
            CrossingResult(direction == CrossingDirection.ENTERING, direction)
        }
    }

    private fun findIntersection(a1: Vector, a2: Vector, b1: Vector, b2: Vector): Vector? {
        val r = a2 - a1 // Car vector
        val s = b2 - b1 // Finish line vector
        val rxs = r.cross(s)

        // If rxs is 0, the lines are parallel and will never intersect
        if (Math.abs(rxs) < 1e-10) return null

        val qmp = b1 - a1
        val t = qmp.cross(s) / rxs
        val u = qmp.cross(r) / rxs

        // t is the "time" along the car's path (0.0 to 1.0)
        // u is the "position" along the finish line (0.0 to 1.0)
        return if (t in 0.0..1.0 && u in 0.0..1.0) {
            a1 + r * t
        } else null
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