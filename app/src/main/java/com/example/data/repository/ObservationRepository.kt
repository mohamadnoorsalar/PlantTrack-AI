package com.example.data.repository

import com.example.data.local.dao.ObservationDao
import com.example.data.local.entity.Observation
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ObservationRepository(private val observationDao: ObservationDao) {

    val allObservations: Flow<List<Observation>> = observationDao.getAllObservations()
    val pendingObservations: Flow<List<Observation>> = observationDao.getPendingObservations()
    val pendingCount: Flow<Int> = observationDao.getPendingCount()

    fun getRecentObservations(limit: Int = 5): Flow<List<Observation>> =
        observationDao.getRecentObservations(limit)

    fun getObservationsForPlant(plantId: String): Flow<List<Observation>> =
        observationDao.getObservationsForPlant(plantId)

    suspend fun getObservationsForPlantSync(plantId: String): List<Observation> =
        observationDao.getObservationsForPlantSync(plantId)

    suspend fun getObservationById(id: String): Observation? =
        observationDao.getObservationById(id)

    fun getObservationFlowById(id: String): Flow<Observation?> =
        observationDao.getObservationFlowById(id)

    fun getTodayObservationsCount(): Flow<Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return observationDao.getTodayObservationsCount(cal.timeInMillis)
    }

    suspend fun getPendingObservationsSync(): List<Observation> =
        observationDao.getPendingObservationsSync()

    suspend fun insertObservation(observation: Observation) =
        observationDao.insertObservation(observation)

    suspend fun updateObservation(observation: Observation) =
        observationDao.updateObservation(observation)

    suspend fun deleteObservation(id: String) =
        observationDao.deleteObservationById(id)
}
