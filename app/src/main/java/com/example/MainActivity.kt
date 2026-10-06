package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.garden.GardenScreen
import com.example.ui.garden.GardenViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.navigation.Screen
import com.example.ui.observation.ObservationFlowScreen
import com.example.ui.observation.ObservationViewModel
import com.example.ui.plantdetail.PlantDetailScreen
import com.example.ui.plantdetail.PlantDetailViewModel
import com.example.ui.plants.AddPlantDialog
import com.example.ui.plants.PlantsScreen
import com.example.ui.plants.PlantListViewModel
import com.example.ui.reports.ReportsScreen
import com.example.ui.reports.ReportsViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "fa") ?: "fa"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

            val isDark = when (settingsState.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(settingsViewModel = settingsVm)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val homeViewModel: HomeViewModel = viewModel()
    val plantListViewModel: PlantListViewModel = viewModel()
    val gardenViewModel: GardenViewModel = viewModel()
    val reportsViewModel: ReportsViewModel = viewModel()
    val observationViewModel: ObservationViewModel = viewModel()
    val plantDetailViewModel: PlantDetailViewModel = viewModel()

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val plantListState by plantListViewModel.uiState.collectAsStateWithLifecycle()
    val gardenState by gardenViewModel.uiState.collectAsStateWithLifecycle()
    val reportsState by reportsViewModel.uiState.collectAsStateWithLifecycle()
    val observationFlowState by observationViewModel.flowState.collectAsStateWithLifecycle()
    val plantDetailState by plantDetailViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var showAddPlantDialog by remember { mutableStateOf(false) }
    var isInObservationFlow by remember { mutableStateOf(false) }
    var showSourceSelectionDialog by remember { mutableStateOf(false) }

    // Telegram preview state
    var pendingTelegramPostText by remember { mutableStateOf<String?>(null) }
    var pendingTelegramImagePath by remember { mutableStateOf<String?>(null) }

    // Observation Detail screen state
    var selectedObservationForDetail by remember { mutableStateOf<com.example.data.local.entity.Observation?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // File-based high-res camera launcher (robust across all devices)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = tempPhotoUri
        if (success && uri != null) {
            isInObservationFlow = true
            observationViewModel.onImageSelectedFromGallery(uri)
        }
    }

    // Photo picker launcher (Gallery)
    val galleryPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isInObservationFlow = true
            observationViewModel.onImageSelectedFromGallery(uri)
        }
    }

    // Camera runtime permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCapture(
                context = context,
                onUriCreated = { tempPhotoUri = it },
                onLaunch = { uri ->
                    try {
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT).show()
                        galleryPickLauncher.launch("image/*")
                    }
                }
            )
        } else {
            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
            galleryPickLauncher.launch("image/*")
        }
    }

    fun startDailyCheckCameraFlow() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCameraCapture(
                context = context,
                onUriCreated = { tempPhotoUri = it },
                onLaunch = { uri ->
                    try {
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT).show()
                        galleryPickLauncher.launch("image/*")
                    }
                }
            )
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Plants,
        Screen.Garden,
        Screen.Reports,
        Screen.Settings
    )

    if (isInObservationFlow) {
        ObservationFlowScreen(
            flowState = observationFlowState,
            allPlants = plantListState.plants,
            onConfirmCandidate = { candidate ->
                observationViewModel.confirmCandidate(candidate)
            },
            onSelectPlantManually = { plant ->
                observationViewModel.selectPlantManually(plant)
            },
            onProceedDespiteQuality = {
                observationViewModel.proceedDespiteQualityWarning()
            },
            onRetakePhoto = {
                observationViewModel.reset()
                startDailyCheckCameraFlow()
            },
            onFinish = {
                isInObservationFlow = false
                observationViewModel.reset()
            },
            onShareToTelegram = { obsId ->
                val obs = plantDetailState.observations.find { it.id == obsId }
                    ?: homeState.recentObservations.find { it.id == obsId }
                if (obs != null) {
                    val plant = plantListState.plants.find { it.plant.id == obs.plantId }?.plant
                        ?: plantDetailState.plantWithDetails?.plant
                    val pName = plant?.name ?: obs.plantId
                    val postText = com.example.data.telegram.TelegramMessageBuilder.buildObservationPost(
                        plantName = pName,
                        plantId = obs.plantId,
                        observation = obs
                    )
                    pendingTelegramPostText = postText
                    pendingTelegramImagePath = obs.imagePath
                }
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Screen.Home.route || currentRoute == Screen.Plants.route) {
                FloatingActionButton(
                    onClick = { showAddPlantDialog = true },
                    modifier = Modifier.testTag("fab_add_plant")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_plant))
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        uiState = homeState,
                        onDailyCheckClick = {
                            showSourceSelectionDialog = true
                        },
                        onAddPlantClick = { showAddPlantDialog = true },
                        onPlantClick = { plantId ->
                            plantDetailViewModel.setPlantId(plantId)
                            navController.navigate("plant_detail")
                        },
                        onPendingAnalysisClick = {
                            navController.navigate(Screen.Reports.route)
                        }
                    )
                }

                composable(Screen.Plants.route) {
                    PlantsScreen(
                        uiState = plantListState,
                        onSearchChange = { plantListViewModel.onSearchQueryChanged(it) },
                        onFilterChange = { plantListViewModel.onFilterModeChanged(it) },
                        onPlantClick = { plantId ->
                            plantDetailViewModel.setPlantId(plantId)
                            navController.navigate("plant_detail")
                        },
                        onAddPlantClick = { showAddPlantDialog = true }
                    )
                }

                composable("plant_detail") {
                    PlantDetailScreen(
                        uiState = plantDetailState,
                        onBackClick = { navController.popBackStack() },
                        onEditSave = { name, species, notes, loc ->
                            plantDetailViewModel.updatePlant(name, species, notes, loc)
                        },
                        onToggleArchive = { plantDetailViewModel.toggleArchive() },
                        onDeletePlant = {
                            plantDetailViewModel.deletePlant {
                                navController.popBackStack()
                            }
                        },
                        onObservationClick = { obs ->
                            selectedObservationForDetail = obs
                            navController.navigate("observation_detail")
                        },
                        onRetryAnalysis = { obs ->
                            plantDetailViewModel.retryObservationAnalysis(obs)
                            Toast.makeText(context, "در حال ارسال مجدد به هوش مصنوعی...", Toast.LENGTH_SHORT).show()
                        },
                        onCompareClick = {
                            navController.navigate("compare_screen")
                        }
                    )
                }

                composable("observation_detail") {
                    val obs = selectedObservationForDetail
                    val plantName = plantDetailState.plantWithDetails?.plant?.name ?: "گیاه"
                    if (obs != null) {
                        com.example.ui.observation.ObservationDetailScreen(
                            observation = obs,
                            plantName = plantName,
                            onBackClick = { navController.popBackStack() },
                            onRetryAnalysis = { targetObs ->
                                plantDetailViewModel.retryObservationAnalysis(targetObs)
                                Toast.makeText(context, "در حال تلاش مجدد برای تحلیل تصویر...", Toast.LENGTH_SHORT).show()
                            },
                            onShareToTelegram = { targetObs ->
                                val postText = com.example.data.telegram.TelegramMessageBuilder.buildObservationPost(
                                    plantName = plantName,
                                    plantId = targetObs.plantId,
                                    observation = targetObs
                                )
                                pendingTelegramPostText = postText
                                pendingTelegramImagePath = targetObs.imagePath
                            }
                        )
                    }
                }

                composable("compare_screen") {
                    com.example.ui.compare.CompareScreen(
                        observations = plantDetailState.observations,
                        plantName = plantDetailState.plantWithDetails?.plant?.name ?: "گیاه",
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.Garden.route) {
                    GardenScreen(
                        uiState = gardenState,
                        onMapSelected = { gardenViewModel.selectMap(it) },
                        onPlantPositionChanged = { id, x, y ->
                            gardenViewModel.updatePlantPosition(id, x, y)
                        },
                        onPlantClick = { plantId ->
                            plantDetailViewModel.setPlantId(plantId)
                            navController.navigate("plant_detail")
                        },
                        onCreateMap = { name -> gardenViewModel.createMap(name) }
                    )
                }

                composable(Screen.Reports.route) {
                    ReportsScreen(
                        uiState = reportsState,
                        onSelectPlant = { plant -> reportsViewModel.selectPlant(plant) },
                        onExportPdf = { reportsViewModel.exportPdf() },
                        onExportJson = { reportsViewModel.exportJson() },
                        onDismissMessage = { reportsViewModel.clearStatusMessage() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        uiState = settingsState,
                        onLanguageChange = { lang ->
                            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                .edit().putString("app_language", lang).apply()
                            settingsViewModel.setLanguage(lang)
                            Toast.makeText(context, "Restart app to change system language", Toast.LENGTH_SHORT).show()
                        },
                        onThemeChange = { theme -> settingsViewModel.setTheme(theme) },
                        onSaveGeminiKey = { key -> settingsViewModel.saveGeminiApiKey(key) },
                        onTestGemini = { settingsViewModel.testGeminiConnection() },
                        onSaveTelegram = { tok, chat -> settingsViewModel.saveTelegramConfig(tok, chat) },
                        onTestTelegram = { settingsViewModel.testTelegramConnection() },
                        onCreateBackup = { settingsViewModel.createBackup() },
                        onRestoreBackup = { file -> settingsViewModel.restoreBackup(file) },
                        onClearCache = { settingsViewModel.clearCache() },
                        onDismissMessage = { settingsViewModel.clearOperationMessage() }
                    )
                }
            }
        }
    }

    if (showSourceSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showSourceSelectionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.choose_input_method),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSourceSelectionDialog = false
                                startDailyCheckCameraFlow()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.camera_capture),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSourceSelectionDialog = false
                                galleryPickLauncher.launch("image/*")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.gallery_pick),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourceSelectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddPlantDialog) {
        AddPlantDialog(
            onDismiss = { showAddPlantDialog = false },
            onSave = { name, species, notes, loc, x, y, uris ->
                plantListViewModel.addNewPlant(
                    name = name,
                    species = species,
                    notes = notes,
                    locationLabel = loc,
                    relativeX = x,
                    relativeY = y,
                    referenceUris = uris,
                    onSuccess = {
                        showAddPlantDialog = false
                    }
                )
            }
        )
    }

    pendingTelegramPostText?.let { initialText ->
        val imgPath = pendingTelegramImagePath ?: ""
        com.example.ui.telegram.TelegramPreviewDialog(
            initialPostText = initialText,
            imagePath = imgPath,
            onDismiss = {
                pendingTelegramPostText = null
                pendingTelegramImagePath = null
            },
            onSend = { editedText ->
                pendingTelegramPostText = null
                pendingTelegramImagePath = null
                coroutineScope.launch {
                    val res = PlantTrackApplication.instance.telegramService.sendObservationPhoto(
                        botToken = settingsState.telegramBotToken,
                        chatId = settingsState.telegramChatId,
                        imagePath = imgPath,
                        captionText = editedText,
                        context = context
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, "گزارش با موفقیت به تلگرام ارسال شد!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خطای تلگرام: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

private fun launchCameraCapture(
    context: Context,
    onUriCreated: (Uri) -> Unit,
    onLaunch: (Uri) -> Unit
) {
    try {
        val cacheDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val photoFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        onUriCreated(uri)
        onLaunch(uri)
    } catch (e: Exception) {
        Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
