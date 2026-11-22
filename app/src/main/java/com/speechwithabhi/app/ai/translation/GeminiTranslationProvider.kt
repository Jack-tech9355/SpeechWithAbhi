package com.speechwithabhi.app.ai.translation

import com.speechwithabhi.app.ai.gemini.GeminiApiClient
import com.speechwithabhi.app.core.model.DocumentSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-fidelity multilingual translation orchestrator powered by Gemini.
 * Strictly preserves names, numbers, units, formulas, and technical terminology.
 */
class GeminiTranslationProvider(
    private val apiClient: GeminiApiClient
) {

    /**
     * Translates a list of document sections into the desired target language.
     */
    suspend fun translateSections(
        sections: List<DocumentSection>,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String> = emptyMap()
    ): List<DocumentSection> = withContext(Dispatchers.IO) {
        if (sourceLanguage.equals(targetLanguage, ignoreCase = true)) {
            return@withContext sections
        }

        val glossaryInstructions = if (glossary.isNotEmpty()) {
            "Strictly follow this vocabulary glossary and do not alter these terms: " +
                    glossary.entries.joinToString(", ") { "${it.key} -> ${it.value}" }
        } else ""

        val systemInstruction = """
            You are a faithful document translation engine.
            Translate the provided content from $sourceLanguage to $targetLanguage.
            Preserve all facts, numbers, units, proper nouns, references, formulas, code identifiers, and paragraph order.
            Do not summarize, embellish, or add conversational explanations.
            $glossaryInstructions
        """.trimIndent()

        sections.map { section ->
            val textToTranslate = section.cleanedText.ifBlank { section.originalText }
            if (textToTranslate.isBlank() || section.isRemovedCandidate) {
                section
            } else {
                try {
                    val translated = apiClient.generateContent(
                        prompt = textToTranslate,
                        systemInstruction = systemInstruction
                    ).trim()
                    section.copy(translatedText = translated)
                } catch (_: Exception) {
                    // On failure, gracefully retain original text
                    section
                }
            }
        }
    }
}
