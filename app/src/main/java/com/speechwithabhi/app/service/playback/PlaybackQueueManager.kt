package com.speechwithabhi.app.service.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
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

/**
 * Manages continuous, gapless playback queue with lookahead prebuffering.
 * Pre-synthesizes upcoming chunks to app-private cache to avoid audio pauses.
 */
class PlaybackQueueManager(
    private val player: ExoPlayer,
    private val storageManager: DocumentStorageManager,
    private val synthesisDispatcher: SpeechSynthesisDispatcher,
    private val coroutineScope: CoroutineScope
) {

    private val chunks = mutableListOf<NarrationChunk>()
    private var activeVoice: VoiceProfile = synthesisDispatcher.getAllVoices().first()
    private var currentChunkIndex = 0
    private var prebufferJob: Job? = null

    private val _activeChunkFlow = MutableStateFlow<NarrationChunk?>(null)
    val activeChunkFlow: StateFlow<NarrationChunk?> = _activeChunkFlow.asStateFlow()

    private val _isBufferingChunk = MutableStateFlow(false)
    val isBufferingChunk: StateFlow<Boolean> = _isBufferingChunk.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMinutes: StateFlow<Int?> = _sleepTimerRemainingMinutes.asStateFlow()

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMinutes.value = null
            return
        }
        _sleepTimerRemainingMinutes.value = minutes
        sleepTimerJob = coroutineScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                kotlinx.coroutines.delay(60_000L)
                remaining--
                _sleepTimerRemainingMinutes.value = if (remaining > 0) remaining else null
            }
            pause()
        }
    }

    fun setQueue(newChunks: List<NarrationChunk>, voice: VoiceProfile, startIndex: Int = 0) {
        chunks.clear()
        chunks.addAll(newChunks)
        activeVoice = voice
        currentChunkIndex = startIndex.coerceIn(0, (newChunks.size - 1).coerceAtLeast(0))

        if (chunks.isNotEmpty()) {
            _activeChunkFlow.value = chunks[currentChunkIndex]
            prepareAndPlayCurrent()
        }
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekRelative(offsetMs: Long) {
        val newPos = (player.currentPosition + offsetMs).coerceAtLeast(0L)
        player.seekTo(newPos)
    }

    fun skipNext() {
        if (currentChunkIndex < chunks.lastIndex) {
            currentChunkIndex++
            _activeChunkFlow.value = chunks[currentChunkIndex]
            prepareAndPlayCurrent()
        }
    }

    fun skipPrevious() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (currentChunkIndex > 0) {
            currentChunkIndex--
            _activeChunkFlow.value = chunks[currentChunkIndex]
            prepareAndPlayCurrent()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        val params = player.playbackParameters.withSpeed(clampedSpeed)
        player.playbackParameters = params
    }

    /**
     * Called when ExoPlayer transitions to the next item or finishes a chunk.
     * Automatically loads and starts the next synthesized chunk for seamless continuous narration.
     */
    fun onChunkCompleted() {
        if (currentChunkIndex < chunks.lastIndex) {
            currentChunkIndex++
            _activeChunkFlow.value = chunks[currentChunkIndex]
            prepareAndPlayCurrent()
        }
    }

    private fun prepareAndPlayCurrent() {
        prebufferJob?.cancel()
        prebufferJob = coroutineScope.launch {
            val chunk = chunks.getOrNull(currentChunkIndex) ?: return@launch
            val cacheFile = storageManager.getAudioChunkCacheFile(chunk.id)

            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                _isBufferingChunk.value = true
                val success = synthesisDispatcher.synthesize(chunk, activeVoice, cacheFile)
                _isBufferingChunk.value = false
                if (!success) {
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(cacheFile))
                    .setMediaId(chunk.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("SpeechWithAbhi")
                            .setArtist(activeVoice.displayName)
                            .setDescription(chunk.text.take(80))
                            .build()
                    )
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }

            // Immediately pre-synthesize chunk N+1 and N+2 in background
            triggerLookaheadPrebuffer()
        }
    }

    private fun triggerLookaheadPrebuffer() {
        coroutineScope.launch(Dispatchers.IO) {
            val lookaheadIndices = listOf(currentChunkIndex + 1, currentChunkIndex + 2)
            for (idx in lookaheadIndices) {
                val nextChunk = chunks.getOrNull(idx) ?: continue
                val nextCacheFile = storageManager.getAudioChunkCacheFile(nextChunk.id)
                if (!nextCacheFile.exists() || nextCacheFile.length() == 0L) {
                    synthesisDispatcher.synthesize(nextChunk, activeVoice, nextCacheFile)
                }
            }
        }
    }
}
