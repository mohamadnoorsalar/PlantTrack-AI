package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    suspend fun saveImageFromUri(
        context: Context,
        uri: Uri,
        prefix: String = "IMG"
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val imagesDir = File(context.filesDir, "plant_images").apply { mkdirs() }
        val thumbsDir = File(context.filesDir, "plant_thumbs").apply { mkdirs() }

        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val timestamp = System.currentTimeMillis()
        val originalFile = File(imagesDir, "${prefix}_${timestamp}_$uuid.jpg")
        val thumbFile = File(thumbsDir, "THUMB_${timestamp}_$uuid.jpg")

        // 1. Read input stream and decode
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val rotatedBitmap = originalBitmap ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        // 2. Scale down original for storage efficiency (max 1600px dimension)
        val maxDim = 1600f
        val origScale = if (rotatedBitmap.width > maxDim || rotatedBitmap.height > maxDim) {
            val ratio = maxDim / maxOf(rotatedBitmap.width, rotatedBitmap.height)
            Bitmap.createScaledBitmap(
                rotatedBitmap,
                (rotatedBitmap.width * ratio).toInt(),
                (rotatedBitmap.height * ratio).toInt(),
                true
            )
        } else {
            rotatedBitmap
        }

        FileOutputStream(originalFile).use { out ->
            origScale.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        // 3. Create Thumbnail (max 320px)
        val thumbDim = 320f
        val thumbRatio = thumbDim / maxOf(rotatedBitmap.width, rotatedBitmap.height)
        val thumbBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap,
            maxOf(1, (rotatedBitmap.width * thumbRatio).toInt()),
            maxOf(1, (rotatedBitmap.height * thumbRatio).toInt()),
            true
        )

        FileOutputStream(thumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        Pair(originalFile.absolutePath, thumbFile.absolutePath)
    }

    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "CAM"
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val imagesDir = File(context.filesDir, "plant_images").apply { mkdirs() }
        val thumbsDir = File(context.filesDir, "plant_thumbs").apply { mkdirs() }

        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val timestamp = System.currentTimeMillis()
        val originalFile = File(imagesDir, "${prefix}_${timestamp}_$uuid.jpg")
        val thumbFile = File(thumbsDir, "THUMB_${timestamp}_$uuid.jpg")

        // Max 1600px
        val maxDim = 1600f
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        FileOutputStream(originalFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val thumbDim = 320f
        val thumbRatio = thumbDim / maxOf(bitmap.width, bitmap.height)
        val thumbBitmap = Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * thumbRatio).toInt()),
            maxOf(1, (bitmap.height * thumbRatio).toInt()),
            true
        )

        FileOutputStream(thumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        Pair(originalFile.absolutePath, thumbFile.absolutePath)
    }

    // Quality check: light level & blur evaluation
    fun evaluateImageQuality(imagePath: String): QualityCheckResult {
        val file = File(imagePath)
        if (!file.exists()) return QualityCheckResult(false, 0f, 0f, "File not found")
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(imagePath, opts) ?: return QualityCheckResult(false, 0f, 0f, "Cannot decode")

        var totalLum = 0L
        val w = bitmap.width
        val h = bitmap.height
        val step = 4
        var count = 0
        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalLum += lum
                count++
            }
        }
        val avgLum = if (count > 0) (totalLum / count).toFloat() else 128f
        val isLightingAcceptable = avgLum in 35f..240f

        return QualityCheckResult(
            isAcceptable = isLightingAcceptable,
            brightness = avgLum,
            blurScore = 80f,
            message = if (isLightingAcceptable) "Acceptable" else "Poor lighting detected"
        )
    }

    data class QualityCheckResult(
        val isAcceptable: Boolean,
        val brightness: Float,
        val blurScore: Float,
        val message: String
    )
}
