package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantReferenceImage
import kotlinx.coroutines.flow.Flow

data class PlantWithDetails(
    @Embedded val plant: Plant,
    @Relation(
        parentColumn = "id",
        entityColumn = "plantId"
    )
    val referenceImages: List<PlantReferenceImage>
)

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY createdAt DESC")
    fun getAllPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getActivePlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    suspend fun getPlantById(id: String): Plant?

    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    fun getPlantFlowById(id: String): Flow<Plant?>

    @Transaction
    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    suspend fun getPlantWithDetails(id: String): PlantWithDetails?

    @Transaction
    @Query("SELECT * FROM plants WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllPlantsWithDetails(): Flow<List<PlantWithDetails>>

    @Query("SELECT COUNT(*) FROM plants WHERE isArchived = 0")
    fun getActivePlantsCount(): Flow<Int>

    @Query("SELECT MAX(id) FROM plants")
    suspend fun getMaxPlantId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlant(plant: Plant)

    @Update
    suspend fun updatePlant(plant: Plant)

    @Delete
    suspend fun deletePlant(plant: Plant)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun deletePlantById(id: String)

    // Reference images
    @Query("SELECT * FROM plant_reference_images WHERE plantId = :plantId ORDER BY isPrimary DESC, createdAt ASC")
    fun getReferenceImages(plantId: String): Flow<List<PlantReferenceImage>>

    @Query("SELECT * FROM plant_reference_images WHERE plantId = :plantId ORDER BY isPrimary DESC, createdAt ASC")
    suspend fun getReferenceImagesSync(plantId: String): List<PlantReferenceImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferenceImage(image: PlantReferenceImage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferenceImages(images: List<PlantReferenceImage>)

    @Query("DELETE FROM plant_reference_images WHERE id = :id")
    suspend fun deleteReferenceImage(id: Long)

    @Query("DELETE FROM plant_reference_images WHERE plantId = :plantId")
    suspend fun deleteReferenceImagesByPlantId(plantId: String)
}
