package com.speechwithabhi.app.service.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.speechwithabhi.app.MainActivity
import com.speechwithabhi.app.ai.synthesis.SpeechSynthesisDispatcher
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import com.speechwithabhi.app.core.storage.DocumentStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AndroidX Media3 MediaSessionService implementing compliant background audio playback.
 * Declared with foregroundServiceType="mediaPlayback" for continuous screen-off narration.
 */
class SpeechMediaSessionService : MediaSessionService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocusManager: AudioFocusManager
    lateinit var queueManager: PlaybackQueueManager
        private set

    override fun onCreate() {
        super.onCreate()
        activeInstance = this

        // 1. Initialize ExoPlayer with pitch-preserving time stretch
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(false) // Managed via AudioFocusManager
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    queueManager.onChunkCompleted()
                }
            }
        })

        // 2. Initialize AudioFocus Manager
        audioFocusManager = AudioFocusManager(this) { shouldPlay ->
            if (shouldPlay) {
                player.play()
            } else {
                player.pause()
            }
        }

        // 3. Initialize Storage & Queue
        val storageManager = DocumentStorageManager(this)
        val synthesisDispatcher = SpeechSynthesisDispatcher(this)
        queueManager = PlaybackQueueManager(player, storageManager, synthesisDispatcher, serviceScope)

        // Listen for active chunk updates and mirror to static flow for UI
        serviceScope.launch {
            queueManager.activeChunkFlow.collect { chunk ->
                _currentActiveChunk.value = chunk
            }
        }
        serviceScope.launch {
            queueManager.isBufferingChunk.collect { buffering ->
                _isBufferingFlow.value = buffering
            }
        }

        // Apply pending queue if started while service was initializing
        pendingQueue?.let { data ->
            queueManager.setQueue(data.chunks, data.voice, data.startIndex)
            pendingQueue = null
        }

        // 4. Activity launch PendingIntent for notification clicks
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 5. Build Media3 MediaSession with custom commands (Rewind 10s, Forward 30s)
        val rewindCommand = SessionCommand(CUSTOM_COMMAND_REWIND_10, Bundle.EMPTY)
        val forwardCommand = SessionCommand(CUSTOM_COMMAND_FORWARD_30, Bundle.EMPTY)

        val customCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_COMMAND_REWIND_10 -> {
                        queueManager.seekRelative(-10_000L)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_COMMAND_FORWARD_30 -> {
                        queueManager.seekRelative(30_000L)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(customCallback)
            .setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setDisplayName("-10s")
                        .setSessionCommand(rewindCommand)
                        .setIconResId(android.R.drawable.ic_media_rew)
                        .build(),
                    CommandButton.Builder()
                        .setDisplayName("+30s")
                        .setSessionCommand(forwardCommand)
                        .setIconResId(android.R.drawable.ic_media_ff)
                        .build()
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        audioFocusManager.abandonAudioFocus()
        mediaSession.run {
            player.release()
            release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class PendingQueue(
        val chunks: List<NarrationChunk>,
        val voice: VoiceProfile,
        val startIndex: Int
    )

    companion object {
        const val CUSTOM_COMMAND_REWIND_10 = "com.speechwithabhi.action.REWIND_10"
        const val CUSTOM_COMMAND_FORWARD_30 = "com.speechwithabhi.action.FORWARD_30"

        @Volatile
        var activeInstance: SpeechMediaSessionService? = null
            private set

        private var pendingQueue: PendingQueue? = null

        private val _currentActiveChunk = MutableStateFlow<NarrationChunk?>(null)
        val currentActiveChunk: StateFlow<NarrationChunk?> = _currentActiveChunk.asStateFlow()

        private val _isBufferingFlow = MutableStateFlow(false)
        val isBufferingFlow: StateFlow<Boolean> = _isBufferingFlow.asStateFlow()

        fun setQueue(chunks: List<NarrationChunk>, voice: VoiceProfile, startIndex: Int = 0) {
            val service = activeInstance
            if (service != null) {
                service.queueManager.setQueue(chunks, voice, startIndex)
            } else {
                pendingQueue = PendingQueue(chunks, voice, startIndex)
            }
        }
    }
}
