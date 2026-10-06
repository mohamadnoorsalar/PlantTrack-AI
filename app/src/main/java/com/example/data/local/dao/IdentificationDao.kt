package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.PlantIdentification
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentificationDao {
    @Query("SELECT * FROM plant_identifications WHERE observationId = :observationId ORDER BY confidence DESC")
    fun getIdentificationsForObservation(observationId: String): Flow<List<PlantIdentification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentifications(identifications: List<PlantIdentification>)

    @Query("UPDATE plant_identifications SET isConfirmed = 1 WHERE observationId = :observationId AND candidatePlantId = :plantId")
    suspend fun markConfirmed(observationId: String, plantId: String)
}
