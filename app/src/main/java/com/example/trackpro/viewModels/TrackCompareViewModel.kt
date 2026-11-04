package com.example.trackpro.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.calculationClasses.GeoPoint
import com.example.trackpro.managerClasses.calculationClasses.TrackCarInput
import com.example.trackpro.managerClasses.calculationClasses.TrackCompare
import com.example.trackpro.managerClasses.calculationClasses.TrackComparison
import com.example.trackpro.managerClasses.calculationClasses.TrackLap
import com.example.trackpro.managerClasses.calculationClasses.TrackLapExtractor
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import com.example.trackpro.managerClasses.utilities.toLapTimeMillisOrNull
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.models.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** A track that has been driven, as the picker offers it. */
data class CompareTrack(val trackId: Long, val name: String, val circuit: Boolean, val sessions: Int)

/**
 * Something to put in the comparison: a car (all its good laps on the track, the best of them
 * shown) or one single lap or run.
 */
data class CompareEntry(val key: String, val label: String, val detail: String, val isCar: Boolean)

sealed interface TrackCompareState {
    /** Nothing picked yet. */
    data object Idle : TrackCompareState

    data object Working : TrackCompareState

    /** Something is picked, but no lap of the first pick could serve as the line to compare to. */
    data object NoLaps : TrackCompareState

    class Ready(val comparison: TrackComparison, val entries: List<CompareEntry>) : TrackCompareState
}

/**
 * Laps and runs on one track, car against car (or lap against lap).
 *
 * A lap row's fixes are read and cut only when it is first needed and then kept, since a car
 * can have dozens of laps on a track and each is a few thousand points.
 */
class TrackCompareViewModel(private val database: ESPDatabase) : ViewModel() {

    private class LapRef(
        val lapId: Long,
        val vehicleId: Long,
        val carName: String,
        val lapMs: Long,
        val startTime: Long
    )

    private class Request(val trackId: Long?, val keys: List<String>)

    private val sessionsByTrack = MutableStateFlow<Map<Long, List<DragSessionWithVehicle>>>(emptyMap())

    private val _tracks = MutableStateFlow<List<CompareTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState = _loadState.asStateFlow()

    private val _selectedTrack = MutableStateFlow<Long?>(null)
    val selectedTrack = _selectedTrack.asStateFlow()

    private val _entries = MutableStateFlow<List<CompareEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    /** In the order they were picked: the first is the reference. */
    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected = _selected.asStateFlow()

    private val _state = MutableStateFlow<TrackCompareState>(TrackCompareState.Idle)
    val state = _state.asStateFlow()

    private var lapRefs: List<LapRef> = emptyList()
    private val laps = ConcurrentHashMap<Long, TrackLap>()
    private val unusable = ConcurrentHashMap.newKeySet<Long>()
    private val request = MutableStateFlow(Request(null, emptyList()))

    init {
        viewModelScope.launch {
            try {
                combine(
                    database.sessionDataDao().getAllTrackSessionsWithVehicles(),
                    database.trackMainDao().getAllTrack()
                ) { sessions, tracks -> sessions.filter { !it.voided } to tracks }
                    .collect { (sessions, tracks) ->
                        val byTrack = sessions.filter { it.trackId != null }.groupBy { it.trackId!! }
                        sessionsByTrack.value = byTrack
                        _tracks.value = tracks.filter { it.trackId in byTrack }.map {
                            CompareTrack(it.trackId, it.trackName, it.type.equals("Circuit", ignoreCase = true), byTrack.getValue(it.trackId).size)
                        }
                        _loadState.value = LoadState.Ready
                    }
            } catch (e: Exception) {
                _loadState.value = LoadState.Failed("Could not read track sessions")
            }
        }
        viewModelScope.launch {
            request.collectLatest { compute(it) }
        }
    }

    fun selectTrack(trackId: Long) {
        if (trackId == _selectedTrack.value) return
        _selectedTrack.value = trackId
        _selected.value = emptyList()
        _entries.value = emptyList()
        laps.clear()
        unusable.clear()
        request.value = Request(trackId, emptyList())
        viewModelScope.launch { loadEntries(trackId) }
    }

    fun toggle(key: String) {
        _selected.update { picked ->
            when {
                key in picked -> picked - key
                picked.size >= MAX_PICKS -> picked
                else -> picked + key
            }
        }
        request.value = Request(_selectedTrack.value, _selected.value)
    }

    private suspend fun loadEntries(trackId: Long) {
        val refs = withContext(Dispatchers.IO) {
            sessionsByTrack.value[trackId].orEmpty().flatMap { session ->
                database.lapTimeDataDAO().getLapsForSession(session.sessionId)
                    .filter { !it.signalGap }
                    .mapNotNull { lap ->
                        val ms = lap.laptime.toLapTimeMillisOrNull() ?: return@mapNotNull null
                        LapRef(lap.id, session.vehicleId, "${session.manufacturer} ${session.model}".trim(), ms, session.startTime)
                    }
            }
        }
        if (_selectedTrack.value != trackId) return
        lapRefs = refs

        val dates = DateFormatterUtil.getDateFormat()
        val cars = refs.groupBy { it.vehicleId }.map { (_, list) ->
            val best = list.minOf { it.lapMs }
            CompareEntry("car:${list.first().vehicleId}", list.first().carName, "${best.toLapTimeString()} · ${list.size}", isCar = true) to best
        }.sortedBy { it.second }.map { it.first }
        val singles = refs.sortedBy { it.lapMs }.take(MAX_SINGLE_ENTRIES).map {
            CompareEntry("lap:${it.lapId}", it.carName, "${dates.format(Date(it.startTime))} · ${it.lapMs.toLapTimeString()}", isCar = false)
        }
        _entries.value = cars + singles
    }

    private suspend fun compute(req: Request) {
        val trackId = req.trackId
        if (trackId == null || req.keys.isEmpty()) {
            _state.value = TrackCompareState.Idle
            return
        }
        val track = _tracks.value.firstOrNull { it.trackId == trackId } ?: return
        _state.value = TrackCompareState.Working

        val coords = withContext(Dispatchers.IO) { database.trackCoordinatesDao().getCoordinatesOfTrack(trackId).first() }
        val finishLine = if (track.circuit) TrackGeometry.calculateFinishLine(coords) else null
        val gates = gatePoints(coords)
        val picked = req.keys.mapNotNull { key -> _entries.value.firstOrNull { it.key == key } }

        val inputs = picked.map { entry ->
            val refs = if (entry.isCar) {
                val vehicleId = entry.key.removePrefix("car:").toLong()
                lapRefs.filter { it.vehicleId == vehicleId }.sortedBy { it.lapMs }.take(MAX_LAPS_PER_CAR)
            } else {
                lapRefs.filter { it.lapId == entry.key.removePrefix("lap:").toLong() }
            }
            // A single lap is told apart by its time; the date it was driven on is in the picker.
            val label = if (entry.isCar) entry.label else "${entry.label} · ${entry.detail.substringAfterLast(" · ")}"
            TrackCarInput(0L, label, refs.mapNotNull { lapOf(it, track.circuit, finishLine) })
        }

        val comparison = withContext(Dispatchers.Default) {
            TrackCompare.compare(inputs, gates, if (track.circuit) 3 else 4)
        }
        _state.value = comparison?.let { TrackCompareState.Ready(it, picked) } ?: TrackCompareState.NoLaps
    }

    private fun gatePoints(coords: List<TrackCoordinatesData>): List<GeoPoint> =
        coords.filter { it.isSectorPoint }
            .sortedBy { it.sectorIndex ?: Int.MAX_VALUE }
            .map { GeoPoint(it.latitude, it.longitude) }

    private suspend fun lapOf(ref: LapRef, circuit: Boolean, finishLine: List<TrackCoordinatesData>?): TrackLap? {
        laps[ref.lapId]?.let { return it }
        if (ref.lapId in unusable) return null
        val lap = withContext(Dispatchers.IO) {
            TrackLapExtractor.extract(database.lapInfoDataDAO().getLapData(ref.lapId), ref.lapId, ref.lapMs, finishLine, circuit)
        }
        if (lap != null) laps[ref.lapId] = lap else unusable += ref.lapId
        return lap
    }

    companion object {
        const val MAX_PICKS = 4
        private const val MAX_LAPS_PER_CAR = 15
        private const val MAX_SINGLE_ENTRIES = 20
    }
}

class TrackCompareViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TrackCompareViewModel(ESPDatabase.getInstance(context.applicationContext)) as T
    }
}
