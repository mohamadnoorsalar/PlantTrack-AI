package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class GeminiAnalysisResult(
    val overallStatus: String,
    val summary: String,
    val visualChanges: List<String>,
    val visibleIssues: List<String>,
    val possibleCauses: List<String>,
    val recommendedObservations: List<String>,
    val confidence: Float
)

class GeminiPlantService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    suspend fun testConnection(customApiKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val buildKey = BuildConfig.GEMINI_API_KEY
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("کلید هوش مصنوعی (Gemini API Key) تنظیم نشده است.")
            )
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Ping test") })
                        })
                    })
                })
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("اتصال به Gemini AI با موفقیت برقرار شد!")
            } else {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                Result.failure(Exception("خطا (${response.code}): $errBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Analyzes a plant image to identify its Persian common species name.
     * Returns ONLY the clean Persian name (e.g. 'شاهدانه', 'پتوس', 'فیکوس بنجامین').
     */
    suspend fun identifyPlantSpecies(
        bitmap: Bitmap,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val buildKey = BuildConfig.GEMINI_API_KEY
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("کلید هوش مصنوعی (Gemini API Key) تنظیم نشده است.")
            )
        }

        try {
            val maxDim = 800f
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = maxDim / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = "Analyze this plant photo and identify its common Persian name and species. Return ONLY the name in Persian (e.g. 'شاهدانه' or 'پتوس' or 'فیکوس بنجامین'). If unsure, provide the most likely plant species name. Do not include markdown or explanations, return only the plain species name."

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext Result.failure(Exception("Gemini API error (${response.code}): $errBody"))
            }

            val respBody = response.body?.string() ?: ""
            val root = JSONObject(respBody)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleaned = rawText.trim()
                .removePrefix("```")
                .removeSuffix("```")
                .replace("\n", " ")
                .trim()

            if (cleaned.isNotBlank()) {
                Result.success(cleaned)
            } else {
                Result.failure(Exception("نام گونه تشخیص داده نشد"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzePlantImage(
        imagePath: String,
        plantName: String = "",
        userPromptNote: String = "",
        customApiKey: String? = null,
        outputLanguage: String = "fa"
    ): Result<GeminiAnalysisResult> = withContext(Dispatchers.IO) {
        val buildKey = BuildConfig.GEMINI_API_KEY
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("کلید هوش مصنوعی (Gemini API Key) تنظیم نشده است. لطفاً در بخش تنظیمات یا AI Studio Secrets کلید را وارد کنید.")
            )
        }

        val file = File(imagePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("فایل تصویر یافت نشد: $imagePath"))
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(imagePath, opts)
            ?: return@withContext Result.failure(IllegalStateException("خطا در پردازش تصویر گیاه"))

        val maxDim = 1024f
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        // Attempt 1: Regular execution with explicit language instructions
        var result = executeGeminiRequest(apiKey, base64Image, plantName, userPromptNote, outputLanguage, isRetry = false)

        // If Persian was requested, validate that the result is in Persian. If not, auto-retry once with stronger enforcement
        if (outputLanguage == "fa" && result.isSuccess) {
            val parsed = result.getOrThrow()
            if (!containsPersianText(parsed.overallStatus + " " + parsed.summary)) {
                // Auto retry once with strict Persian instruction
                result = executeGeminiRequest(apiKey, base64Image, plantName, userPromptNote, outputLanguage, isRetry = true)
                if (result.isSuccess) {
                    val retryParsed = result.getOrThrow()
                    if (!containsPersianText(retryParsed.overallStatus + " " + retryParsed.summary)) {
                        return@withContext Result.failure(
                            IllegalStateException("پاسخ هوش مصنوعی به زبان فارسی ارائه نشد. لطفاً مجدداً تلاش کنید.")
                        )
                    }
                }
            }
        }

        result
    }

    private fun executeGeminiRequest(
        apiKey: String,
        base64Image: String,
        plantName: String,
        userPromptNote: String,
        outputLanguage: String,
        isRetry: Boolean
    ): Result<GeminiAnalysisResult> {
        val isPersian = outputLanguage == "fa"

        val languageInstruction = if (isPersian) {
            """
            MANDATORY LANGUAGE REQUIREMENT:
            Respond in natural, fluent Persian (Farsi) for ALL user-facing text values.
            Do NOT write the analysis in English.
            Keep JSON keys in English, but ALL string values intended for the user (overall_status, summary, visual_changes, visible_issues, possible_causes, recommended_observations) MUST BE IN PERSIAN (FARSI).
            ${if (isRetry) "STRICT ENFORCEMENT: The previous response was rejected because it was not in Persian. You MUST write all string values in Persian (فارسی)!" else ""}
            """.trimIndent()
        } else {
            "Respond in English for all textual values."
        }

        val systemPrompt = """
            You are a visual plant observation assistant.
            Analyze only what is visibly supported by the provided image.
            Clearly distinguish:
            1. Direct visual observations
            2. Possible explanations
            3. Uncertainty
            Never claim certainty when the image does not support it.
            If the image quality is insufficient, explicitly state that a clearer image is needed.
            Do not invent missing information.
            Do not provide specialized advice on controlled substances, heavy yield maximization, or specialized industrial cultivation.
            
            $languageInstruction

            Return the result strictly as a valid JSON object matching this schema:
            {
              "overall_status": "string in ${if (isPersian) "Persian (e.g. وضعیت کلی مناسب به نظر می‌رسد / نیازمند بررسی / شاداب)" else "English"}",
              "summary": "string in ${if (isPersian) "Persian" else "English"}",
              "visual_changes": ["string in ${if (isPersian) "Persian" else "English"}"],
              "visible_issues": ["string in ${if (isPersian) "Persian" else "English"}"],
              "possible_causes": ["string in ${if (isPersian) "Persian" else "English"}"],
              "recommended_observations": ["string in ${if (isPersian) "Persian" else "English"}"],
              "confidence": 0.95
            }
        """.trimIndent()

        val userInstruction = if (isPersian) {
            "لطفاً تصویر مشاهده گیاه را${if (plantName.isNotBlank()) " برای گیاه با نام '$plantName'" else ""} به دقت تحلیل کن و تمام توضیحات را به زبان فارسی در قالب JSON برگردان.${if (userPromptNote.isNotBlank()) " یادداشت کاربر: $userPromptNote" else ""}"
        } else {
            "Analyze this plant observation image${if (plantName.isNotBlank()) " for plant named '$plantName'" else ""}.${if (userPromptNote.isNotBlank()) " Additional context: $userPromptNote" else ""}"
        }

        try {
            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userInstruction)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                return Result.failure(Exception("Gemini API error (${response.code}): $errBody"))
            }

            val respBody = response.body?.string() ?: ""
            val parsedResult = parseGeminiResponse(respBody)
            return Result.success(parsedResult)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun containsPersianText(text: String): Boolean {
        // Range for Arabic/Persian Unicode characters
        for (char in text) {
            val code = char.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF) {
                return true
            }
        }
        return false
    }

    private fun parseGeminiResponse(jsonText: String): GeminiAnalysisResult {
        val root = JSONObject(jsonText)
        val candidates = root.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text") ?: "{}"

        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val data = JSONObject(cleaned)

        val overallStatus = data.optString("overall_status", "وضعیت ثبت شد")
        val summary = data.optString("summary", "بررسی تصویر انجام شد.")
        val visualChanges = jsonArrayToList(data.optJSONArray("visual_changes"))
        val visibleIssues = jsonArrayToList(data.optJSONArray("visible_issues"))
        val possibleCauses = jsonArrayToList(data.optJSONArray("possible_causes"))
        val recommendedObservations = jsonArrayToList(data.optJSONArray("recommended_observations"))
        val confidence = data.optDouble("confidence", 0.85).toFloat()

        return GeminiAnalysisResult(
            overallStatus = overallStatus,
            summary = summary,
            visualChanges = visualChanges,
            visibleIssues = visibleIssues,
            possibleCauses = possibleCauses,
            recommendedObservations = recommendedObservations,
            confidence = confidence
        )
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i)
            if (item.isNotBlank()) list.add(item)
        }
        return list
    }
}
