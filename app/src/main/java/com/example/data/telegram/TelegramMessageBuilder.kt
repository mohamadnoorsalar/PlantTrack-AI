package com.example.data.telegram

import com.example.data.local.entity.Observation
import com.example.util.JalaliDateHelper
import org.json.JSONArray

object TelegramMessageBuilder {

    /**
     * Builds a single, unified, elegant Persian post text for Telegram.
     * Guaranteed to stay within Telegram's 1024 photo caption character budget (under 1000 characters).
     * Eliminates extra blank lines and uses compact bullet points to avoid any message splitting.
     */
    fun buildObservationPost(
        plantName: String,
        plantId: String,
        observation: Observation,
        lightCondition: String = "",
        temperature: String = "",
        humidity: String = ""
    ): String {
        val persianDateString = JalaliDateHelper.formatToPersianDateTime(observation.createdAt)

        val visualChangesList = parseJsonList(observation.visualChanges)
        val visibleIssuesList = parseJsonList(observation.visibleIssues)
        val possibleCausesList = parseJsonList(observation.possibleCauses)
        val recommendedList = parseJsonList(observation.recommendedObservations)

        val cleanName = plantName.ifBlank { "گیاه" }
        val overallStatus = observation.overallStatus.ifBlank { "عادی" }

        // Format bullets concisely
        val visualChangesText = formatListToBullets(visualChangesList, "تغییر ظاهری خاصی مشاهده نشد")
        val visibleIssuesText = formatListToBullets(visibleIssuesList, "مورد یا آسیبی مشاهده نشد")
        val possibleCausesText = formatListToBullets(possibleCausesList, "")
        val recommendedText = formatListToBullets(recommendedList, "")

        val sb = StringBuilder()
        sb.append("🌿 PlantTrack AI | $cleanName ($plantId)\n")
        sb.append("📅 $persianDateString\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("📊 وضعیت: $overallStatus\n")

        if (observation.summary.isNotBlank()) {
            val cleanSummary = observation.summary.trim().replace("\n", " ")
            sb.append("📝 خلاصه: $cleanSummary\n")
        }

        sb.append("🔍 تغییرات: $visualChangesText\n")
        sb.append("⚠️ نشانه‌ها: $visibleIssuesText\n")

        if (possibleCausesText.isNotBlank()) {
            sb.append("💡 علل احتمالی: $possibleCausesText\n")
        }

        if (recommendedText.isNotBlank()) {
            sb.append("🌱 اقدامات پیشنهادی: $recommendedText\n")
        }

        // Optional environment / user notes
        val envParts = mutableListOf<String>()
        if (lightCondition.isNotBlank()) envParts.add("نور: $lightCondition")
        if (temperature.isNotBlank()) envParts.add("دما: $temperature")
        if (humidity.isNotBlank()) envParts.add("رطوبت: $humidity")
        val hasEnv = envParts.isNotEmpty()
        val hasNote = observation.userNote.isNotBlank()

        if (hasEnv || hasNote) {
            val noteDetails = mutableListOf<String>()
            if (hasEnv) noteDetails.add(envParts.joinToString(" | "))
            if (hasNote) noteDetails.add(observation.userNote.trim().replace("\n", " "))
            sb.append("🌡 شرایط و یادداشت: ${noteDetails.joinToString(" • ")}\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("🤖 گزارش هوشمند PlantTrack AI")

        var resultText = sb.toString().trim()

        // Strict character budget: ensure always strictly <= 1000 characters for Telegram single photo caption
        if (resultText.length > 1000) {
            resultText = compactTextToFitBudget(
                cleanName = cleanName,
                plantId = plantId,
                date = persianDateString,
                status = overallStatus,
                summary = observation.summary,
                visualChanges = visualChangesList,
                visibleIssues = visibleIssuesList,
                possibleCauses = possibleCausesList,
                recommended = recommendedList
            )
        }

        return resultText
    }

    private fun formatListToBullets(items: List<String>, emptyFallback: String): String {
        if (items.isEmpty()) return emptyFallback
        // For compact layout, join items with bullets on separate lines or clean inline format
        return items.take(3).joinToString(" | ") { it.trim().replace("\n", " ") }
    }

    /**
     * Compacts text dynamically to ensure the entire report fits cleanly in under 1000 characters.
     */
    private fun compactTextToFitBudget(
        cleanName: String,
        plantId: String,
        date: String,
        status: String,
        summary: String,
        visualChanges: List<String>,
        visibleIssues: List<String>,
        possibleCauses: List<String>,
        recommended: List<String>
    ): String {
        val sb = StringBuilder()
        sb.append("🌿 PlantTrack AI | $cleanName ($plantId)\n")
        sb.append("📅 $date\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("📊 وضعیت: $status\n")

        if (summary.isNotBlank()) {
            val shortSummary = summary.trim().replace("\n", " ").take(160)
            sb.append("📝 خلاصه: $shortSummary\n")
        }

        val vc = if (visualChanges.isNotEmpty()) visualChanges.take(2).joinToString(" | ") else "عادی"
        sb.append("🔍 تغییرات: $vc\n")

        val vi = if (visibleIssues.isNotEmpty()) visibleIssues.take(2).joinToString(" | ") else "بدون عارضه"
        sb.append("⚠️ نشانه‌ها: $vi\n")

        if (possibleCauses.isNotEmpty()) {
            val pc = possibleCauses.take(2).joinToString(" | ")
            sb.append("💡 علل احتمالی: $pc\n")
        }

        if (recommended.isNotEmpty()) {
            val rec = recommended.take(2).joinToString(" | ")
            sb.append("🌱 اقدامات پیشنهادی: $rec\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("🤖 گزارش هوشمند PlantTrack AI")

        return sb.toString().trim().take(1000)
    }

    private fun parseJsonList(jsonString: String): List<String> {
        if (jsonString.isBlank() || jsonString == "[]") return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.isNotEmpty() && item != "null") {
                    list.add(item)
                }
            }
        } catch (_: Exception) {
            jsonString.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { list.add(it) }
        }
        return list
    }
}
