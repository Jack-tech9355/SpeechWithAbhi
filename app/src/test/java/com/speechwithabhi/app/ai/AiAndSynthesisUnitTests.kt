package com.speechwithabhi.app.ai

import com.speechwithabhi.app.ai.narration.NarrationPreparationProvider
import com.speechwithabhi.app.ai.synthesis.GeminiSpeechSynthesisProvider
import com.speechwithabhi.app.core.model.DocumentSection
import com.speechwithabhi.app.core.model.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAndSynthesisUnitTests {

    private val narrationPreparer = NarrationPreparationProvider()

    @Test
    fun narrationPreparer_chunksTextRespectingSentenceBoundariesAndAbbreviations() {
        val longParagraph = """
            Dr. APJ Abdul Kalam was an Indian aerospace scientist and statesman. 
            He served as the 11th President of India from 2002 to 2007. 
            He was known as the Missile Man of India for his work on ballistic missiles. 
            His inspiring words motivated millions of students worldwide.
        """.trimIndent()

        val section = DocumentSection(
            id = "sec_1",
            documentId = "doc_1",
            orderIndex = 0,
            originalText = longParagraph,
            cleanedText = longParagraph,
            isSelected = true
        )

        val chunks = narrationPreparer.prepareNarrationChunks(
            documentId = "doc_1",
            sections = listOf(section)
        )

        assertTrue(chunks.isNotEmpty())
        // Ensure "Dr." abbreviation did not cause an improper sentence split
        assertTrue(chunks.first().text.contains("Dr. APJ Abdul Kalam"))
    }

    @Test
    fun geminiVoices_containsExactly12VerifiedCelestialProfiles() {
        val voices = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES
        assertEquals(12, voices.size)

        val voiceNames = voices.map { it.voiceId }
        assertTrue(voiceNames.contains("Kore"))
        assertTrue(voiceNames.contains("Puck"))
        assertTrue(voiceNames.contains("Charon"))
        assertTrue(voiceNames.contains("Aoede"))
        assertTrue(voiceNames.contains("Fenrir"))
        assertTrue(voiceNames.contains("Zephyr"))
        assertTrue(voiceNames.contains("Orus"))
        assertTrue(voiceNames.contains("Umbriel"))
        assertTrue(voiceNames.contains("Erinome"))
        assertTrue(voiceNames.contains("Laomedeia"))
        assertTrue(voiceNames.contains("Schedar"))
        assertTrue(voiceNames.contains("Autonoe"))
    }

    @Test
    fun sectionType_fromStringHandlesLegacyAndUnknownValuesSafely() {
        assertEquals(SectionType.PARAGRAPH, SectionType.fromString("BODY_PARAGRAPH"))
        assertEquals(SectionType.PARAGRAPH, SectionType.fromString("BODY"))
        assertEquals(SectionType.PARAGRAPH, SectionType.fromString("paragraph"))
        assertEquals(SectionType.HEADING, SectionType.fromString("HEADING"))
        assertEquals(SectionType.TITLE, SectionType.fromString("TITLE"))
        assertEquals(SectionType.LIST_ITEM, SectionType.fromString("LIST_ITEM"))
        assertEquals(SectionType.LIST_ITEM, SectionType.fromString("LIST"))
        assertEquals(SectionType.TABLE, SectionType.fromString("TABLE"))
        assertEquals(SectionType.PARAGRAPH, SectionType.fromString("UNKNOWN_LEGACY_TYPE"))
    }
}
