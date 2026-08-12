package com.example.trackpro

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import org.junit.Assert.*
import org.junit.Test

class TrackGeometryTest {

    private fun gpsPoint(lat: Double, lon: Double) = RawGPSData(
        id = 0,
        sessionid = 1L,
        timestamp = 0L,
        latitude = lat,
        longitude = lon,
        altitude = 0.0,
        speed = 0f,
        fixQuality = 0
    )

    private fun trackPoint(id: Long, lat: Double, lon: Double, isStart: Boolean = false) =
        TrackCoordinatesData(
            id = id,
            trackId = 1L,
            latitude = lat,
            longitude = lon,
            altitude = 0.0,
            isStartPoint = isStart
        )

    private fun sectorPoint(id: Long, lat: Double, lon: Double, sectorIndex: Int) =
        TrackCoordinatesData(
            id = id,
            trackId = 1L,
            latitude = lat,
            longitude = lon,
            altitude = 0.0,
            isSectorPoint = true,
            sectorIndex = sectorIndex
        )

    // ─────────────────────────────────────────────
    // checkLineCrossing basics
    // ─────────────────────────────────────────────

    @Test
    fun `checkLineCrossing returns null when line has fewer than 2 points`() {
        val prev = gpsPoint(47.0, 19.0)
        val curr = gpsPoint(47.0, 19.001)
        val line = listOf(trackPoint(1, 47.001, 19.0005))

        assertNull(TrackGeometry.checkLineCrossing(prev, curr, line))
    }

    @Test
    fun `checkLineCrossing returns null when path does not cross the line`() {
        val prev = gpsPoint(47.0, 19.0)
        val curr = gpsPoint(47.0, 19.001)
        // Line is far to the east; the short east-moving path never reaches it
        val line = listOf(
            trackPoint(1, 46.999, 19.5),
            trackPoint(2, 47.001, 19.5)
        )

        assertNull(TrackGeometry.checkLineCrossing(prev, curr, line))
    }

    // ─────────────────────────────────────────────
    // Direction-aware validity (regression test for the bug where
    // isValid was hardcoded true regardless of crossing direction)
    // ─────────────────────────────────────────────

    @Test
    fun `checkLineCrossing marks a forward crossing valid with ENTERING direction`() {
        // North-south line, built the same way calculateFinishLine/calculateSprintLines
        // build lines for an eastward track: line[0] is the "south" endpoint,
        // line[1] is the "north" endpoint.
        val line = listOf(
            trackPoint(1, 46.999, 19.0005),
            trackPoint(2, 47.001, 19.0005)
        )

        val prev = gpsPoint(47.0, 19.0000) // west of the line
        val curr = gpsPoint(47.0, 19.0010) // east of the line -> forward crossing

        val result = TrackGeometry.checkLineCrossing(prev, curr, line)

        assertNotNull(result)
        assertTrue(result!!.isValid)
        assertEquals(TrackGeometry.CrossingDirection.ENTERING, result.direction)
    }

    @Test
    fun `checkLineCrossing marks a backward rollback crossing invalid with EXITING direction`() {
        val line = listOf(
            trackPoint(1, 46.999, 19.0005),
            trackPoint(2, 47.001, 19.0005)
        )

        val prev = gpsPoint(47.0, 19.0010) // east of the line
        val curr = gpsPoint(47.0, 19.0000) // west of the line -> rolled back

        val result = TrackGeometry.checkLineCrossing(prev, curr, line)

        assertNotNull(result)
        assertFalse(result!!.isValid)
        assertEquals(TrackGeometry.CrossingDirection.EXITING, result.direction)
    }

    @Test
    fun `finish line built from an eastward track only validates eastward crossings`() {
        // A short straight track heading east from the start point.
        val track = listOf(
            trackPoint(1, 47.0, 19.0000, isStart = true),
            trackPoint(2, 47.0, 19.0001),
            trackPoint(3, 47.0, 19.0002),
            trackPoint(4, 47.0, 19.0003),
            trackPoint(5, 47.0, 19.0004)
        )
        val finishLine = TrackGeometry.calculateFinishLine(track)
        assertEquals(2, finishLine.size)

        // Driving forward (east) through the line should be a valid crossing.
        val forward = TrackGeometry.checkLineCrossing(
            gpsPoint(47.0, 18.9998),
            gpsPoint(47.0, 19.0002),
            finishLine
        )
        assertNotNull(forward)
        assertTrue(forward!!.isValid)

        // Overshooting and rolling back (west) across the same line must not
        // register as another valid crossing.
        val rollback = TrackGeometry.checkLineCrossing(
            gpsPoint(47.0, 19.0002),
            gpsPoint(47.0, 18.9998),
            finishLine
        )
        assertNotNull(rollback)
        assertFalse(rollback!!.isValid)
    }

    // ─────────────────────────────────────────────
    // calculateFinishLine
    // ─────────────────────────────────────────────

    @Test
    fun `calculateFinishLine falls back to first two points when no start point is marked`() {
        val track = listOf(
            trackPoint(1, 47.0, 19.0000),
            trackPoint(2, 47.0, 19.0001),
            trackPoint(3, 47.0, 19.0002)
        )

        val finishLine = TrackGeometry.calculateFinishLine(track)

        assertEquals(listOf(track[0], track[1]), finishLine)
    }

    // ─────────────────────────────────────────────
    // calculateSprintLines
    // ─────────────────────────────────────────────

    @Test
    fun `calculateSprintLines returns a 2-point start line and a 2-point finish line`() {
        val track = listOf(
            trackPoint(1, 47.0, 19.0000, isStart = true),
            trackPoint(2, 47.0, 19.0001),
            trackPoint(3, 47.0, 19.0002),
            trackPoint(4, 47.0, 19.0003)
        )

        val (startLine, finishLine) = TrackGeometry.calculateSprintLines(track)

        assertEquals(2, startLine.size)
        assertEquals(2, finishLine.size)
    }

    @Test
    fun `calculateSprintLines returns empty lines for a track with fewer than 2 points`() {
        val (startLine, finishLine) = TrackGeometry.calculateSprintLines(listOf(trackPoint(1, 47.0, 19.0)))

        assertTrue(startLine.isEmpty())
        assertTrue(finishLine.isEmpty())
    }

    // ─────────────────────────────────────────────
    // calculateSectorLines
    // ─────────────────────────────────────────────

    @Test
    fun `calculateSectorLines returns empty list when no points are marked as sectors`() {
        val track = listOf(
            trackPoint(1, 47.0, 19.0000, isStart = true),
            trackPoint(2, 47.0, 19.0001),
            trackPoint(3, 47.0, 19.0002)
        )

        assertTrue(TrackGeometry.calculateSectorLines(track).isEmpty())
    }

    @Test
    fun `calculateSectorLines returns one 2-point gate per marked sector, ordered by sectorIndex`() {
        val track = listOf(
            trackPoint(1, 47.0, 19.0000, isStart = true),
            trackPoint(2, 47.0, 19.0001),
            sectorPoint(3, 47.0, 19.0002, sectorIndex = 1),
            trackPoint(4, 47.0, 19.0003),
            sectorPoint(5, 47.0, 19.0004, sectorIndex = 0),
            trackPoint(6, 47.0, 19.0005)
        )

        val sectorLines = TrackGeometry.calculateSectorLines(track)

        // Marked at sectorIndex 0 and 1 -> two gates, ordered 0 then 1 regardless of track position
        assertEquals(2, sectorLines.size)
        sectorLines.forEach { assertEquals(2, it.size) }

        // The first returned gate should be built around the sectorIndex=0 point (id=5),
        // which sits at a different longitude than the sectorIndex=1 point (id=3).
        val firstGateLon = sectorLines[0].map { it.longitude }.average()
        val secondGateLon = sectorLines[1].map { it.longitude }.average()
        assertEquals(19.0004, firstGateLon, 0.00001)
        assertEquals(19.0002, secondGateLon, 0.00001)
    }

    // ─────────────────────────────────────────────
    // autoSliceSectors
    // ─────────────────────────────────────────────

    @Test
    fun `autoSliceSectors marks sectorCount minus 1 points evenly along the track`() {
        // 11 evenly-spaced points along a straight line -> ~evenly-spaced distance too
        val track = (0..10).map { i -> trackPoint(i.toLong(), 47.0, 19.0000 + i * 0.0001) }

        val sliced = TrackGeometry.autoSliceSectors(track, sectorCount = 3)

        val marked = sliced.filter { it.isSectorPoint }.sortedBy { it.sectorIndex }
        assertEquals(2, marked.size)
        assertEquals(listOf(0, 1), marked.map { it.sectorIndex })
        // Roughly 1/3 and 2/3 of the way along
        assertTrue(marked[0].id in 2..4)
        assertTrue(marked[1].id in 6..8)
    }

    @Test
    fun `autoSliceSectors replaces a previous slicing rather than accumulating`() {
        val track = (0..10).map { i -> trackPoint(i.toLong(), 47.0, 19.0000 + i * 0.0001) }

        val firstPass = TrackGeometry.autoSliceSectors(track, sectorCount = 5)
        assertEquals(4, firstPass.count { it.isSectorPoint })

        val secondPass = TrackGeometry.autoSliceSectors(firstPass, sectorCount = 2)
        assertEquals(1, secondPass.count { it.isSectorPoint })
    }

    @Test
    fun `autoSliceSectors with count under 2 clears all markers`() {
        val track = listOf(
            sectorPoint(1, 47.0, 19.0001, sectorIndex = 0),
            trackPoint(2, 47.0, 19.0002)
        )

        val cleared = TrackGeometry.autoSliceSectors(track, sectorCount = 1)

        assertTrue(cleared.none { it.isSectorPoint })
        assertTrue(cleared.all { it.sectorIndex == null })
    }
}
