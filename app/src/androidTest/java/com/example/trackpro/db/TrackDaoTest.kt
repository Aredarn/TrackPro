package com.example.trackpro.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tracks and their recorded points, as the track builder, viewer and seeder use them. */
@RunWith(AndroidJUnit4::class)
class TrackDaoTest {

    @get:Rule
    val database = InMemoryDatabaseRule()
    private val db get() = database.db

    @Test
    fun coordinates_areScopedToTheirTrack_inRecordedOrder() = runBlocking<Unit> {
        // The list *is* the track path: the map draws it in this order and the gate lines
        // are built from neighbouring ids, so insertion order coming back is load-bearing.
        val ring = db.trackMainDao().insertTrackMainDataDAO(track("Ring"))
        val hill = db.trackMainDao().insertTrackMainDataDAO(track("Hill", type = "sprint"))
        db.trackCoordinatesDao().insertTrack(straightTrack(ring, count = 5))
        db.trackCoordinatesDao().insertTrack(straightTrack(hill, count = 3))

        val ringPoints = db.trackCoordinatesDao().getCoordinatesOfTrack(ring).first()

        assertEquals(5, ringPoints.size)
        assertTrue(ringPoints.all { it.trackId == ring })
        assertEquals(ringPoints.sortedBy { it.id }, ringPoints)
        assertTrue("only the first recorded point is the start", ringPoints.first().isStartPoint)
        assertTrue(ringPoints.drop(1).none { it.isStartPoint })
        assertEquals(
            "longitudes climb the way they were recorded",
            ringPoints.map { it.longitude },
            ringPoints.map { it.longitude }.sorted()
        )
    }

    @Test
    fun sectorMarkers_canBeReappliedInPlace() = runBlocking<Unit> {
        // The track screen's slicer: read the saved points, let TrackGeometry mark the
        // boundaries, write them back by id. Must update the existing rows, never add any.
        val trackId = db.trackMainDao().insertTrackMainDataDAO(track())
        db.trackCoordinatesDao().insertTrack(straightTrack(trackId, count = 11))
        val saved = db.trackCoordinatesDao().getCoordinatesOfTrack(trackId).first()

        db.trackCoordinatesDao().updateTrackCoordinates(TrackGeometry.autoSliceSectors(saved, sectorCount = 3))
        val threeSectors = db.trackCoordinatesDao().getCoordinatesOfTrack(trackId).first()

        assertEquals("rows were updated, not duplicated", 11, threeSectors.size)
        assertEquals(listOf(0, 1), threeSectors.filter { it.isSectorPoint }.map { it.sectorIndex })
        assertTrue(threeSectors.first().isStartPoint)

        // Re-slicing replaces the previous markers rather than accumulating them.
        db.trackCoordinatesDao().updateTrackCoordinates(TrackGeometry.autoSliceSectors(threeSectors, sectorCount = 2))
        val twoSectors = db.trackCoordinatesDao().getCoordinatesOfTrack(trackId).first()

        assertEquals(1, twoSectors.count { it.isSectorPoint })
        assertTrue(twoSectors.filter { !it.isSectorPoint }.all { it.sectorIndex == null })
    }

    @Test
    fun deletingATrack_removesItsCoordinates() = runBlocking<Unit> {
        val trackId = db.trackMainDao().insertTrackMainDataDAO(track())
        db.trackCoordinatesDao().insertTrack(straightTrack(trackId))

        db.trackMainDao().deleteTrack(trackId)

        assertTrue(db.trackCoordinatesDao().getCoordinatesOfTrack(trackId).first().isEmpty())
    }

    @Test
    fun deleteTrackCoordinates_clearsOnlyThatTrack() = runBlocking<Unit> {
        // Used when a track is re-recorded: the points go, the track itself stays.
        val kept = db.trackMainDao().insertTrackMainDataDAO(track("Kept"))
        val cleared = db.trackMainDao().insertTrackMainDataDAO(track("Cleared"))
        db.trackCoordinatesDao().insertTrack(straightTrack(kept, count = 4))
        db.trackCoordinatesDao().insertTrack(straightTrack(cleared, count = 4))

        db.trackCoordinatesDao().deleteTrackCoordinates(cleared)

        assertTrue(db.trackCoordinatesDao().getCoordinatesOfTrack(cleared).first().isEmpty())
        assertEquals(4, db.trackCoordinatesDao().getCoordinatesOfTrack(kept).first().size)
        assertEquals("Cleared", db.trackMainDao().getTrack(cleared).first().trackName)
    }

    @Test
    fun trackNames_listEveryTrack_forSeederDedup() = runBlocking<Unit> {
        // TrackSeeder skips any bundled track whose name is already present, so this list
        // being complete is what stops the premade tracks duplicating on every launch.
        db.trackMainDao().insertTrackMainDataDAO(track("Pannonia-ring"))
        db.trackMainDao().insertTrackMainDataDAO(track("Hungaroring"))

        assertEquals(
            setOf("Pannonia-ring", "Hungaroring"),
            db.trackMainDao().getAllTrackNames().toSet()
        )
    }

    @Test
    fun allTracks_areSortedByName() = runBlocking<Unit> {
        db.trackMainDao().insertTrackMainDataDAO(track("Mugello"))
        db.trackMainDao().insertTrackMainDataDAO(track("Euro-ring"))
        db.trackMainDao().insertTrackMainDataDAO(track("Paul Ricard"))

        assertEquals(
            listOf("Euro-ring", "Mugello", "Paul Ricard"),
            db.trackMainDao().getAllTrack().first().map { it.trackName }
        )
    }

    @Test
    fun updateTotalLength_storesKilometres() = runBlocking<Unit> {
        // Length is stored in km at the source (seeded tracks and recorded ones alike).
        val trackId = db.trackMainDao().insertTrackMainDataDAO(track())

        db.trackMainDao().updateTotalLength(trackId, 5.242)

        assertEquals(5.242, db.trackMainDao().getTrack(trackId).first().totalLength!!, 1e-9)
    }
}
