package com.example.data.backup

import android.content.Context
import com.example.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    suspend fun createBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val backupsDir = File(context.filesDir, "backups").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(backupsDir, "PlantTrack_Backup_$timeStamp.zip")

            // Checkpoint database to flush WAL
            val db = AppDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // 1. Write metadata JSON
                val metadata = JSONObject().apply {
                    put("backupVersion", 1)
                    put("appVersion", "1.0")
                    put("createdAt", System.currentTimeMillis())
                    put("databaseName", "plant_track_database")
                }
                val metaEntry = ZipEntry("backup_metadata.json")
                zos.putNextEntry(metaEntry)
                zos.write(metadata.toString().toByteArray())
                zos.closeEntry()

                // 2. Add database files
                val dbFile = context.getDatabasePath("plant_track_database")
                if (dbFile.exists()) {
                    addFileToZip(dbFile, "database/plant_track_database", zos)
                }

                // 3. Add plant images
                val imagesDir = File(context.filesDir, "plant_images")
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    imagesDir.listFiles()?.forEach { img ->
                        if (img.isFile) {
                            addFileToZip(img, "plant_images/${img.name}", zos)
                        }
                    }
                }

                // 4. Add plant thumbs
                val thumbsDir = File(context.filesDir, "plant_thumbs")
                if (thumbsDir.exists() && thumbsDir.isDirectory) {
                    thumbsDir.listFiles()?.forEach { thumb ->
                        if (thumb.isFile) {
                            addFileToZip(thumb, "plant_thumbs/${thumb.name}", zos)
                        }
                    }
                }
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addFileToZip(file: File, entryPath: String, zos: ZipOutputStream) {
        val entry = ZipEntry(entryPath)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    suspend fun restoreBackup(context: Context, backupZipFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!backupZipFile.exists()) {
                return@withContext Result.failure(FileNotFoundException("Backup file does not exist"))
            }

            // Verify backup structure
            var isValidMetadata = false
            ZipInputStream(BufferedInputStream(FileInputStream(backupZipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup_metadata.json") {
                        val bytes = zis.readBytes()
                        val json = JSONObject(String(bytes))
                        val version = json.optInt("backupVersion", -1)
                        if (version >= 1) {
                            isValidMetadata = true
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }

            if (!isValidMetadata) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or corrupted backup archive"))
            }

            // Close existing database instance
            val db = AppDatabase.getDatabase(context)
            db.close()

            // Extract contents
            ZipInputStream(BufferedInputStream(FileInputStream(backupZipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        entryName.startsWith("database/plant_track_database") -> {
                            val dbTarget = context.getDatabasePath("plant_track_database")
                            dbTarget.parentFile?.mkdirs()
                            FileOutputStream(dbTarget).use { fos -> zis.copyTo(fos) }
                        }
                        entryName.startsWith("plant_images/") -> {
                            val targetFile = File(context.filesDir, entryName)
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                        }
                        entryName.startsWith("plant_thumbs/") -> {
                            val targetFile = File(context.filesDir, entryName)
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            Result.success("Restoration completed successfully.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
