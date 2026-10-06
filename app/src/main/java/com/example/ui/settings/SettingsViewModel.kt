package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlantTrackApplication
import com.example.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val language: String = "fa", // "fa" or "en"
    val themeMode: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val geminiApiKey: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val notificationsEnabled: Boolean = true,
    val storageUsageSummary: String = "Calculating...",
    val operationMessage: String? = null,
    val isBackingUp: Boolean = false,
    val lastBackupFile: File? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PlantTrackApplication
    private val settingRepo = app.settingRepository
    private val telegramService = app.telegramService

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        calculateStorage()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val lang = settingRepo.get("app_language") ?: "fa"
            val theme = settingRepo.get("app_theme") ?: "SYSTEM"
            val geminiKey = settingRepo.get("gemini_api_key") ?: ""
            val tgToken = settingRepo.get("telegram_bot_token") ?: ""
            val tgChat = settingRepo.get("telegram_chat_id") ?: ""
            val notif = settingRepo.get("notifications_enabled")?.toBoolean() ?: true

            _uiState.value = _uiState.value.copy(
                language = lang,
                themeMode = theme,
                geminiApiKey = geminiKey,
                telegramBotToken = tgToken,
                telegramChatId = tgChat,
                notificationsEnabled = notif
            )
        }
    }

    private fun calculateStorage() {
        viewModelScope.launch {
            var totalBytes = 0L
            val filesDir = app.filesDir
            filesDir.walkTopDown().forEach { file ->
                if (file.isFile) totalBytes += file.length()
            }
            val mb = totalBytes / (1024f * 1024f)
            _uiState.value = _uiState.value.copy(
                storageUsageSummary = "Total Local Storage: %.2f MB".format(mb)
            )
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            settingRepo.set("app_language", lang)
            _uiState.value = _uiState.value.copy(language = lang)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingRepo.set("app_theme", theme)
            _uiState.value = _uiState.value.copy(themeMode = theme)
        }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch {
            settingRepo.set("gemini_api_key", key.trim())
            _uiState.value = _uiState.value.copy(
                geminiApiKey = key.trim(),
                operationMessage = "کلید هوش مصنوعی با موفقیت ذخیره شد."
            )
        }
    }

    fun testGeminiConnection() {
        val key = _uiState.value.geminiApiKey
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(operationMessage = "در حال تست اتصال به Gemini AI...")
            val result = app.geminiService.testConnection(key.ifBlank { null })
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(operationMessage = "✓ ${result.getOrNull()}")
            } else {
                _uiState.value = _uiState.value.copy(operationMessage = "✗ ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun saveTelegramConfig(token: String, chatId: String) {
        viewModelScope.launch {
            settingRepo.set("telegram_bot_token", token.trim())
            settingRepo.set("telegram_chat_id", chatId.trim())
            _uiState.value = _uiState.value.copy(
                telegramBotToken = token.trim(),
                telegramChatId = chatId.trim(),
                operationMessage = "Telegram settings saved."
            )
        }
    }

    fun testTelegramConnection() {
        val token = _uiState.value.telegramBotToken
        val chat = _uiState.value.telegramChatId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(operationMessage = "Testing Telegram connection...")
            val result = telegramService.testConnection(token, chat)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(operationMessage = "✓ ${result.getOrNull()}")
            } else {
                _uiState.value = _uiState.value.copy(operationMessage = "✗ Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackingUp = true, operationMessage = "Creating backup .zip...")
            val res = BackupManager.createBackup(app)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    lastBackupFile = res.getOrNull(),
                    operationMessage = "✓ Full backup created: ${res.getOrNull()?.name}"
                )
                calculateStorage()
            } else {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    operationMessage = "✗ Backup failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun restoreBackup(backupFile: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(operationMessage = "Restoring data...")
            val res = BackupManager.restoreBackup(app, backupFile)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(operationMessage = "✓ Backup restored successfully.")
                calculateStorage()
            } else {
                _uiState.value = _uiState.value.copy(operationMessage = "✗ Restore failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            app.cacheDir.deleteRecursively()
            calculateStorage()
            _uiState.value = _uiState.value.copy(operationMessage = "Temporary cache cleared.")
        }
    }

    fun clearOperationMessage() {
        _uiState.value = _uiState.value.copy(operationMessage = null)
    }
}
