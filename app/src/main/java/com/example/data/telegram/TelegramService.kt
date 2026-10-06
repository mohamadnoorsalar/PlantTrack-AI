package com.example.data.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
) {

    suspend fun testConnection(botToken: String, chatId: String): Result<String> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext Result.failure(IllegalArgumentException("Bot Token is required"))
        try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Telegram API error: ${response.code}"))
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.optBoolean("ok")) {
                val user = json.optJSONObject("result")?.optString("first_name", "Bot")
                Result.success("Connected to $user successfully!")
            } else {
                Result.failure(Exception("Connection failed: ${json.optString("description")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendObservationPhoto(
        botToken: String,
        chatId: String,
        imagePath: String,
        captionText: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token and chat ID must be configured"))
        }

        val file = File(imagePath)
        if (!file.exists()) {
            // Fallback to text message if image missing
            return@withContext sendMessage(botToken, chatId, captionText)
        }

        try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/sendPhoto"
            val mediaType = "image/jpeg".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.trim())
                .addFormDataPart("caption", captionText.take(1024))
                .addFormDataPart("photo", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (response.isSuccessful && json.optBoolean("ok")) {
                Result.success(true)
            } else {
                Result.failure(Exception("Telegram error: ${json.optString("description", "Failed to send photo")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        message: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/sendMessage"
            val json = JSONObject().apply {
                put("chat_id", chatId.trim())
                put("text", message.take(4096))
            }
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val respJson = JSONObject(body)
            if (response.isSuccessful && respJson.optBoolean("ok")) {
                Result.success(true)
            } else {
                Result.failure(Exception(respJson.optString("description", "Failed to send message")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
