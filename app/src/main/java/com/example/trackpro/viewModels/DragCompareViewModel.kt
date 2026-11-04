package com.example.trackpro.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.calculationClasses.DragCarInput
import com.example.trackpro.managerClasses.calculationClasses.DragCarSummary
import com.example.trackpro.managerClasses.calculationClasses.DragCompare
import com.example.trackpro.managerClasses.calculationClasses.DragCompareScale
import com.example.trackpro.managerClasses.calculationClasses.DragRunAnalyzer
import com.example.trackpro.managerClasses.calculationClasses.DragRunProfile
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
import java.util.concurrent.ConcurrentHashMap

/** A car that has drag runs, as the picker offers it. */
data class CompareCar(val vehicleId: Long, val name: String, val runCount: Int)

/**
 * Drag runs compared car against car.
 *
 * The user picks cars; every non-voided run of each picked car is read from its GPS trace,
 * analysed once and cached, and the summaries are rebuilt whenever the pick, the unit or the
 * number of people in the car changes. Only the last of those changes is ever calculated.
 */
class DragCompareViewModel(private val database: ESPDatabase) : ViewModel() {

    private val sessionsByCar = MutableStateFlow<Map<Long, List<DragSessionWithVehicle>>>(emptyMap())

    private val _cars = MutableStateFlow<List<CompareCar>>(emptyList())
    val cars = _cars.asStateFlow()

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState = _loadState.asStateFlow()

    /** In the order they were picked: the first one is what the others are measured against. */
    private val _selected = MutableStateFlow<List<Long>>(emptyList())
    val selected = _selected.asStateFlow()

    private val _occupants = MutableStateFlow(1)
    val occupants = _occupants.asStateFlow()

    private val metric = MutableStateFlow(true)

    private val _summaries = MutableStateFlow<List<DragCarSummary>>(emptyList())
    val summaries = _summaries.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    /** Finished runs only: a run still being recorded would be cached half-read. */
    private val profiles = ConcurrentHashMap<Long, DragRunProfile>()
    private val withoutPull = ConcurrentHashMap.newKeySet<Long>()

    private class Inputs(
        val sessions: Map<Long, List<DragSessionWithVehicle>>,
        val selected: List<Long>,
        val occupants: Int,
        val metric: Boolean
    )

    init {
        viewModelScope.launch {
            try {
                database.sessionDataDao().getAllDragSessionsWithVehicles().collect { all ->
                    val byCar = all.filter { !it.voided }.groupBy { it.vehicleId }
                    sessionsByCar.value = byCar
                    _cars.value = byCar.map { (id, runs) ->
                        CompareCar(id, "${runs.first().manufacturer} ${runs.first().model}".trim(), runs.size)
                    }.sortedBy { it.name }
                    _selected.update { picked -> picked.filter { it in byCar } }
                    _loadState.value = LoadState.Ready
                }
            } catch (e: Exception) {
                _loadState.value = LoadState.Failed("Could not read drag sessions")
            }
        }
        viewModelScope.launch {
            combine(sessionsByCar, _selected, _occupants, metric) { sessions, picked, people, isMetric ->
                Inputs(sessions, picked, people, isMetric)
            }.collectLatest { summarize(it) }
        }
    }

    fun toggle(vehicleId: Long) {
        _selected.update { picked ->
            when {
                vehicleId in picked -> picked - vehicleId
                picked.size >= MAX_CARS -> picked
                else -> picked + vehicleId
            }
        }
    }

    fun setOccupants(people: Int) {
        _occupants.value = people.coerceIn(1, MAX_OCCUPANTS)
    }

    fun setMetric(isMetric: Boolean) {
        metric.value = isMetric
    }

    private suspend fun summarize(inputs: Inputs) {
        val picked = inputs.selected.filter { it in inputs.sessions }
        if (picked.isEmpty()) {
            _summaries.value = emptyList()
            _working.value = false
            return
        }
        _working.value = true
        val scale = DragCompareScale.of(inputs.metric)
        val result = withContext(Dispatchers.Default) {
            val vehicles = database.vehicleInformationDAO().getAllVehicles().first().associateBy { it.vehicleId }
            picked.map { id ->
                val sessions = inputs.sessions.getValue(id)
                val vehicle = vehicles[id]
                DragCompare.summarize(
                    DragCarInput(
                        vehicleId = id,
                        name = "${sessions.first().manufacturer} ${sessions.first().model}".trim(),
                        weightKg = vehicle?.weight ?: 0.0,
                        horsepower = vehicle?.horsepower ?: 0,
                        runs = sessions.mapNotNull { profileOf(it) }
                    ),
                    scale,
                    inputs.occupants
                )
            }
        }
        _summaries.value = result
        _working.value = false
    }

    private suspend fun profileOf(session: DragSessionWithVehicle): DragRunProfile? {
        profiles[session.sessionId]?.let { return it }
        if (session.sessionId in withoutPull) return null
        val samples = database.rawGPSDataDao().getGPSDataBySession(session.sessionId)
        val profile = DragRunAnalyzer.analyze(session.sessionId, samples)
        if (session.endTime != null) {
            if (profile != null) profiles[session.sessionId] = profile else withoutPull += session.sessionId
        }
        return profile
    }

    companion object {
        const val MAX_CARS = 4
        const val MAX_OCCUPANTS = 5
    }
}

class DragCompareViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DragCompareViewModel(ESPDatabase.getInstance(context.applicationContext)) as T
    }
}
