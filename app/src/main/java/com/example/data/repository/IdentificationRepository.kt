package com.example.data.repository

import com.example.data.local.dao.IdentificationDao
import com.example.data.local.entity.PlantIdentification
import kotlinx.coroutines.flow.Flow

class IdentificationRepository(private val identificationDao: IdentificationDao) {

    fun getIdentificationsForObservation(observationId: String): Flow<List<PlantIdentification>> =
        identificationDao.getIdentificationsForObservation(observationId)

    suspend fun saveIdentifications(identifications: List<PlantIdentification>) =
        identificationDao.insertIdentifications(identifications)

    suspend fun markConfirmed(observationId: String, plantId: String) =
        identificationDao.markConfirmed(observationId, plantId)
}
