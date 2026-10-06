package com.example

import android.app.Application
import com.example.data.ai.GeminiPlantService
import com.example.data.local.database.AppDatabase
import com.example.data.repository.*
import com.example.data.telegram.TelegramService

class PlantTrackApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val plantRepository by lazy { PlantRepository(database.plantDao()) }
    val observationRepository by lazy { ObservationRepository(database.observationDao()) }
    val identificationRepository by lazy { IdentificationRepository(database.identificationDao()) }
    val gardenRepository by lazy { GardenRepository(database.gardenMapDao()) }
    val settingRepository by lazy { SettingRepository(database.settingDao()) }

    val geminiService by lazy { GeminiPlantService() }
    val telegramService by lazy { TelegramService() }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PlantTrackApplication
            private set
    }
}
