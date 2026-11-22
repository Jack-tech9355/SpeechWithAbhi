package com.speechwithabhi.app.ai.synthesis

import com.speechwithabhi.app.ai.gemini.GeminiApiClient
import com.speechwithabhi.app.core.model.NarrationChunk
import com.speechwithabhi.app.core.model.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gemini AI Speech Synthesis provider delivering human-like, expressive narration
 * with verified prebuilt celestial voices.
 */
class GeminiSpeechSynthesisProvider(
    private val apiClient: GeminiApiClient
) : SpeechSynthesisProvider {

    override val providerId: String = "gemini"
    override val isOnlineOnly: Boolean = true

    override suspend fun synthesizeChunk(
        chunk: NarrationChunk,
        voiceProfile: VoiceProfile,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val audioBytes = apiClient.generateSpeechAudio(
                text = chunk.text,
                voiceName = voiceProfile.voiceId
            )
            val containerizedAudio = ensureWavContainer(audioBytes, sampleRate = 24000)
            FileOutputStream(targetFile).use { out ->
                out.write(containerizedAudio)
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspects audio payload. If raw PCM is returned by the Gemini API,
     * wraps it in a standard 44-byte RIFF/WAV header to guarantee gapless ExoPlayer playback.
     */
    private fun ensureWavContainer(data: ByteArray, sampleRate: Int = 24000): ByteArray {
        if (data.size >= 4 && data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()) {
            return data // Already a containerized RIFF/WAV file
        }

        val totalDataLen = data.size + 36
        val channels = 1
        val byteRate = sampleRate * channels * 16 / 8

        val header = ByteArray(44)
        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size for 16-bit PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // BlockAlign
        header[33] = 0
        header[34] = 16 // BitsPerSample
        header[35] = 0
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (data.size and 0xff).toByte()
        header[41] = ((data.size shr 8) and 0xff).toByte()
        header[42] = ((data.size shr 16) and 0xff).toByte()
        header[43] = ((data.size shr 24) and 0xff).toByte()

        return header + data
    }

    override fun getAvailableVoices(): List<VoiceProfile> {
        return VERIFIED_GEMINI_VOICES
    }

    companion object {
        val VERIFIED_GEMINI_VOICES = listOf(
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Kore",
                displayName = "Kore",
                acousticProfile = "Firm, clear, authoritative tone",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Clarity", "Textbooks", "Academic")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Puck",
                displayName = "Puck",
                acousticProfile = "Upbeat, lively, energetic tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Lively", "Storytelling", "Engaging")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Charon",
                displayName = "Charon",
                acousticProfile = "Deep, informative, calm tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Deep", "Documentaries", "Business")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Aoede",
                displayName = "Aoede",
                acousticProfile = "Breezy, natural, relaxed tone",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Conversational", "Warm", "Audiobook")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Fenrir",
                displayName = "Fenrir",
                acousticProfile = "Passionate, resonant, impactful tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Dynamic", "Speeches", "Presentations")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Zephyr",
                displayName = "Zephyr",
                acousticProfile = "Bright, clear, balanced warmth",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Bright", "Tutorials", "Instructional")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Orus",
                displayName = "Orus",
                acousticProfile = "Firm, grounded, confident tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Confident", "Formal", "Technical")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Umbriel",
                displayName = "Umbriel",
                acousticProfile = "Easy-going, steady, casual tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Casual", "Articles", "Relaxed")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Erinome",
                displayName = "Erinome",
                acousticProfile = "Clear, elegant, articulate tone",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Articulate", "Essays", "Non-fiction")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Laomedeia",
                displayName = "Laomedeia",
                acousticProfile = "Upbeat, dynamic, engaging tone",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Vibrant", "Study Notes", "Interactive")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Schedar",
                displayName = "Schedar",
                acousticProfile = "Even, steady, disciplined tone",
                genderPresentation = "Masculine-sounding",
                styleTags = listOf("Data", "Code", "Reference")
            ),
            VoiceProfile(
                providerId = "gemini",
                modelId = "gemini-2.0-flash",
                voiceId = "Autonoe",
                displayName = "Autonoe",
                acousticProfile = "Bright, cheerful, welcoming tone",
                genderPresentation = "Feminine-sounding",
                styleTags = listOf("Friendly", "Children", "Daily Digest")
            )
        )
    }
}
