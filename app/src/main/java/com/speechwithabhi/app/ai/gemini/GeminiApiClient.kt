package com.speechwithabhi.app.ai.gemini

import com.speechwithabhi.app.ai.security.SecureApiKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Official Google Gemini API Client supporting text generation,
 * structured JSON output, and generative speech audio synthesis.
 */
class GeminiApiClient(
    private val apiKeyStorage: SecureApiKeyStorage,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    var modelId: String = "gemini-2.0-flash"
    var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Generates structured text content (e.g. for conservative cleanup or translation).
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        responseJsonSchema: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStorage.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key is not configured. Please set your key in Settings.")

        val payload = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            }
            put("contents", contents)

            if (!systemInstruction.isNullOrBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
            }

            val genConfig = JSONObject()
            if (responseJsonSchema) {
                genConfig.put("responseMimeType", "application/json")
            }
            genConfig.put("temperature", 0.1) // Conservative, factual reproduction
            put("generationConfig", genConfig)
        }

        val url = "$baseUrl/models/$modelId:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeWithRetry(request) { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text") ?: ""
        }
    }

    /**
     * Generates natural speech audio via Gemini Generative Audio endpoint.
     * Returns base64 encoded audio bytes (PCM/WAV/MP3).
     */
    suspend fun generateSpeechAudio(
        text: String,
        voiceName: String = "Kore"
    ): ByteArray = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStorage.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key is not configured.")

        val payload = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", text))
                    })
                })
            }
            put("contents", contents)

            val genConfig = JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            }
            put("generationConfig", genConfig)
        }

        val url = "$baseUrl/models/$modelId:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeWithRetry(request) { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val inlineData = parts?.optJSONObject(0)?.optJSONObject("inlineData")
            val base64Data = inlineData?.optString("data") ?: ""

            if (base64Data.isBlank()) {
                throw IOException("Gemini returned empty audio payload.")
            }

            android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        }
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        parser: (Response) -> T
    ): T {
        var attempts = 0
        val maxAttempts = 3
        var backoffMs = 1000L

        while (true) {
            attempts++
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 429 || response.code in 500..599) {
                        if (attempts >= maxAttempts) {
                            val body = response.body?.string() ?: ""
                            handleHttpError(response.code, body)
                        }
                    } else {
                        return parser(response)
                    }
                }
            } catch (e: IOException) {
                if (attempts >= maxAttempts) throw e
            }

            // Exponential backoff with jitter
            val jitter = Random.nextLong(100, 500)
            delay(backoffMs + jitter)
            backoffMs *= 2
        }
    }

    private fun handleHttpError(code: Int, body: String): Nothing {
        when (code) {
            400 -> throw IllegalArgumentException("Invalid API request. Please verify document parameters.")
            401, 403 -> throw SecurityException("Invalid Gemini API key. Please check your credentials in Settings.")
            429 -> throw IOException("Gemini API rate limit or quota exceeded (HTTP 429). Falling back to offline TTS.")
            in 500..599 -> throw IOException("Google Gemini server error ($code). Please retry in a few moments.")
            else -> throw IOException("Gemini API error ($code): ${body.take(150)}")
        }
    }
}
