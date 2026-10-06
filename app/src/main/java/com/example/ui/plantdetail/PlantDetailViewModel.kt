package com.example.ui.plantdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Observation
import com.example.data.local.entity.Plant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlantDetailUiState(
    val plantWithDetails: PlantWithDetails? = null,
    val observations: List<Observation> = emptyList(),
    val isLoading: Boolean = true
)

class PlantDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val plantRepo = app.plantRepository
    private val obsRepo = app.observationRepository

    private val _plantId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlantDetailUiState> = _plantId.filterNotNull().flatMapLatest { id ->
        combine(
            plantRepo.getPlantFlowById(id),
            plantRepo.getReferenceImages(id),
            obsRepo.getObservationsForPlant(id)
        ) { plant, refs, observations ->
            if (plant != null) {
                PlantDetailUiState(
                    plantWithDetails = PlantWithDetails(plant, refs),
                    observations = observations,
                    isLoading = false
                )
            } else {
                PlantDetailUiState(isLoading = false)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlantDetailUiState())

    fun setPlantId(id: String) {
        _plantId.value = id
    }

    fun updatePlant(name: String, species: String, notes: String, locationLabel: String) {
        val current = uiState.value.plantWithDetails?.plant ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = name,
                species = species,
                notes = notes,
                locationLabel = locationLabel,
                updatedAt = System.currentTimeMillis()
            )
            plantRepo.updatePlant(updated)
        }
    }

    fun toggleArchive() {
        val current = uiState.value.plantWithDetails?.plant ?: return
        viewModelScope.launch {
            plantRepo.setPlantArchived(current.id, !current.isArchived)
        }
    }

    fun deletePlant(onDeleted: () -> Unit) {
        val id = _plantId.value ?: return
        viewModelScope.launch {
            plantRepo.deletePlant(id)
            onDeleted()
        }
    }
}
