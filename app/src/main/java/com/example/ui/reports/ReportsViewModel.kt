package com.example.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.export.JsonDataExporter
import com.example.data.export.PdfReportGenerator
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Observation
import com.example.data.local.entity.Plant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ReportsUiState(
    val plantsWithDetails: List<PlantWithDetails> = emptyList(),
    val selectedPlant: Plant? = null,
    val selectedPlantObservations: List<Observation> = emptyList(),
    val generatedPdfFile: File? = null,
    val generatedJsonFile: File? = null,
    val isExporting: Boolean = false,
    val statusMessage: String? = null
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val plantRepo = app.plantRepository
    private val obsRepo = app.observationRepository

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            plantRepo.allPlantsWithDetails.collect { list ->
                val currentSelected = _uiState.value.selectedPlant ?: list.firstOrNull()?.plant
                _uiState.value = _uiState.value.copy(
                    plantsWithDetails = list,
                    selectedPlant = currentSelected
                )
                if (currentSelected != null) {
                    loadPlantObservations(currentSelected.id)
                }
            }
        }
    }

    fun selectPlant(plant: Plant) {
        _uiState.value = _uiState.value.copy(selectedPlant = plant)
        loadPlantObservations(plant.id)
    }

    private fun loadPlantObservations(plantId: String) {
        viewModelScope.launch {
            val obs = obsRepo.getObservationsForPlantSync(plantId)
            _uiState.value = _uiState.value.copy(selectedPlantObservations = obs)
        }
    }

    fun exportPdf() {
        val plant = _uiState.value.selectedPlant ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, statusMessage = "Generating PDF...")
            val refs = plantRepo.getReferenceImagesSync(plant.id)
            val observations = _uiState.value.selectedPlantObservations
            val result = PdfReportGenerator.generatePlantReport(app, plant, refs, observations)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    generatedPdfFile = result.getOrNull(),
                    statusMessage = "PDF Generated: ${result.getOrNull()?.name}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    statusMessage = "PDF Error: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun exportJson() {
        val plant = _uiState.value.selectedPlant ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, statusMessage = "Exporting JSON...")
            val observations = _uiState.value.selectedPlantObservations
            val result = JsonDataExporter.exportPlantToJson(app, plant, observations)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    generatedJsonFile = result.getOrNull(),
                    statusMessage = "JSON Exported: ${result.getOrNull()?.name}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    statusMessage = "JSON Error: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}
