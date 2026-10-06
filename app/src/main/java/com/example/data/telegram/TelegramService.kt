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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    suspend fun testConnection(botToken: String, chatId: String): Result<String> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext Result.failure(IllegalArgumentException("توکن ربات تلگرام وارد نشده است"))
        try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطای سرور تلگرام: کد ${response.code}"))
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.optBoolean("ok")) {
                val user = json.optJSONObject("result")?.optString("first_name", "Bot")
                Result.success("اتصال موفقیت‌آمیز به ربات $user!")
            } else {
                Result.failure(Exception("اتصال ناموفق: ${json.optString("description")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends the observation report to Telegram.
     * If the text exceeds Telegram's photo caption limit (1024 characters),
     * it gracefully sends the photo with a headline caption and delivers
     * the complete comprehensive report as an immediately following message
     * to avoid truncation, or falls back to text if image is missing.
     */
    suspend fun sendObservationPhoto(
        botToken: String,
        chatId: String,
        imagePath: String,
        captionText: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("توکن ربات و شناسه چت باید تنظیم شده باشند"))
        }

        val file = File(imagePath)
        if (!file.exists()) {
            // Fallback to text message if image is missing
            return@withContext sendMessage(botToken, chatId, captionText)
        }

        try {
            // Telegram photo caption limit is 1024 characters
            if (captionText.length <= 1024) {
                // Fits in photo caption directly
                val url = "https://api.telegram.org/bot${botToken.trim()}/sendPhoto"
                val mediaType = "image/jpeg".toMediaType()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.trim())
                    .addFormDataPart("caption", captionText)
                    .addFormDataPart("photo", file.name, file.asRequestBody(mediaType))
                    .build()

                val request = Request.Builder().url(url).post(requestBody).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                if (response.isSuccessful && json.optBoolean("ok")) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("خطای ارسال تلگرام: ${json.optString("description", "عدم پاسخ سرور")}"))
                }
            } else {
                // Caption exceeds 1024 characters:
                // Send photo with concise header, then send the full detailed post as message to avoid truncation
                val photoUrl = "https://api.telegram.org/bot${botToken.trim()}/sendPhoto"
                val mediaType = "image/jpeg".toMediaType()
                val shortCaption = captionText.take(900).substringBeforeLast("\n") + "\n\n(ادامه گزارش در پیام زیر 👇)"
                val photoRequestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.trim())
                    .addFormDataPart("caption", shortCaption)
                    .addFormDataPart("photo", file.name, file.asRequestBody(mediaType))
                    .build()

                val photoRequest = Request.Builder().url(photoUrl).post(photoRequestBody).build()
                val photoResponse = client.newCall(photoRequest).execute()
                val photoBody = photoResponse.body?.string() ?: "{}"
                val photoJson = JSONObject(photoBody)

                if (!photoResponse.isSuccessful || !photoJson.optBoolean("ok")) {
                    return@withContext Result.failure(Exception("خطای ارسال تصویر: ${photoJson.optString("description")}"))
                }

                // Send the complete full text as follow-up text message (allows up to 4096 chars)
                sendMessage(botToken, chatId, captionText)
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
                Result.failure(Exception(respJson.optString("description", "خطا در ارسال پیام")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
