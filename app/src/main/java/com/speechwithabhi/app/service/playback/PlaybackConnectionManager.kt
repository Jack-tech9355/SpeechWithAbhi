package com.speechwithabhi.app.service.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.PlaybackState
import com.speechwithabhi.app.core.model.VoiceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * High-level connection bridge between Compose UI and SpeechMediaSessionService.
 * Connects via AndroidX MediaController to ensure synchronized background playback,
 * lock-screen controls, and real-time timeline updates.
 */
class PlaybackConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionPollJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val activeChunk: StateFlow<NarrationChunk?> = SpeechMediaSessionService.currentActiveChunk
    val isBufferingChunk: StateFlow<Boolean> = SpeechMediaSessionService.isBufferingFlow

    private var activeChunks: List<NarrationChunk> = emptyList()
    private var activeVoice: VoiceProfile? = null

    fun connect() {
        if (mediaController != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, SpeechMediaSessionService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListeners()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))

        // Monitor active chunk flow and synchronize with playback state
        scope.launch {
            SpeechMediaSessionService.currentActiveChunk.collect { chunk ->
                if (chunk != null) {
                    _playbackState.value = _playbackState.value.copy(
                        activeChunkId = chunk.id
                    )
                }
            }
        }

        scope.launch {
            SpeechMediaSessionService.isBufferingFlow.collect { buffering ->
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = buffering
                )
            }
        }
    }

    private fun setupControllerListeners() {
        val controller = mediaController ?: return

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING || SpeechMediaSessionService.isBufferingFlow.value
                )
            }
        })

        _playbackState.value = _playbackState.value.copy(
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition,
            durationMs = controller.duration.coerceAtLeast(0L),
            playbackSpeed = controller.playbackParameters.speed
        )
    }

    private fun ensureServiceStarted() {
        val serviceIntent = Intent(context, SpeechMediaSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        connect()
    }

    fun startPlayback(
        documentId: String,
        documentTitle: String,
        chunks: List<NarrationChunk>,
        voice: VoiceProfile,
        startIndex: Int = 0
    ) {
        ensureServiceStarted()
        activeChunks = chunks
        activeVoice = voice

        _playbackState.value = _playbackState.value.copy(
            documentId = documentId,
            documentTitle = documentTitle,
            activeVoiceId = voice.displayName,
            activeProvider = voice.providerId,
            isPlaying = true
        )

        // Queue chunks into the service queue manager
        SpeechMediaSessionService.setQueue(chunks, voice, startIndex)

        scope.launch(Dispatchers.Main) {
            var waitAttempts = 0
            while (mediaController == null && waitAttempts < 15) {
                delay(100)
                waitAttempts++
            }
            mediaController?.play()
        }
    }

    fun play() {
        SpeechMediaSessionService.activeInstance?.queueManager?.play() ?: mediaController?.play()
    }

    fun pause() {
        SpeechMediaSessionService.activeInstance?.queueManager?.pause() ?: mediaController?.pause()
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun rewind10Seconds() {
        val service = SpeechMediaSessionService.activeInstance
        if (service != null) {
            service.queueManager.seekRelative(-10_000L)
        } else {
            val controller = mediaController ?: return
            val newPos = (controller.currentPosition - 10_000L).coerceAtLeast(0L)
            controller.seekTo(newPos)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = newPos)
        }
    }

    fun forward30Seconds() {
        val service = SpeechMediaSessionService.activeInstance
        if (service != null) {
            service.queueManager.seekRelative(30_000L)
        } else {
            val controller = mediaController ?: return
            val newPos = controller.currentPosition + 30_000L
            controller.seekTo(newPos)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = newPos)
        }
    }

    fun skipNext() {
        val service = SpeechMediaSessionService.activeInstance
        if (service != null) {
            service.queueManager.skipNext()
        }
    }

    fun skipPrevious() {
        val service = SpeechMediaSessionService.activeInstance
        if (service != null) {
            service.queueManager.skipPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(clamped)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)
    }

    fun setSleepTimer(minutes: Int?) {
        _playbackState.value = _playbackState.value.copy(sleepTimerMinutes = minutes)
        SpeechMediaSessionService.activeInstance?.queueManager?.setSleepTimer(minutes ?: 0)
        if (minutes != null && minutes > 0) {
            scope.launch {
                delay(minutes * 60_000L)
                pause()
                _playbackState.value = _playbackState.value.copy(sleepTimerMinutes = null)
            }
        }
    }

    fun setSpeed(speed: Float) {
        SpeechMediaSessionService.activeInstance?.queueManager?.setPlaybackSpeed(speed)
        val controller = mediaController
        controller?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaController?.let { controller ->
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = controller.currentPosition,
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    fun disconnect() {
        stopPositionPolling()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
