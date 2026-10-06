package com.example.data.repository

import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.AppSetting
import kotlinx.coroutines.flow.Flow

class SettingRepository(private val settingDao: SettingDao) {

    suspend fun get(key: String): String? = settingDao.getValue(key)
    fun getFlow(key: String): Flow<String?> = settingDao.getValueFlow(key)

    suspend fun set(key: String, value: String) {
        settingDao.set(AppSetting(key, value))
    }

    suspend fun delete(key: String) = settingDao.delete(key)
}
