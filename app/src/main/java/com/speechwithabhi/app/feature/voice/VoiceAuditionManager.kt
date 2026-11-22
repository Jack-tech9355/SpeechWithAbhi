package com.speechwithabhi.app.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.speechwithabhi.app.ai.synthesis.SpeechSynthesisDispatcher
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import com.speechwithabhi.app.core.storage.DocumentStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles seamless voice auditioning in Voice Studio.
 * Prepares and plays natural audition samples with low latency.
 */
class VoiceAuditionManager(
    private val context: Context,
    private val synthesisDispatcher: SpeechSynthesisDispatcher,
    private val storageManager: DocumentStorageManager,
    private val coroutineScope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentJob: Job? = null

    private val _auditioningVoiceId = MutableStateFlow<String?>(null)
    val auditioningVoiceId: StateFlow<String?> = _auditioningVoiceId.asStateFlow()

    fun previewVoice(voice: VoiceProfile) {
        if (_auditioningVoiceId.value == voice.voiceId) {
            stopAudition()
            return
        }

        stopAudition()
        _auditioningVoiceId.value = voice.voiceId

        currentJob = coroutineScope.launch {
            val sampleText = "Namaste! Main hoon ${voice.displayName}. SpeechWithAbhi me aapka swagat hai."
            val cacheKey = "audition_${voice.voiceId}"
            val sampleFile = storageManager.getAudioChunkCacheFile(cacheKey)

            if (!sampleFile.exists() || sampleFile.length() == 0L) {
                val dummyChunk = NarrationChunk(
                    id = cacheKey,
                    documentId = "audition",
                    sectionId = "sample",
                    orderIndex = 0,
                    text = sampleText,
                    language = "hi"
                )
                val success = synthesisDispatcher.synthesize(dummyChunk, voice, sampleFile)
                if (!success) {
                    _auditioningVoiceId.value = null
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(context, Uri.fromFile(sampleFile))
                        setOnCompletionListener {
                            _auditioningVoiceId.value = null
                            release()
                            mediaPlayer = null
                        }
                        setOnErrorListener { _, _, _ ->
                            _auditioningVoiceId.value = null
                            release()
                            mediaPlayer = null
                            true
                        }
                        prepare()
                        start()
                    }
                } catch (_: Exception) {
                    _auditioningVoiceId.value = null
                }
            }
        }
    }

    fun stopAudition() {
        currentJob?.cancel()
        currentJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _auditioningVoiceId.value = null
    }

    fun release() {
        stopAudition()
    }
}
