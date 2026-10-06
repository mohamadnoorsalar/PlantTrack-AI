package com.example.data.telegram

import com.example.data.local.entity.Observation
import com.example.util.JalaliDateHelper
import org.json.JSONArray

object TelegramMessageBuilder {

    /**
     * Builds the comprehensive Persian post text for a plant observation according to the required specification.
     * Uses Jalali / Solar Hijri calendar with Persian digits (e.g. 📅 تاریخ ثبت: ۱۵ مهر ۱۴۰۵ - ساعت ۱۵:۲۷).
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

        val sb = StringBuilder()
        sb.append("🌿 PlantTrack AI | گزارش وضعیت گیاه\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("📌 نام گیاه: ${plantName.ifBlank { "نام‌گذاری نشده" }}\n")
        sb.append("🆔 شناسه: $plantId\n")
        sb.append("📅 تاریخ ثبت: $persianDateString\n\n")

        sb.append("📊 وضعیت کلی:\n")
        sb.append("${observation.overallStatus.ifBlank { "وضعیت عادی" }}\n\n")

        if (observation.summary.isNotBlank()) {
            sb.append("📝 خلاصه پایش:\n")
            sb.append("${observation.summary}\n\n")
        }

        sb.append("🔍 تغییرات ظاهری مشاهده‌شده:\n")
        if (visualChangesList.isNotEmpty()) {
            visualChangesList.forEach { sb.append("• $it\n") }
        } else {
            sb.append("تغییر ظاهری محسوسی ثبت نشده است\n")
        }
        sb.append("\n")

        sb.append("⚠️ عارضه‌ها یا نشانه‌های نیازمند توجه:\n")
        if (visibleIssuesList.isNotEmpty()) {
            visibleIssuesList.forEach { sb.append("• $it\n") }
        } else {
            sb.append("نشانه‌ای از آسیب یا بیماری مشاهده نشد\n")
        }
        sb.append("\n")

        if (possibleCausesList.isNotEmpty()) {
            sb.append("💡 علل احتمالی:\n")
            possibleCausesList.forEach { sb.append("• $it\n") }
            sb.append("\n")
        }

        if (recommendedList.isNotEmpty()) {
            sb.append("🌱 اقدامات و بررسی‌های پیشنهادی:\n")
            recommendedList.forEach { sb.append("• $it\n") }
            sb.append("\n")
        }

        // Environmental condition and user note (only if provided)
        val hasEnv = lightCondition.isNotBlank() || temperature.isNotBlank() || humidity.isNotBlank()
        val hasNote = observation.userNote.isNotBlank()

        if (hasEnv || hasNote) {
            sb.append("🌡 شرایط محیطی و یادداشت کاربر:\n")
            if (hasEnv) {
                val envParts = mutableListOf<String>()
                if (lightCondition.isNotBlank()) envParts.add("نور: $lightCondition")
                if (temperature.isNotBlank()) envParts.add("دما: $temperature")
                if (humidity.isNotBlank()) envParts.add("رطوبت: $humidity")
                sb.append("• ${envParts.joinToString(" | ")}\n")
            }
            if (hasNote) {
                sb.append("• یادداشت: ${observation.userNote}\n")
            }
            sb.append("\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("🤖 گزارش تولیدشده توسط هوش مصنوعی PlantTrack AI")

        return sb.toString().trim()
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
