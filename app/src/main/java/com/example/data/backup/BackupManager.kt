package com.example.data.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.MainActivity
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

    data class BackupResult(
        val file: File?,
        val displayPath: String
    )

    /**
     * Creates a complete application backup (.zip) containing metadata, database and plant images.
     * Saves directly into public Documents/PlantTrack AI directory accessible by device File Managers.
     */
    suspend fun createBackup(context: Context): Result<BackupResult> = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "PlantTrack_Backup_$timeStamp.zip"

            // Temporary file in app cache to build the zip cleanly
            val tempCacheZip = File(context.cacheDir, fileName)

            // Checkpoint database to flush WAL into main db file
            val db = AppDatabase.getDatabase(context)
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (_: Exception) {}

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempCacheZip))).use { zos ->
                // 1. Write metadata JSON
                val metadata = JSONObject().apply {
                    put("backupVersion", 1)
                    put("appVersion", "1.0")
                    put("createdAt", System.currentTimeMillis())
                    put("databaseName", AppDatabase.DATABASE_NAME)
                }
                val metaEntry = ZipEntry("backup_metadata.json")
                zos.putNextEntry(metaEntry)
                zos.write(metadata.toString().toByteArray())
                zos.closeEntry()

                // 2. Add database files
                val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                if (dbFile.exists()) {
                    addFileToZip(dbFile, "database/${AppDatabase.DATABASE_NAME}", zos)
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

            // Transfer to public Documents/PlantTrack AI directory
            var destinationFile: File? = null
            var displayLocation = "Documents/PlantTrack AI"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/PlantTrack AI")
                }

                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(tempCacheZip).use { fis ->
                            fis.copyTo(out)
                        }
                    }
                    displayLocation = "Documents/PlantTrack AI/$fileName"
                } else {
                    val publicDocsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "PlantTrack AI"
                    ).apply { mkdirs() }
                    destinationFile = File(publicDocsDir, fileName)
                    tempCacheZip.copyTo(destinationFile, overwrite = true)
                    displayLocation = destinationFile.absolutePath
                }
            } else {
                val publicDocsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "PlantTrack AI"
                ).apply { mkdirs() }
                destinationFile = File(publicDocsDir, fileName)
                tempCacheZip.copyTo(destinationFile, overwrite = true)
                displayLocation = destinationFile.absolutePath
            }

            // Keep internal copy in filesDir/backups for internal reference
            val internalBackupsDir = File(context.filesDir, "backups").apply { mkdirs() }
            val internalBackupCopy = File(internalBackupsDir, fileName)
            tempCacheZip.copyTo(internalBackupCopy, overwrite = true)
            tempCacheZip.delete()

            val finalFile = destinationFile ?: internalBackupCopy
            Result.success(BackupResult(file = finalFile, displayPath = displayLocation))
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

    /**
     * Completely restores the application database and images from the selected backup zip file.
     * Safely closes the database connection, purges -wal and -shm temporary files,
     * extracts database and images, and cleanly restarts the app so that the new database is loaded.
     */
    suspend fun restoreBackup(context: Context, backupZipFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!backupZipFile.exists()) {
                return@withContext Result.failure(FileNotFoundException("فایل پشتیبان یافت نشد"))
            }

            // 1. Verify backup structure & metadata
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
                return@withContext Result.failure(IllegalArgumentException("فایل پشتیبان نامعتبر است یا ساختار استانداردی ندارد"))
            }

            // 2. Safely close database connection and reset Room singleton
            AppDatabase.closeAndResetDatabase()

            // 3. Delete existing database and accompanying WAL / SHM files
            val dbTarget = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val dbWal = File(dbTarget.parentFile, "${AppDatabase.DATABASE_NAME}-wal")
            val dbShm = File(dbTarget.parentFile, "${AppDatabase.DATABASE_NAME}-shm")

            try { dbWal.delete() } catch (_: Exception) {}
            try { dbShm.delete() } catch (_: Exception) {}
            try { dbTarget.delete() } catch (_: Exception) {}

            dbTarget.parentFile?.mkdirs()

            // 4. Extract database and image contents
            val targetImagesDir = File(context.filesDir, "plant_images").apply { mkdirs() }
            val targetThumbsDir = File(context.filesDir, "plant_thumbs").apply { mkdirs() }

            ZipInputStream(BufferedInputStream(FileInputStream(backupZipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        entryName.startsWith("database/") -> {
                            FileOutputStream(dbTarget).use { fos -> zis.copyTo(fos) }
                        }
                        entryName.startsWith("plant_images/") && !entry.isDirectory -> {
                            val fileName = entryName.substringAfter("plant_images/")
                            if (fileName.isNotBlank()) {
                                val targetFile = File(targetImagesDir, fileName)
                                FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        entryName.startsWith("plant_thumbs/") && !entry.isDirectory -> {
                            val fileName = entryName.substringAfter("plant_thumbs/")
                            if (fileName.isNotBlank()) {
                                val targetFile = File(targetThumbsDir, fileName)
                                FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // Re-delete any stale -wal or -shm files if they were recreated
            try { dbWal.delete() } catch (_: Exception) {}
            try { dbShm.delete() } catch (_: Exception) {}

            Result.success("اطلاعات با موفقیت بازیابی شد.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleanly restarts the application to re-initialize Room database and ViewModels.
     */
    fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
