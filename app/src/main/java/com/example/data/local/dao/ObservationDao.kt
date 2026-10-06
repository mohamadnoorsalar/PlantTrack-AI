package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.Observation
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Query("SELECT * FROM observations ORDER BY createdAt DESC")
    fun getAllObservations(): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE plantId = :plantId ORDER BY createdAt DESC")
    fun getObservationsForPlant(plantId: String): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE plantId = :plantId ORDER BY createdAt DESC")
    suspend fun getObservationsForPlantSync(plantId: String): List<Observation>

    @Query("SELECT * FROM observations WHERE id = :id LIMIT 1")
    suspend fun getObservationById(id: String): Observation?

    @Query("SELECT * FROM observations WHERE id = :id LIMIT 1")
    fun getObservationFlowById(id: String): Flow<Observation?>

    @Query("SELECT * FROM observations WHERE aiStatus = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingObservations(): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE aiStatus = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingObservationsSync(): List<Observation>

    @Query("SELECT COUNT(*) FROM observations WHERE createdAt >= :startOfDayTimestamp")
    fun getTodayObservationsCount(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM observations WHERE aiStatus = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM observations ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentObservations(limit: Int = 5): Flow<List<Observation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: Observation)

    @Update
    suspend fun updateObservation(observation: Observation)

    @Delete
    suspend fun deleteObservation(observation: Observation)

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteObservationById(id: String)
}
