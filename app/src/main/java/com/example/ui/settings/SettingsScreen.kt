package com.example.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onTestGemini: () -> Unit = {},
    onSaveTelegram: (String, String) -> Unit,
    onTestTelegram: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (File) -> Unit,
    onClearCache: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var geminiKeyInput by remember(uiState.geminiApiKey) { mutableStateOf(uiState.geminiApiKey) }
    var tgTokenInput by remember(uiState.telegramBotToken) { mutableStateOf(uiState.telegramBotToken) }
    var tgChatInput by remember(uiState.telegramChatId) { mutableStateOf(uiState.telegramChatId) }

    // File picker for restoring zip
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val tempFile = File(context.cacheDir, "restore_temp.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            onRestoreBackup(tempFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language & Theme
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.language == "fa",
                                onClick = { onLanguageChange("fa") },
                                label = { Text(stringResource(R.string.persian)) }
                            )
                            FilterChip(
                                selected = uiState.language == "en",
                                onClick = { onLanguageChange("en") },
                                label = { Text(stringResource(R.string.english)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.theme_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.themeMode == "SYSTEM",
                                onClick = { onThemeChange("SYSTEM") },
                                label = { Text(stringResource(R.string.system_default)) }
                            )
                            FilterChip(
                                selected = uiState.themeMode == "LIGHT",
                                onClick = { onThemeChange("LIGHT") },
                                label = { Text(stringResource(R.string.light)) }
                            )
                            FilterChip(
                                selected = uiState.themeMode == "DARK",
                                onClick = { onThemeChange("DARK") },
                                label = { Text(stringResource(R.string.dark)) }
                            )
                        }
                    }
                }
            }

            // Gemini AI Configuration
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Gemini AI Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter optional custom API key or use secrets panel key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = { geminiKeyInput = it },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSaveGeminiKey(geminiKeyInput) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.save))
                            }
                            OutlinedButton(
                                onClick = onTestGemini,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.test_connection))
                            }
                        }
                    }
                }
            }

            // Telegram Bot Configuration
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.telegram),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tgTokenInput,
                            onValueChange = { tgTokenInput = it },
                            label = { Text(stringResource(R.string.telegram_bot_token)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tgChatInput,
                            onValueChange = { tgChatInput = it },
                            label = { Text(stringResource(R.string.telegram_chat_id)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSaveTelegram(tgTokenInput, tgChatInput) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.save))
                            }
                            OutlinedButton(
                                onClick = onTestTelegram,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.test_connection))
                            }
                        }
                    }
                }
            }

            // Storage & Backup / Restore
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.storage_manager),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.storageUsageSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onCreateBackup,
                                enabled = !uiState.isBackingUp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_backup_button")
                            ) {
                                Text(stringResource(R.string.backup_now))
                            }
                            OutlinedButton(
                                onClick = { restorePickerLauncher.launch("application/zip") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore .zip")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Temporary Cache")
                        }
                    }
                }
            }

            // Privacy & Security Info
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• All plant data, maps, and logs are stored locally on your device in Room DB.\n• Images are analyzed visually without transmitting personal data.\n• Telegram posts are sent only when explicitly requested.\n• No hardcoded secrets exist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.operationMessage != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.operationMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.lastBackupFile != null) {
                                IconButton(onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        uiState.lastBackupFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Backup File"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
