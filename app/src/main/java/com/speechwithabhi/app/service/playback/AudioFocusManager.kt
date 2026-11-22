package com.speechwithabhi.app.service.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Robust audio focus and noisy-intent manager.
 * Pauses playback on incoming calls, alarms, or when headphones are disconnected.
 */
class AudioFocusManager(
    private val context: Context,
    private val onPlayStateChange: (shouldPlay: Boolean) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Headphone unplugged: pause immediately
                onPlayStateChange(false)
            }
        }
    }

    private var isReceiverRegistered = false

    fun requestAudioFocus(): Boolean {
        if (hasFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleFocusChange(focusChange)
                }
                .build()

            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        if (hasFocus) {
            registerNoisyReceiver()
        }
        return hasFocus
    }

    fun abandonAudioFocus() {
        if (!hasFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
        hasFocus = false
        unregisterNoisyReceiver()
    }

    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                onPlayStateChange(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onPlayStateChange(false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onPlayStateChange(true)
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }
}
