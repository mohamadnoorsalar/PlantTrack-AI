package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_identifications")
data class PlantIdentification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val observationId: String,
    val candidatePlantId: String,
    val confidence: Float,
    val isConfirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
