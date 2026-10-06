package com.example.ui.observation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Plant
import com.example.ui.components.PlantThumbnail
import com.example.util.PlantVisualMatcher
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationFlowScreen(
    flowState: ObservationFlowState,
    allPlants: List<PlantWithDetails>,
    onConfirmCandidate: (PlantVisualMatcher.CandidateMatch) -> Unit,
    onSelectPlantManually: (Plant) -> Unit,
    onProceedDespiteQuality: () -> Unit,
    onRetakePhoto: () -> Unit,
    onFinish: () -> Unit,
    onShareToTelegram: (obsId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualPlantSelectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Plant Observation") },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (val step = flowState.step) {
                is ObservationStep.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ObservationStep.QualityCheck -> {
                    QualityWarningCard(
                        imagePath = step.imagePath,
                        warningMessage = step.result.message,
                        onRetake = onRetakePhoto,
                        onProceed = onProceedDespiteQuality
                    )
                }

                is ObservationStep.Identification -> {
                    IdentificationStepCard(
                        imagePath = step.imagePath,
                        candidates = step.candidates,
                        bestCandidate = step.bestCandidate,
                        hasMultiple = step.hasMultipleCandidates,
                        onConfirm = onConfirmCandidate,
                        onManualSelect = { showManualPlantSelectDialog = true },
                        onRetake = onRetakePhoto
                    )
                }

                is ObservationStep.Analyzing -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.analyzing_with_gemini),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Matching visual state for ${step.plant.name}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ObservationStep.Completed -> {
                    ObservationResultCard(
                        plant = step.plant,
                        observation = step.observation,
                        result = step.result,
                        onDone = onFinish,
                        onTelegramShare = { onShareToTelegram(step.observation.id) }
                    )
                }
            }
        }
    }

    if (showManualPlantSelectDialog) {
        AlertDialog(
            onDismissRequest = { showManualPlantSelectDialog = false },
            title = { Text(stringResource(R.string.select_manually)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(allPlants) { item ->
                        val plant = item.plant
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showManualPlantSelectDialog = false
                                    onSelectPlantManually(plant)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlantThumbnail(imagePath = item.referenceImages.firstOrNull()?.imagePath, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(plant.name, fontWeight = FontWeight.Bold)
                                Text(plant.id, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showManualPlantSelectDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun QualityWarningCard(
    imagePath: String,
    warningMessage: String,
    onRetake: () -> Unit,
    onProceed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚠️ Image Quality Warning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.low_quality_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetake,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.retake_photo))
                }
                OutlinedButton(
                    onClick = onProceed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Proceed Anyway")
                }
            }
        }
    }
}

@Composable
fun IdentificationStepCard(
    imagePath: String,
    candidates: List<PlantVisualMatcher.CandidateMatch>,
    bestCandidate: PlantVisualMatcher.CandidateMatch?,
    hasMultiple: Boolean,
    onConfirm: (PlantVisualMatcher.CandidateMatch) -> Unit,
    onManualSelect: () -> Unit,
    onRetake: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(File(imagePath)),
                    contentDescription = "Captured Observation",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (hasMultiple) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        text = stringResource(R.string.multiple_plants_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (bestCandidate != null && bestCandidate.confidence >= 0.60f) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (bestCandidate.confidence >= 0.90f) "Identified Match (High Confidence)" else "Likely Plant Candidate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Plant: ${bestCandidate.plant.name} [${bestCandidate.plant.id}]",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Confidence: ${(bestCandidate.confidence * 100).toInt()}% • ${bestCandidate.scoreBreakdown}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onConfirm(bestCandidate) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confirm_plant_candidate_button")
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                            OutlinedButton(
                                onClick = onManualSelect,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.select_manually))
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Plant not identified with sufficient confidence.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onManualSelect, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.select_manually))
                            }
                            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.retake_photo))
                            }
                        }
                    }
                }
            }
        }

        // Other Candidates List
        if (candidates.size > 1) {
            item {
                Text(
                    text = stringResource(R.string.candidate_matching),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(candidates.drop(1)) { match ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfirm(match) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(match.plant.name, fontWeight = FontWeight.Bold)
                            Text("ID: ${match.plant.id}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "${(match.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ObservationResultCard(
    plant: Plant,
    observation: com.example.data.local.entity.Observation,
    result: com.example.data.ai.GeminiAnalysisResult?,
    onDone: () -> Unit,
    onTelegramShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(File(observation.imagePath)),
                    contentDescription = "Observation Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Observation Saved for ${plant.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: ${observation.overallStatus} (${observation.aiStatus})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (result != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.summary),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (result.visualChanges.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.visual_changes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            result.visualChanges.forEach { change ->
                                Text("• $change", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (result.visibleIssues.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.visible_issues),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            result.visibleIssues.forEach { issue ->
                                Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (result.recommendedObservations.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.recommended_observations),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            result.recommendedObservations.forEach { rec ->
                                Text("• $rec", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
                OutlinedButton(
                    onClick = onTelegramShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Telegram")
                }
            }
        }
    }
}
