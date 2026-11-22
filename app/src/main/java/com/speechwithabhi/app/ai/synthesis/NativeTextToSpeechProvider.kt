package com.speechwithabhi.app.ai.synthesis

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * 100% Offline, on-device speech synthesis provider utilizing Android's native TextToSpeech engine.
 * Synthesizes audio files locally with zero network calls, zero quota limits, and total privacy.
 */
class NativeTextToSpeechProvider(
    private val context: Context
) : SpeechSynthesisProvider {

    override val providerId: String = "native_tts"
    override val isOnlineOnly: Boolean = false

    private var tts: TextToSpeech? = null
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initDeferred.complete(true)
            } else {
                initDeferred.complete(false)
            }
        }
    }

    override suspend fun synthesizeChunk(
        chunk: NarrationChunk,
        voiceProfile: VoiceProfile,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val ready = withTimeoutOrNull(10_000L) { initDeferred.await() } ?: false
        if (!ready || tts == null) return@withContext false

        val utteranceId = "chunk_${chunk.id}"
        val synthesisDeferred = CompletableDeferred<Boolean>()

        val locale = if (chunk.language.startsWith("hi", ignoreCase = true)) {
            Locale("hi", "IN")
        } else {
            Locale.ENGLISH
        }
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.getDefault()
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(targetFile.exists() && targetFile.length() > 0)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(false)
                }
            }
        })

        val params = Bundle()
        val result = tts?.synthesizeToFile(chunk.text, params, targetFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            return@withContext false
        }

        withTimeoutOrNull(25_000L) { synthesisDeferred.await() } ?: false
    }

    override fun getAvailableVoices(): List<VoiceProfile> {
        return listOf(
            VoiceProfile(
                providerId = "native_tts",
                modelId = "device_default",
                voiceId = "system_default",
                displayName = "Device System Voice",
                acousticProfile = "Native on-device synthesized voice",
                genderPresentation = "Neutral",
                styleTags = listOf("Offline", "Device Default", "Zero Latency")
            )
        )
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
