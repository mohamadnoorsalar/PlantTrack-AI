package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Observation
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val activePlantsCount: Int = 0,
    val todayChecksCount: Int = 0,
    val pendingAnalysisCount: Int = 0,
    val totalReportsCount: Int = 0,
    val recentObservations: List<Observation> = emptyList(),
    val plantsWithDetails: List<PlantWithDetails> = emptyList(),
    val plantObservationCounts: Map<String, Int> = emptyMap(),
    val plantLatestObservations: Map<String, Observation?> = emptyMap(),
    val isLoading: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val plantRepo = app.plantRepository
    private val obsRepo = app.observationRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val countsFlow = combine(
            plantRepo.activeCount,
            obsRepo.getTodayObservationsCount(),
            obsRepo.pendingCount
        ) { active, today, pending ->
            Triple(active, today, pending)
        }

        val dataFlow = combine(
            obsRepo.getRecentObservations(6),
            plantRepo.allPlantsWithDetails,
            obsRepo.allObservations
        ) { recentObs, plants, allObs ->
            Triple(recentObs, plants, allObs)
        }

        combine(countsFlow, dataFlow) { counts, data ->
            val (activeCount, todayCount, pendingCount) = counts
            val (recentObs, plants, allObs) = data

            val obsCountMap = allObs.groupBy { it.plantId }.mapValues { it.value.size }
            val latestObsMap = allObs.groupBy { it.plantId }.mapValues { entry ->
                entry.value.maxByOrNull { it.createdAt }
            }

            HomeUiState(
                activePlantsCount = activeCount,
                todayChecksCount = todayCount,
                pendingAnalysisCount = pendingCount,
                totalReportsCount = allObs.size,
                recentObservations = recentObs,
                plantsWithDetails = plants,
                plantObservationCounts = obsCountMap,
                plantLatestObservations = latestObsMap,
                isLoading = false
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }
}
