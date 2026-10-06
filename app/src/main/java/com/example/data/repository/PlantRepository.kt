package com.example.data.repository

import com.example.data.local.dao.PlantDao
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantReferenceImage
import kotlinx.coroutines.flow.Flow

class PlantRepository(private val plantDao: PlantDao) {

    val allPlants: Flow<List<Plant>> = plantDao.getAllPlants()
    val activePlants: Flow<List<Plant>> = plantDao.getActivePlants()
    val archivedPlants: Flow<List<Plant>> = plantDao.getArchivedPlants()
    val allPlantsWithDetails: Flow<List<PlantWithDetails>> = plantDao.getAllPlantsWithDetails()
    val activeCount: Flow<Int> = plantDao.getActivePlantsCount()

    suspend fun getPlantById(id: String): Plant? = plantDao.getPlantById(id)
    fun getPlantFlowById(id: String): Flow<Plant?> = plantDao.getPlantFlowById(id)

    suspend fun getPlantWithDetails(id: String): PlantWithDetails? = plantDao.getPlantWithDetails(id)

    fun getReferenceImages(plantId: String): Flow<List<PlantReferenceImage>> =
        plantDao.getReferenceImages(plantId)

    suspend fun getReferenceImagesSync(plantId: String): List<PlantReferenceImage> =
        plantDao.getReferenceImagesSync(plantId)

    suspend fun generateNextPlantId(): String {
        val maxId = plantDao.getMaxPlantId() ?: return "PLANT-0001"
        val numberPart = maxId.removePrefix("PLANT-").toIntOrNull() ?: 0
        val nextNumber = numberPart + 1
        return "PLANT-%04d".format(nextNumber)
    }

    suspend fun insertPlant(plant: Plant) = plantDao.insertPlant(plant)

    suspend fun updatePlant(plant: Plant) = plantDao.updatePlant(plant)

    suspend fun deletePlant(plantId: String) {
        plantDao.deletePlantById(plantId)
    }

    suspend fun insertReferenceImage(image: PlantReferenceImage): Long =
        plantDao.insertReferenceImage(image)

    suspend fun insertReferenceImages(images: List<PlantReferenceImage>) =
        plantDao.insertReferenceImages(images)

    suspend fun deleteReferenceImage(id: Long) = plantDao.deleteReferenceImage(id)

    suspend fun setPlantArchived(plantId: String, archived: Boolean) {
        val current = plantDao.getPlantById(plantId) ?: return
        plantDao.updatePlant(current.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
    }
}
