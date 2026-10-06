package com.example.ui.garden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.GardenMap
import com.example.data.local.entity.Plant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GardenUiState(
    val maps: List<GardenMap> = emptyList(),
    val currentMap: GardenMap? = null,
    val plantsWithDetails: List<PlantWithDetails> = emptyList(),
    val selectedPlant: Plant? = null,
    val isLoading: Boolean = false
)

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val gardenRepo = app.gardenRepository
    private val plantRepo = app.plantRepository

    private val _selectedMapId = MutableStateFlow<Long?>(null)
    private val _selectedPlant = MutableStateFlow<Plant?>(null)

    val uiState: StateFlow<GardenUiState> = combine(
        gardenRepo.allMaps,
        _selectedMapId,
        plantRepo.allPlantsWithDetails,
        _selectedPlant
    ) { maps, selectedId, plants, selectedPlant ->
        val current = maps.find { it.id == selectedId } ?: maps.firstOrNull()
        GardenUiState(
            maps = maps,
            currentMap = current,
            plantsWithDetails = plants.filter { !it.plant.isArchived },
            selectedPlant = selectedPlant,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GardenUiState())

    init {
        // Create default garden map if empty
        viewModelScope.launch {
            gardenRepo.allMaps.first().let { list ->
                if (list.isEmpty()) {
                    gardenRepo.insertMap(
                        GardenMap(
                            name = "Main Garden / Bed A",
                            width = 100f,
                            height = 100f,
                            notes = "Primary outdoor planting area"
                        )
                    )
                }
            }
        }
    }

    fun selectMap(mapId: Long) {
        _selectedMapId.value = mapId
    }

    fun selectPlant(plant: Plant?) {
        _selectedPlant.value = plant
    }

    fun updatePlantPosition(plantId: String, newX: Float, newY: Float) {
        viewModelScope.launch {
            val plant = plantRepo.getPlantById(plantId) ?: return@launch
            val updated = plant.copy(
                relativeX = newX.coerceIn(0.05f, 0.95f),
                relativeY = newY.coerceIn(0.05f, 0.95f),
                updatedAt = System.currentTimeMillis()
            )
            plantRepo.updatePlant(updated)
        }
    }

    fun createMap(name: String, notes: String = "") {
        viewModelScope.launch {
            val id = gardenRepo.insertMap(
                GardenMap(
                    name = name.ifBlank { "Garden Bed" },
                    notes = notes
                )
            )
            _selectedMapId.value = id
        }
    }
}
