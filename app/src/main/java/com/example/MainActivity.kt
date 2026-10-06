package com.example

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import java.util.*

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply saved locale (fa/en)
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

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Camera capture launcher for daily check
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isInObservationFlow = true
            observationViewModel.onImageCaptured(bitmap)
        }
    }

    // Photo picker launcher for daily check (gallery fallback)
    val galleryPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isInObservationFlow = true
            observationViewModel.onImageSelectedFromGallery(uri)
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
                takePictureLauncher.launch(null)
            },
            onFinish = {
                isInObservationFlow = false
                observationViewModel.reset()
            },
            onShareToTelegram = { obsId ->
                coroutineScope.launch {
                    val obs = plantDetailState.observations.find { it.id == obsId }
                        ?: homeState.recentObservations.find { it.id == obsId }
                    if (obs != null) {
                        val caption = "🌱 PlantTrack AI\nPlant: ${obs.plantId}\nStatus: ${obs.overallStatus}\nSummary: ${obs.summary}"
                        val res = PlantTrackApplication.instance.telegramService.sendObservationPhoto(
                            botToken = settingsState.telegramBotToken,
                            chatId = settingsState.telegramChatId,
                            imagePath = obs.imagePath,
                            captionText = caption
                        )
                        if (res.isSuccess) {
                            Toast.makeText(context, "Sent to Telegram!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Telegram error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
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
                            takePictureLauncher.launch(null)
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
                            // View details
                        }
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
}
