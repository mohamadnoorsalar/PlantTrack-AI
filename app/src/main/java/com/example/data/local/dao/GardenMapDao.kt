package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.GardenMap
import kotlinx.coroutines.flow.Flow

@Dao
interface GardenMapDao {
    @Query("SELECT * FROM garden_maps ORDER BY id ASC")
    fun getAllMaps(): Flow<List<GardenMap>>

    @Query("SELECT * FROM garden_maps WHERE id = :id LIMIT 1")
    suspend fun getMapById(id: Long): GardenMap?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: GardenMap): Long

    @Update
    suspend fun updateMap(map: GardenMap)

    @Delete
    suspend fun deleteMap(map: GardenMap)
}
