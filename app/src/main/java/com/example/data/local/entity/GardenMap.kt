package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "garden_maps")
data class GardenMap(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val width: Float = 100f,
    val height: Float = 100f,
    val backgroundImagePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)
