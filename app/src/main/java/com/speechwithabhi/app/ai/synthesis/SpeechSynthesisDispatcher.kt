package com.speechwithabhi.app.ai.synthesis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.speechwithabhi.app.ai.gemini.GeminiApiClient
import com.speechwithabhi.app.ai.security.SecureApiKeyStorage
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Intelligent Speech Synthesis Dispatcher.
 * Dispatches synthesis requests to Gemini Generative Audio when online and credentials exist,
 * and transparently routes to Android Native TextToSpeech when offline, quota is exceeded,
 * or no API key is present.
 */
class SpeechSynthesisDispatcher(
    private val context: Context,
    private val apiKeyStorage: SecureApiKeyStorage = SecureApiKeyStorage(context),
    private val geminiProvider: GeminiSpeechSynthesisProvider = GeminiSpeechSynthesisProvider(GeminiApiClient(apiKeyStorage)),
    private val nativeProvider: NativeTextToSpeechProvider = NativeTextToSpeechProvider(context)
) {

    private val _activeProviderState = MutableStateFlow("gemini")
    val activeProviderState: StateFlow<String> = _activeProviderState.asStateFlow()

    private val _isOfflineFallback = MutableStateFlow(false)
    val isOfflineFallback: StateFlow<Boolean> = _isOfflineFallback.asStateFlow()

    /**
     * Synthesizes audio for a chunk with automatic graceful fallback.
     */
    suspend fun synthesize(
        chunk: NarrationChunk,
        preferredVoice: VoiceProfile,
        targetFile: File
    ): Boolean {
        val hasKey = apiKeyStorage.hasGeminiApiKey()
        val isOnline = isNetworkAvailable()

        // 1. If user prefers Gemini and conditions allow, try Gemini
        if (preferredVoice.providerId == "gemini" && hasKey && isOnline) {
            val success = geminiProvider.synthesizeChunk(chunk, preferredVoice, targetFile)
            if (success) {
                _activeProviderState.value = "gemini"
                _isOfflineFallback.value = false
                return true
            }
        }

        // 2. Otherwise route to Native On-Device TTS
        _activeProviderState.value = "native_tts"
        _isOfflineFallback.value = true

        val fallbackVoice = nativeProvider.getAvailableVoices().first()
        return nativeProvider.synthesizeChunk(chunk, fallbackVoice, targetFile)
    }

    /**
     * Collects all available voice profiles from both providers.
     */
    fun getAllVoices(): List<VoiceProfile> {
        val list = mutableListOf<VoiceProfile>()
        list.addAll(geminiProvider.getAvailableVoices())
        list.addAll(nativeProvider.getAvailableVoices())
        return list
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
