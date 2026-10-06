package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantReferenceImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

object PlantVisualMatcher {

    data class CandidateMatch(
        val plant: Plant,
        val confidence: Float, // 0.0 to 1.0 (e.g. 0.94f)
        val scoreBreakdown: String = ""
    )

    // Calculate a visual fingerprint for a picture:
    // 16-bin color histogram (RGB distribution) + aspect ratio + average greenness
    private data class ImageFingerprint(
        val rAvg: Float,
        val gAvg: Float,
        val bAvg: Float,
        val greenDominance: Float,
        val histogram: FloatArray // 16 elements
    )

    private fun extractFingerprint(imagePath: String): ImageFingerprint? {
        val file = File(imagePath)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(imagePath, opts) ?: return null

        val w = bitmap.width
        val h = bitmap.height
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        val hist = FloatArray(16)
        val step = 3
        var count = 0

        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                totalR += r
                totalG += g
                totalB += b

                val bin = ((r + g + b) / 3 * 16) / 256
                if (bin in 0..15) hist[bin] += 1f
                count++
            }
        }
        if (count == 0) return null

        // Normalize histogram
        for (i in hist.indices) {
            hist[i] = hist[i] / count
        }

        val rA = (totalR.toFloat() / count)
        val gA = (totalG.toFloat() / count)
        val bA = (totalB.toFloat() / count)
        val greenDom = if (rA + bA > 0) gA / (rA + bA) else 1f

        return ImageFingerprint(rA, gA, bA, greenDom, hist)
    }

    private fun compareFingerprints(fp1: ImageFingerprint, fp2: ImageFingerprint): Float {
        // Histogram intersection
        var histSim = 0f
        for (i in fp1.histogram.indices) {
            histSim += minOf(fp1.histogram[i], fp2.histogram[i])
        }

        // Color distance normalized (0 to 1)
        val dr = abs(fp1.rAvg - fp2.rAvg) / 255f
        val dg = abs(fp1.gAvg - fp2.gAvg) / 255f
        val db = abs(fp1.bAvg - fp2.bAvg) / 255f
        val colorDist = (dr + dg + db) / 3f
        val colorSim = 1f - colorDist

        // Green dominance similarity
        val greenSim = 1f - minOf(1f, abs(fp1.greenDominance - fp2.greenDominance) / 2f)

        // Weighted total
        val sim = (histSim * 0.5f) + (colorSim * 0.3f) + (greenSim * 0.2f)
        return sim.coerceIn(0f, 1f)
    }

    suspend fun matchCandidates(
        newImagePath: String,
        plantsWithReferences: List<Pair<Plant, List<PlantReferenceImage>>>,
        locationHintX: Float? = null,
        locationHintY: Float? = null
    ): List<CandidateMatch> = withContext(Dispatchers.Default) {
        val queryFp = extractFingerprint(newImagePath)

        val results = mutableListOf<CandidateMatch>()

        for ((plant, refImages) in plantsWithReferences) {
            if (refImages.isEmpty()) {
                // Base fallback confidence
                results.add(CandidateMatch(plant, 0.35f, "No reference photos"))
                continue
            }

            var bestVisualSim = 0.0f
            if (queryFp != null) {
                for (ref in refImages) {
                    val refFp = extractFingerprint(ref.imagePath)
                    if (refFp != null) {
                        val sim = compareFingerprints(queryFp, refFp)
                        if (sim > bestVisualSim) {
                            bestVisualSim = sim
                        }
                    }
                }
            } else {
                bestVisualSim = 0.5f
            }

            // Location assistance (Optional hint, adds small bonus up to 15% if coordinates align)
            var locBonus = 0.0f
            if (locationHintX != null && locationHintY != null && plant.relativeX != null && plant.relativeY != null) {
                val dx = locationHintX - plant.relativeX
                val dy = locationHintY - plant.relativeY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 0.2f) {
                    locBonus = (1f - dist / 0.2f) * 0.15f
                }
            }

            // Normalizing confidence score to realistic visual match percentage (e.g. 0.40 .. 0.98)
            val baseConfidence = ((bestVisualSim * 0.85f) + locBonus).coerceIn(0.1f, 0.98f)

            // Dynamic calibration for distinctiveness
            val finalScore = (baseConfidence * 100).toInt() / 100f
            results.add(
                CandidateMatch(
                    plant = plant,
                    confidence = finalScore,
                    scoreBreakdown = "Visual similarity: ${(bestVisualSim * 100).toInt()}%"
                )
            )
        }

        results.sortedByDescending { it.confidence }
    }
}
