package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*

@Database(
    entities = [
        Plant::class,
        PlantReferenceImage::class,
        Observation::class,
        PlantIdentification::class,
        GardenMap::class,
        AppSetting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun observationDao(): ObservationDao
    abstract fun identificationDao(): IdentificationDao
    abstract fun gardenMapDao(): GardenMapDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plant_track_database"
                )
                    // In release/future version migrations will be added here
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
