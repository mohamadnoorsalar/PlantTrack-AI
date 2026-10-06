package com.example.data.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.data.local.entity.Observation
import com.example.data.local.entity.Plant
import com.example.data.local.entity.PlantReferenceImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportGenerator {

    suspend fun generatePlantReport(
        context: Context,
        plant: Plant,
        references: List<PlantReferenceImage>,
        observations: List<Observation>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reportsDir = File(context.filesDir, "reports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFile = File(reportsDir, "PlantReport_${plant.id}_$timeStamp.pdf")

            val document = PdfDocument()
            val pageWidth = 595 // Standard A4 width in PostScript points (72 dpi)
            val pageHeight = 842 // Standard A4 height

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            // Page 1: Cover & Plant Profile
            val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page1 = document.startPage(pageInfo1)
            val canvas1 = page1.canvas

            // Colors
            val headerPaint = Paint().apply {
                color = Color.rgb(27, 94, 32)
                textSize = 24f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val subHeaderPaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 14f
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(33, 33, 33)
                textSize = 11f
                isAntiAlias = true
            }
            val labelPaint = Paint().apply {
                color = Color.rgb(97, 97, 97)
                textSize = 10f
                isAntiAlias = true
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(200, 230, 201)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            // Top Header Banner
            val bannerPaint = Paint().apply {
                color = Color.rgb(232, 245, 233)
            }
            canvas1.drawRect(0f, 0f, pageWidth.toFloat(), 90f, bannerPaint)

            canvas1.drawText("PlantTrack AI — Plant Report", 30f, 45f, headerPaint)
            canvas1.drawText("Generated on ${dateFormat.format(Date())}", 30f, 70f, subHeaderPaint)

            var y = 120f
            canvas1.drawText("Plant Identification: ${plant.id}", 30f, y, labelPaint)
            y += 20f
            canvas1.drawText("Name: ${plant.name}", 30f, y, headerPaint.apply { textSize = 18f })
            y += 22f
            if (plant.species.isNotBlank()) {
                canvas1.drawText("Species / Variety: ${plant.species}", 30f, y, bodyPaint)
                y += 18f
            }
            if (plant.locationLabel.isNotBlank()) {
                canvas1.drawText("Location: ${plant.locationLabel}", 30f, y, bodyPaint)
                y += 18f
            }
            canvas1.drawText("Registered: ${dateFormat.format(Date(plant.createdAt))}", 30f, y, bodyPaint)
            y += 18f
            if (plant.notes.isNotBlank()) {
                canvas1.drawText("Notes: ${plant.notes}", 30f, y, bodyPaint)
                y += 24f
            } else {
                y += 12f
            }

            // Divider
            canvas1.drawLine(30f, y, (pageWidth - 30).toFloat(), y, borderPaint)
            y += 20f

            // Reference Photos Section
            canvas1.drawText("Reference Images (${references.size})", 30f, y, subHeaderPaint)
            y += 15f

            var imgX = 30f
            val thumbSize = 90f
            for (ref in references.take(5)) {
                val refFile = File(ref.imagePath)
                if (refFile.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = BitmapFactory.decodeFile(ref.imagePath, opts)
                    if (bmp != null) {
                        val scaledBmp = Bitmap.createScaledBitmap(bmp, thumbSize.toInt(), thumbSize.toInt(), true)
                        canvas1.drawBitmap(scaledBmp, imgX, y, null)
                        canvas1.drawRect(imgX, y, imgX + thumbSize, y + thumbSize, borderPaint)
                    }
                }
                imgX += thumbSize + 15f
            }

            y += thumbSize + 30f
            canvas1.drawText("Recent Observations Summary (${observations.size} total)", 30f, y, subHeaderPaint)
            y += 20f

            // List first 3 observations on Page 1
            for (obs in observations.take(3)) {
                canvas1.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 80f, 8f, 8f, bannerPaint)
                canvas1.drawText("Date: ${dateFormat.format(Date(obs.createdAt))} | Status: ${obs.overallStatus}", 40f, y + 20f, bodyPaint)
                canvas1.drawText("Summary: ${obs.summary.take(80)}", 40f, y + 40f, labelPaint)
                if (obs.visualChanges.isNotBlank() && obs.visualChanges != "[]") {
                    canvas1.drawText("Changes: ${obs.visualChanges.take(80)}", 40f, y + 60f, labelPaint)
                }
                y += 95f
            }

            // Footer
            canvas1.drawText("Page 1 of ${if (observations.size > 3) 2 else 1} — Confidential PlantTrack AI Record", 30f, (pageHeight - 20).toFloat(), labelPaint)
            document.finishPage(page1)

            // Page 2 (if there are more observations)
            if (observations.size > 3) {
                val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
                val page2 = document.startPage(pageInfo2)
                val canvas2 = page2.canvas

                canvas2.drawRect(0f, 0f, pageWidth.toFloat(), 50f, bannerPaint)
                canvas2.drawText("PlantTrack AI — Historical Timeline: ${plant.name}", 30f, 32f, subHeaderPaint)

                var y2 = 70f
                for (obs in observations.drop(3).take(7)) {
                    canvas2.drawRoundRect(30f, y2, (pageWidth - 30).toFloat(), y2 + 75f, 6f, 6f, bannerPaint)
                    canvas2.drawText("Date: ${dateFormat.format(Date(obs.createdAt))} | Status: ${obs.overallStatus}", 40f, y2 + 20f, bodyPaint)
                    canvas2.drawText("Summary: ${obs.summary.take(90)}", 40f, y2 + 40f, labelPaint)
                    if (obs.visibleIssues.isNotBlank() && obs.visibleIssues != "[]") {
                        canvas2.drawText("Issues: ${obs.visibleIssues.take(90)}", 40f, y2 + 58f, labelPaint)
                    }
                    y2 += 88f
                }

                canvas2.drawText("Page 2 of 2 — Confidential PlantTrack AI Record", 30f, (pageHeight - 20).toFloat(), labelPaint)
                document.finishPage(page2)
            }

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
