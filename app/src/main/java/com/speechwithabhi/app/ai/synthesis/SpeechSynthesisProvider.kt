package com.speechwithabhi.app.ai.synthesis

import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import java.io.File

/**
 * Interface contract for speech synthesis providers.
 */
interface SpeechSynthesisProvider {
    val providerId: String
    val isOnlineOnly: Boolean

    /**
     * Synthesizes audio for a chunk of text and saves it to the target file.
     * Returns true on success.
     */
    suspend fun synthesizeChunk(
        chunk: NarrationChunk,
        voiceProfile: VoiceProfile,
        targetFile: File
    ): Boolean

    /**
     * Returns the verified list of supported voice profiles for this provider.
     */
    fun getAvailableVoices(): List<VoiceProfile>
}
