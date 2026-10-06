package com.example.ui.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.Plant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    uiState: GardenUiState,
    onMapSelected: (Long) -> Unit,
    onPlantPositionChanged: (plantId: String, x: Float, y: Float) -> Unit,
    onPlantClick: (String) -> Unit,
    onCreateMap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateMapDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garden_map)) },
                actions = {
                    IconButton(onClick = { showCreateMapDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Garden Bed")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Bed / Map Selector
            if (uiState.maps.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.maps.indexOfFirst { it.id == uiState.currentMap?.id }.coerceAtLeast(0),
                    edgePadding = 0.dp
                ) {
                    uiState.maps.forEachIndexed { index, map ->
                        Tab(
                            selected = uiState.currentMap?.id == map.id,
                            onClick = { onMapSelected(map.id) },
                            text = { Text(map.name) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Drag plant markers to reposition in this bed. Tap to inspect plant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Virtual Field Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE8F5E9), MaterialTheme.shapes.large)
                    .testTag("garden_map_canvas")
            ) {
                // Background soil grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 40.dp.toPx()
                    for (x in 0..(size.width / step).toInt()) {
                        drawLine(
                            color = Color(0xFFC8E6C9),
                            start = Offset(x * step, 0f),
                            end = Offset(x * step, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..(size.height / step).toInt()) {
                        drawLine(
                            color = Color(0xFFC8E6C9),
                            start = Offset(0f, y * step),
                            end = Offset(size.width, y * step),
                            strokeWidth = 1f
                        )
                    }
                }

                // Plant Markers
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxWidthPx = constraints.maxWidth.toFloat()
                    val maxHeightPx = constraints.maxHeight.toFloat()

                    for (item in uiState.plantsWithDetails) {
                        val plant = item.plant
                        val posX = (plant.relativeX ?: 0.5f) * maxWidthPx
                        val posY = (plant.relativeY ?: 0.5f) * maxHeightPx

                        PlantMarkerItem(
                            plant = plant,
                            xOffset = posX,
                            yOffset = posY,
                            onDrag = { newXPx, newYPx ->
                                val normX = (newXPx / maxWidthPx).coerceIn(0.05f, 0.95f)
                                val normY = (newYPx / maxHeightPx).coerceIn(0.05f, 0.95f)
                                onPlantPositionChanged(plant.id, normX, normY)
                            },
                            onClick = { onPlantClick(plant.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateMapDialog) {
        var mapName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateMapDialog = false },
            title = { Text("Add Garden Bed") },
            text = {
                OutlinedTextField(
                    value = mapName,
                    onValueChange = { mapName = it },
                    label = { Text("Bed Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mapName.isNotBlank()) {
                            onCreateMap(mapName)
                            showCreateMapDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateMapDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun PlantMarkerItem(
    plant: Plant,
    xOffset: Float,
    yOffset: Float,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    var currentX by remember(xOffset) { mutableFloatStateOf(xOffset) }
    var currentY by remember(yOffset) { mutableFloatStateOf(yOffset) }

    Box(
        modifier = Modifier
            .offset(x = (currentX / 2.7f).dp - 24.dp, y = (currentY / 2.7f).dp - 24.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDrag(currentX, currentY) }
                ) { change, dragAmount ->
                    change.consume()
                    currentX += dragAmount.x
                    currentY += dragAmount.y
                }
            }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = plant.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
