package com.example.data.export

import android.content.Context
import com.example.data.local.dao.PlantWithDetails
import com.example.data.local.entity.Observation
import com.example.data.local.entity.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object JsonDataExporter {

    suspend fun exportPlantToJson(
        context: Context,
        plant: Plant,
        observations: List<Observation>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("app", "PlantTrack AI")
                put("version", "1.0")
                put("exportedAt", System.currentTimeMillis())

                put("plant", JSONObject().apply {
                    put("id", plant.id)
                    put("name", plant.name)
                    put("species", plant.species)
                    put("createdAt", plant.createdAt)
                    put("notes", plant.notes)
                    put("relativeX", plant.relativeX)
                    put("relativeY", plant.relativeY)
                    put("locationLabel", plant.locationLabel)
                })

                val obsArray = JSONArray()
                for (o in observations) {
                    obsArray.put(JSONObject().apply {
                        put("id", o.id)
                        put("createdAt", o.createdAt)
                        put("aiStatus", o.aiStatus)
                        put("overallStatus", o.overallStatus)
                        put("summary", o.summary)
                        put("visualChanges", o.visualChanges)
                        put("visibleIssues", o.visibleIssues)
                        put("possibleCauses", o.possibleCauses)
                        put("recommendedObservations", o.recommendedObservations)
                        put("confidence", o.confidence)
                        put("userNote", o.userNote)
                    })
                }
                put("observations", obsArray)
            }

            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "Export_${plant.id}_$timeStamp.json")
            FileOutputStream(file).use {
                it.write(root.toString(2).toByteArray())
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportAllPlantsToJson(
        context: Context,
        plantsWithDetails: List<PlantWithDetails>,
        observationsMap: Map<String, List<Observation>>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("app", "PlantTrack AI")
                put("version", "1.0")
                put("exportedAt", System.currentTimeMillis())

                val plantsArray = JSONArray()
                for (item in plantsWithDetails) {
                    val p = item.plant
                    val plantObj = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("species", p.species)
                        put("createdAt", p.createdAt)
                        put("notes", p.notes)
                        put("isArchived", p.isArchived)
                        put("relativeX", p.relativeX)
                        put("relativeY", p.relativeY)
                        put("locationLabel", p.locationLabel)

                        val refArray = JSONArray()
                        for (r in item.referenceImages) {
                            refArray.put(JSONObject().apply {
                                put("id", r.id)
                                put("isPrimary", r.isPrimary)
                                put("createdAt", r.createdAt)
                            })
                        }
                        put("referenceImagesCount", refArray.length())

                        val obsList = observationsMap[p.id] ?: emptyList()
                        val obsArray = JSONArray()
                        for (o in obsList) {
                            obsArray.put(JSONObject().apply {
                                put("id", o.id)
                                put("createdAt", o.createdAt)
                                put("overallStatus", o.overallStatus)
                                put("summary", o.summary)
                                put("visualChanges", o.visualChanges)
                                put("visibleIssues", o.visibleIssues)
                                put("confidence", o.confidence)
                                put("userNote", o.userNote)
                            })
                        }
                        put("observations", obsArray)
                    }
                    plantsArray.put(plantObj)
                }
                put("plants", plantsArray)
            }

            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "FullBackup_Plants_$timeStamp.json")
            FileOutputStream(file).use {
                it.write(root.toString(2).toByteArray())
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
