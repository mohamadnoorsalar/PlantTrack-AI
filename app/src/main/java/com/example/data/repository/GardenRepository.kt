package com.example.data.repository

import com.example.data.local.dao.GardenMapDao
import com.example.data.local.entity.GardenMap
import kotlinx.coroutines.flow.Flow

class GardenRepository(private val gardenMapDao: GardenMapDao) {

    val allMaps: Flow<List<GardenMap>> = gardenMapDao.getAllMaps()

    suspend fun getMapById(id: Long): GardenMap? = gardenMapDao.getMapById(id)

    suspend fun insertMap(map: GardenMap): Long = gardenMapDao.insertMap(map)

    suspend fun updateMap(map: GardenMap) = gardenMapDao.updateMap(map)

    suspend fun deleteMap(map: GardenMap) = gardenMapDao.deleteMap(map)
}
