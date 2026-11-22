package com.speechwithabhi.app.ai.narration

import com.speechwithabhi.app.core.model.DocumentSection
import com.speechwithabhi.app.core.model.NarrationChunk
import java.util.UUID

/**
 * Prepares approved document sections for continuous, natural spoken narration.
 * Performs sentence-aware semantic chunking with a bounded token budget.
 */
class NarrationPreparationProvider {

    /**
     * Chunks document sections into sequential, audio-ready NarrationChunks.
     * Max target chunk length is ~600 characters to ensure low-latency synthesis and continuous lookahead buffering.
     */
    fun prepareNarrationChunks(
        documentId: String,
        sections: List<DocumentSection>,
        useTranslation: Boolean = false,
        language: String = "en"
    ): List<NarrationChunk> {
        val chunks = mutableListOf<NarrationChunk>()
        var globalIndex = 0

        val activeSections = sections.filter { it.isSelected && !it.isRemovedCandidate }

        for (section in activeSections) {
            val spokenText = section.getSpokenText(useTranslation).trim()
            if (spokenText.isBlank()) continue

            val sentences = splitIntoSentences(spokenText)
            val currentChunk = StringBuilder()

            for (sentence in sentences) {
                if (currentChunk.length + sentence.length > MAX_CHUNK_CHARACTERS && currentChunk.isNotEmpty()) {
                    chunks.add(
                        NarrationChunk(
                            id = UUID.randomUUID().toString(),
                            documentId = documentId,
                            sectionId = section.id,
                            orderIndex = globalIndex++,
                            text = currentChunk.toString().trim(),
                            language = language
                        )
                    )
                    currentChunk.clear()
                }

                if (currentChunk.isNotEmpty()) {
                    currentChunk.append(" ")
                }
                currentChunk.append(sentence)
            }

            if (currentChunk.isNotEmpty()) {
                chunks.add(
                    NarrationChunk(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        sectionId = section.id,
                        orderIndex = globalIndex++,
                        text = currentChunk.toString().trim(),
                        language = language
                    )
                )
            }
        }

        return chunks
    }

    /**
     * Splits text into individual sentences while respecting common abbreviations (Mr., Dr., e.g., i.e.).
     */
    private fun splitIntoSentences(text: String): List<String> {
        val normalized = text
            .replace(Regex("""(?<=\bMr)\."""), "@DOT@")
            .replace(Regex("""(?<=\bMrs)\."""), "@DOT@")
            .replace(Regex("""(?<=\bDr)\."""), "@DOT@")
            .replace(Regex("""(?<=\be\.g)\."""), "@DOT@")
            .replace(Regex("""(?<=\bi\.e)\."""), "@DOT@")
            .replace(Regex("""(?<=\bvs)\."""), "@DOT@")

        val rawSentences = normalized.split(Regex("""(?<=[.!?])\s+"""))

        return rawSentences.map {
            it.replace("@DOT@", ".").trim()
        }.filter { it.isNotBlank() }
    }

    companion object {
        const val MAX_CHUNK_CHARACTERS = 600
    }
}
