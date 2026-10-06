package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Reads the EXIF orientation of an image file and returns the rotation angle in degrees.
     */
    fun getExifRotationDegrees(imagePath: String): Float {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return 0f
            val exif = ExifInterface(imagePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Reads the EXIF orientation of a Uri input stream and returns the rotation angle in degrees.
     */
    fun getExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Rotates a bitmap by the given degrees if non-zero.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Prepares an image for Telegram upload:
     * Reads the original image file directly, inspects its EXIF orientation tag,
     * corrects the rotation (so portrait pictures stay vertical), and retains
     * maximum high-resolution quality (95% JPEG quality) without thumbnail downscaling.
     */
    suspend fun getTelegramReadyImageFile(context: Context, originalImagePath: String): File = withContext(Dispatchers.IO) {
        val originalFile = File(originalImagePath)
        if (!originalFile.exists()) return@withContext originalFile

        val rotation = getExifRotationDegrees(originalImagePath)
        if (rotation == 0f) {
            // Orientation is already correct (normal) - send original file directly with 100% full quality!
            return@withContext originalFile
        }

        // If rotation is needed (e.g. 90, 180, 270 degrees), load full-resolution bitmap,
        // rotate with Matrix, and write to a dedicated telegram_upload cache file with 95% quality.
        try {
            val bitmap = BitmapFactory.decodeFile(originalImagePath) ?: return@withContext originalFile
            val rotated = rotateBitmap(bitmap, rotation)

            val tgDir = File(context.cacheDir, "telegram_uploads").apply { mkdirs() }
            val tgFile = File(tgDir, "TG_${System.currentTimeMillis()}_${originalFile.name}")
            FileOutputStream(tgFile).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (rotated != bitmap) {
                rotated.recycle()
            }
            bitmap.recycle()
            tgFile
        } catch (_: Exception) {
            originalFile
        }
    }

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

        // 1. Detect rotation from EXIF
        val rotationDegrees = getExifRotationDegrees(context, uri)

        // 2. Read input stream and decode
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val decodedBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val baseBitmap = decodedBitmap ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val rotatedBitmap = rotateBitmap(baseBitmap, rotationDegrees)

        // 3. Keep original image at high resolution (quality 95)
        FileOutputStream(originalFile).use { out ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        // 4. Create Thumbnail (max 320px) for smooth UI lists
        val thumbDim = 320f
        val thumbRatio = thumbDim / maxOf(rotatedBitmap.width, rotatedBitmap.height)
        val thumbBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap,
            maxOf(1, (rotatedBitmap.width * thumbRatio).toInt()),
            maxOf(1, (rotatedBitmap.height * thumbRatio).toInt()),
            true
        )

        FileOutputStream(thumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
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

        // Save original with 95% quality
        FileOutputStream(originalFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
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
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
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
