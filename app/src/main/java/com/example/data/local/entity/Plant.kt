package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey
    val id: String, // e.g. "PLANT-0001"
    val name: String,
    val species: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isArchived: Boolean = false,
    val gardenMapId: Long? = null,
    val relativeX: Float? = null, // 0.0f .. 1.0f
    val relativeY: Float? = null, // 0.0f .. 1.0f
    val locationLabel: String = ""
)
