package com.example.ui.observation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.ai.GeminiAnalysisResult
import com.example.data.local.entity.Observation
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantIdentification
import com.example.util.ImageUtils
import com.example.util.PlantVisualMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

sealed class ObservationStep {
    object Idle : ObservationStep()
    data class QualityCheck(val imagePath: String, val result: ImageUtils.QualityCheckResult) : ObservationStep()
    data class Identification(
        val imagePath: String,
        val candidates: List<PlantVisualMatcher.CandidateMatch>,
        val bestCandidate: PlantVisualMatcher.CandidateMatch?,
        val hasMultipleCandidates: Boolean
    ) : ObservationStep()
    data class Analyzing(val plant: Plant, val imagePath: String) : ObservationStep()
    data class Completed(val plant: Plant, val observation: Observation, val result: GeminiAnalysisResult?) : ObservationStep()
}

data class ObservationFlowState(
    val step: ObservationStep = ObservationStep.Idle,
    val selectedPlant: Plant? = null,
    val capturedImagePath: String = "",
    val error: String? = null
)

class ObservationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val plantRepo = app.plantRepository
    private val obsRepo = app.observationRepository
    private val identRepo = app.identificationRepository
    private val geminiService = app.geminiService
    private val settingRepo = app.settingRepository

    private val _flowState = MutableStateFlow(ObservationFlowState())
    val flowState: StateFlow<ObservationFlowState> = _flowState.asStateFlow()

    fun onImageCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            val (origPath, thumbPath) = ImageUtils.saveBitmap(app, bitmap, prefix = "DAILY")
            processCapturedImagePath(origPath)
        }
    }

    fun onImageSelectedFromGallery(uri: Uri) {
        viewModelScope.launch {
            val (origPath, _) = ImageUtils.saveImageFromUri(app, uri, prefix = "DAILY")
            processCapturedImagePath(origPath)
        }
    }

    private suspend fun processCapturedImagePath(imagePath: String) {
        _flowState.value = _flowState.value.copy(capturedImagePath = imagePath, error = null)

        // 1. Image Quality Check
        val quality = ImageUtils.evaluateImageQuality(imagePath)
        if (!quality.isAcceptable) {
            _flowState.value = _flowState.value.copy(
                step = ObservationStep.QualityCheck(imagePath, quality)
            )
            return
        }

        // 2. Visual Identification Candidate Matching
        runVisualIdentification(imagePath)
    }

    fun proceedDespiteQualityWarning() {
        val path = _flowState.value.capturedImagePath
        viewModelScope.launch {
            runVisualIdentification(path)
        }
    }

    private suspend fun runVisualIdentification(imagePath: String) {
        val allPlantsWithDetails = plantRepo.allPlantsWithDetails.first()
        val pairs = allPlantsWithDetails.map { Pair(it.plant, it.referenceImages) }

        if (pairs.isEmpty()) {
            // No plants recorded yet
            _flowState.value = _flowState.value.copy(
                step = ObservationStep.Identification(
                    imagePath = imagePath,
                    candidates = emptyList(),
                    bestCandidate = null,
                    hasMultipleCandidates = false
                )
            )
            return
        }

        val candidates = PlantVisualMatcher.matchCandidates(
            newImagePath = imagePath,
            plantsWithReferences = pairs
        )

        val bestCandidate = candidates.firstOrNull()
        val hasMultipleCandidates = candidates.size > 1 &&
                candidates[0].confidence > 0.60f &&
                candidates[1].confidence > 0.55f

        _flowState.value = _flowState.value.copy(
            step = ObservationStep.Identification(
                imagePath = imagePath,
                candidates = candidates,
                bestCandidate = bestCandidate,
                hasMultipleCandidates = hasMultipleCandidates
            )
        )
    }

    fun confirmCandidate(candidate: PlantVisualMatcher.CandidateMatch) {
        _flowState.value = _flowState.value.copy(selectedPlant = candidate.plant)
        startAiAnalysis(candidate.plant, _flowState.value.capturedImagePath)
    }

    fun selectPlantManually(plant: Plant) {
        _flowState.value = _flowState.value.copy(selectedPlant = plant)
        startAiAnalysis(plant, _flowState.value.capturedImagePath)
    }

    private fun startAiAnalysis(plant: Plant, imagePath: String) {
        viewModelScope.launch {
            _flowState.value = _flowState.value.copy(
                step = ObservationStep.Analyzing(plant, imagePath)
            )

            val customApiKey = settingRepo.get("gemini_api_key")
            val obsId = "OBS-${System.currentTimeMillis()}"

            val analysisResult = geminiService.analyzePlantImage(
                imagePath = imagePath,
                plantName = plant.name,
                customApiKey = customApiKey
            )

            val observation = if (analysisResult.isSuccess) {
                val res = analysisResult.getOrThrow()
                Observation(
                    id = obsId,
                    plantId = plant.id,
                    imagePath = imagePath,
                    aiStatus = "COMPLETED",
                    overallStatus = res.overallStatus,
                    summary = res.summary,
                    visualChanges = JSONArray(res.visualChanges).toString(),
                    visibleIssues = JSONArray(res.visibleIssues).toString(),
                    possibleCauses = JSONArray(res.possibleCauses).toString(),
                    recommendedObservations = JSONArray(res.recommendedObservations).toString(),
                    confidence = res.confidence,
                    createdAt = System.currentTimeMillis()
                )
            } else {
                // Keep in pending or failed queue for offline / retry
                Observation(
                    id = obsId,
                    plantId = plant.id,
                    imagePath = imagePath,
                    aiStatus = "PENDING",
                    overallStatus = "Normal",
                    summary = "Waiting for Gemini analysis (${analysisResult.exceptionOrNull()?.message?.take(50)})",
                    confidence = 0f,
                    createdAt = System.currentTimeMillis()
                )
            }

            obsRepo.insertObservation(observation)

            // Save identification record
            identRepo.saveIdentifications(
                listOf(
                    PlantIdentification(
                        observationId = obsId,
                        candidatePlantId = plant.id,
                        confidence = 0.95f,
                        isConfirmed = true
                    )
                )
            )

            _flowState.value = _flowState.value.copy(
                step = ObservationStep.Completed(
                    plant = plant,
                    observation = observation,
                    result = analysisResult.getOrNull()
                )
            )
        }
    }

    fun reset() {
        _flowState.value = ObservationFlowState()
    }
}
