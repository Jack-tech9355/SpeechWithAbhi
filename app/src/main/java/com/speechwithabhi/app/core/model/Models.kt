package com.speechwithabhi.app.core.model

import kotlinx.serialization.Serializable

/**
 * Status of document parsing.
 */
@Serializable
enum class ParseStatus {
    IDLE,
    PARSING,
    PARSED,
    ERROR
}

/**
 * Semantic type of content inside a document.
 */
@Serializable
enum class SectionType {
    TITLE,
    HEADING,
    PARAGRAPH,
    LIST_ITEM,
    TABLE,
    CODE,
    FORMULA,
    CAPTION,
    NOTE;

    companion object {
        fun fromString(value: String): SectionType {
            return when (value.trim().uppercase()) {
                "TITLE" -> TITLE
                "HEADING" -> HEADING
                "PARAGRAPH", "BODY_PARAGRAPH", "BODY" -> PARAGRAPH
                "LIST_ITEM", "LIST" -> LIST_ITEM
                "TABLE" -> TABLE
                "CODE" -> CODE
                "FORMULA" -> FORMULA
                "CAPTION" -> CAPTION
                "NOTE" -> NOTE
                else -> try { valueOf(value.trim()) } catch (_: Exception) { PARAGRAPH }
            }
        }
    }
}

/**
 * Action decided during document cleanup.
 */
@Serializable
enum class CleanupAction {
    KEEP,
    REMOVE_CANDIDATE,
    MERGE_DUPLICATE,
    NEEDS_REVIEW
}

/**
 * Narration modes available for the user.
 */
@Serializable
enum class NarrationMode {
    FAITHFUL_NARRATOR,      // Exact narration, no hallucinations or filler
    FRIENDLY_PRESENTER,     // Natural brief transitions between major chapters
    SIMPLIFY_EXPLAIN        // Clear labelled verbal summary
}

/**
 * Sensitivity level for deterministic noise filtering.
 */
@Serializable
enum class CleanupSensitivity {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE
}

/**
 * Status of synthesis for an audio chunk.
 */
@Serializable
enum class ChunkStatus {
    PENDING,
    SYNTHESIZING,
    READY,
    FAILED
}

/**
 * Representation of an imported document.
 */
@Serializable
data class ImportedDocument(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sourceUri: String,
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val importedAt: Long = System.currentTimeMillis(),
    val parseStatus: ParseStatus = ParseStatus.IDLE,
    val detectedLanguage: String? = null,
    val contentHash: String? = null,
    val errorMessage: String? = null
)

/**
 * Structural section of document text.
 */
@Serializable
data class DocumentSection(
    val id: String,
    val documentId: String,
    val orderIndex: Int,
    val title: String = "",
    val sectionType: SectionType = SectionType.PARAGRAPH,
    val originalText: String,
    val cleanedText: String,
    val translatedText: String? = null,
    val isSelected: Boolean = true,
    val isRemovedCandidate: Boolean = false,
    val removalReason: String? = null,
    val userEdited: Boolean = false,
    val confidence: Float = 1.0f
) {
    /**
     * Resolves the text that should be read aloud based on translation settings.
     */
    fun getSpokenText(useTranslation: Boolean): String {
        return if (useTranslation && !translatedText.isNullOrBlank()) {
            translatedText
        } else if (cleanedText.isNotBlank()) {
            cleanedText
        } else {
            originalText
        }
    }
}

/**
 * Low-level text block extracted from raw document parser.
 */
@Serializable
data class ContentBlock(
    val blockId: String,
    val pageIndex: Int,
    val text: String,
    val normalizedTop: Float = 0.5f,     // 0.0 top of page, 1.0 bottom of page
    val normalizedBottom: Float = 0.5f,
    val isHeaderCandidate: Boolean = false,
    val isFooterCandidate: Boolean = false,
    val isPageNumberCandidate: Boolean = false,
    val isProtectedClause: Boolean = false,
    val rawOrder: Int = 0
)

/**
 * Granular chunk of text prepared for speech synthesis and continuous playback.
 */
@Serializable
data class NarrationChunk(
    val id: String,
    val documentId: String,
    val sectionId: String,
    val orderIndex: Int,
    val text: String,
    val language: String = "en",
    val audioCacheUri: String? = null,
    val durationMs: Long? = null,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val errorMessage: String? = null
)

/**
 * Selectable voice profile with sound characteristics.
 */
@Serializable
data class VoiceProfile(
    val providerId: String,             // "gemini", "native_tts", "cloud_tts"
    val modelId: String,
    val voiceId: String,                // e.g. "Kore", "Puck", "Charon"
    val displayName: String,
    val acousticProfile: String,        // e.g. "Warm, clear, resonant"
    val genderPresentation: String,    // "Feminine-sounding", "Masculine-sounding", "Neutral"
    val styleTags: List<String> = emptyList(),
    val supportedLanguages: List<String> = listOf("en", "hi"),
    val isAvailable: Boolean = true
)

/**
 * User playback bookmark.
 */
@Serializable
data class PlaybackBookmark(
    val id: String,
    val documentId: String,
    val sectionId: String,
    val chunkId: String,
    val title: String,
    val snippet: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Playback state snapshot for UI and service synchronization.
 */
@Serializable
data class PlaybackState(
    val documentId: String? = null,
    val documentTitle: String = "",
    val activeSectionId: String? = null,
    val activeChunkId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeVoiceId: String = "Kore",
    val activeProvider: String = "gemini",
    val isBuffering: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val errorMessage: String? = null
)

/**
 * Result returned by the deterministic cleanup engine.
 */
data class CleanResult(
    val originalBlockCount: Int,
    val cleanedBlockCount: Int,
    val removedCandidateCount: Int,
    val detectedNoisePatterns: List<String>,
    val sections: List<DocumentSection>
)
