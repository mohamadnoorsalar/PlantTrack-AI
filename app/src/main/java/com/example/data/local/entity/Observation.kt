package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observations",
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
data class Observation(
    @PrimaryKey
    val id: String, // e.g. "OBS-1728123456789"
    val plantId: String,
    val imagePath: String,
    val thumbnailPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aiStatus: String = "PENDING", // PENDING, ANALYZING, COMPLETED, FAILED
    val overallStatus: String = "Normal", // Healthy, Attention Needed, Vigorous, Normal, etc.
    val summary: String = "",
    val visualChanges: String = "[]", // JSON array of strings
    val visibleIssues: String = "[]", // JSON array of strings
    val possibleCauses: String = "[]", // JSON array of strings
    val recommendedObservations: String = "[]", // JSON array of strings
    val confidence: Float = 0.0f,
    val userNote: String = "",
    val telegramStatus: String = "NOT_SENT" // NOT_SENT, SENT, FAILED
)
