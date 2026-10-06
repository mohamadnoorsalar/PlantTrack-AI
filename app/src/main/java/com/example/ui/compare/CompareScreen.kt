package com.example.ui.compare

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
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
import com.example.data.local.entity.Observation
import com.example.ui.components.PlantThumbnail
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    observations: List<Observation>,
    plantName: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var beforeObservation by remember(observations) {
        mutableStateOf(observations.getOrNull(observations.size - 1) ?: observations.firstOrNull())
    }
    var afterObservation by remember(observations) {
        mutableStateOf(observations.firstOrNull())
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${stringResource(R.string.compare)}: $plantName") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (observations.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "حداقل دو بررسی برای مقایسه تصاویر نیاز است.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Side by Side Visual Comparison Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("قبل (BEFORE)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("بعد (AFTER)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Before Image
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(200.dp)
                            ) {
                                if (beforeObservation != null && File(beforeObservation!!.imagePath).exists()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(File(beforeObservation!!.imagePath)),
                                        contentDescription = "Before Observation",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("بدون تصویر")
                                    }
                                }
                            }

                            // After Image
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(200.dp)
                            ) {
                                if (afterObservation != null && File(afterObservation!!.imagePath).exists()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(File(afterObservation!!.imagePath)),
                                        contentDescription = "After Observation",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("بدون تصویر")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = beforeObservation?.let { dateFormat.format(Date(it.createdAt)) } ?: "-",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = afterObservation?.let { dateFormat.format(Date(it.createdAt)) } ?: "-",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Timeline Selector for Before
            item {
                Text("انتخاب بررسی مبدأ (قبل):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(observations) { obs ->
                        val isSelected = beforeObservation?.id == obs.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { beforeObservation = obs },
                            label = { Text(dateFormat.format(Date(obs.createdAt))) }
                        )
                    }
                }
            }

            // Timeline Selector for After
            item {
                Text("انتخاب بررسی مقصد (بعد):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(observations) { obs ->
                        val isSelected = afterObservation?.id == obs.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { afterObservation = obs },
                            label = { Text(dateFormat.format(Date(obs.createdAt))) }
                        )
                    }
                }
            }

            // Visual Status Analysis
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("خلاصه تغییرات و وضعیت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "وضعیت قبلی: ${beforeObservation?.overallStatus ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "وضعیت جدید: ${afterObservation?.overallStatus ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!afterObservation?.summary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "یادداشت هوش مصنوعی: ${afterObservation?.summary}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
