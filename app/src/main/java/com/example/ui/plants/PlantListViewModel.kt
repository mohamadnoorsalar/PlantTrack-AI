package com.example.ui.plants

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantReferenceImage
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlantListUiState(
    val plants: List<PlantWithDetails> = emptyList(),
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ACTIVE,
    val isLoading: Boolean = false
)

enum class FilterMode {
    ALL, ACTIVE, ARCHIVED
}

class PlantListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val plantRepo = app.plantRepository

    private val _searchQuery = MutableStateFlow("")
    private val _filterMode = MutableStateFlow(FilterMode.ACTIVE)

    val uiState: StateFlow<PlantListUiState> = combine(
        plantRepo.allPlantsWithDetails,
        _searchQuery,
        _filterMode
    ) { allPlants, query, mode ->
        val filtered = allPlants.filter { item ->
            val matchesFilter = when (mode) {
                FilterMode.ALL -> true
                FilterMode.ACTIVE -> !item.plant.isArchived
                FilterMode.ARCHIVED -> item.plant.isArchived
            }
            val matchesSearch = query.isBlank() ||
                    item.plant.name.contains(query, ignoreCase = true) ||
                    item.plant.id.contains(query, ignoreCase = true) ||
                    item.plant.species.contains(query, ignoreCase = true) ||
                    item.plant.notes.contains(query, ignoreCase = true)

            matchesFilter && matchesSearch
        }

        PlantListUiState(
            plants = filtered,
            searchQuery = query,
            filterMode = mode,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlantListUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterModeChanged(mode: FilterMode) {
        _filterMode.value = mode
    }

    fun addNewPlant(
        name: String,
        species: String,
        notes: String,
        locationLabel: String,
        relativeX: Float?,
        relativeY: Float?,
        referenceUris: List<Uri>,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            val newId = plantRepo.generateNextPlantId()
            val plant = Plant(
                id = newId,
                name = name.ifBlank { "Plant $newId" },
                species = species,
                notes = notes,
                locationLabel = locationLabel,
                relativeX = relativeX,
                relativeY = relativeY,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            plantRepo.insertPlant(plant)

            // Save reference photos (1 to 5)
            val refImages = mutableListOf<PlantReferenceImage>()
            for ((index, uri) in referenceUris.take(5).withIndex()) {
                val (origPath, _) = ImageUtils.saveImageFromUri(app, uri, prefix = "REF_${newId}")
                refImages.add(
                    PlantReferenceImage(
                        plantId = newId,
                        imagePath = origPath,
                        isPrimary = index == 0,
                        angleLabel = when (index) {
                            0 -> "Overall View"
                            1 -> "Close-up"
                            2 -> "Side Angle"
                            else -> "Angle ${index + 1}"
                        }
                    )
                )
            }
            if (refImages.isNotEmpty()) {
                plantRepo.insertReferenceImages(refImages)
            }

            onSuccess(newId)
        }
    }
}
