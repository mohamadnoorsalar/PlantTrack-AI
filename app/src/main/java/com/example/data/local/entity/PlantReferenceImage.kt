package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plant_reference_images",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["plantId"])]
)
data class PlantReferenceImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val plantId: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPrimary: Boolean = false,
    val angleLabel: String = "" // e.g. "Overall", "Close-up", "Side view"
)
