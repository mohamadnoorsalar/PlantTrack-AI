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

    suspend fun analyzePlantImage(
        imagePath: String,
        plantName: String = "",
        userPromptNote: String = "",
        customApiKey: String? = null
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
            Return the result strictly as a valid JSON object matching this schema:
            {
              "overall_status": "Healthy" | "Normal" | "Attention Needed" | "Vigorous",
              "summary": "Concise summary of direct visual observation",
              "visual_changes": ["string"],
              "visible_issues": ["string"],
              "possible_causes": ["string"],
              "recommended_observations": ["string"],
              "confidence": 0.95
            }
        """.trimIndent()

        val userInstruction = "Analyze this plant observation image${if (plantName.isNotBlank()) " for plant named '$plantName'" else ""}.${if (userPromptNote.isNotBlank()) " Additional context: $userPromptNote" else ""}"

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

            // Using modern supported Gemini 3.5 Flash model
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
            val parsedResult = parseGeminiResponse(respBody)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        val overallStatus = data.optString("overall_status", "Normal")
        val summary = data.optString("summary", "Observation recorded.")
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
