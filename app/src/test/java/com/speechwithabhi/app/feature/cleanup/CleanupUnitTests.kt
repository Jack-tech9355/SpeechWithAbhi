package com.speechwithabhi.app.feature.cleanup

import com.speechwithabhi.app.core.model.CleanupAction
import com.speechwithabhi.app.core.model.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CleanupUnitTests {

    private val detector = DeterministicNoiseDetector()
    private val tableConversationalizer = TableConversationalizer()
    private val validator = GeminiCleanupValidator()
    private val pipeline = DocumentCleanupPipeline(detector, tableConversationalizer, validator)

    @Test
    fun detector_removesRepeatedHeaderAcrossPages() {
        val blocks = listOf(
            ContentBlock(
                blockId = "header_p1",
                pageIndex = 1,
                text = "Annual Financial Report 2024",
                normalizedTop = 0.05f,
                normalizedBottom = 0.08f
            ),
            ContentBlock(
                blockId = "body_p1",
                pageIndex = 1,
                text = "Company revenue grew by 15% this fiscal year.",
                normalizedTop = 0.3f,
                normalizedBottom = 0.4f
            ),
            ContentBlock(
                blockId = "header_p2",
                pageIndex = 2,
                text = "Annual Financial Report 2024",
                normalizedTop = 0.05f,
                normalizedBottom = 0.08f
            ),
            ContentBlock(
                blockId = "body_p2",
                pageIndex = 2,
                text = "Operational expenses remained within planned budget.",
                normalizedTop = 0.3f,
                normalizedBottom = 0.4f
            )
        )

        val decisions = detector.analyze(blocks)

        assertEquals(CleanupAction.REMOVE_CANDIDATE, decisions["header_p1"]?.action)
        assertEquals(CleanupAction.REMOVE_CANDIDATE, decisions["header_p2"]?.action)
        assertEquals(CleanupAction.KEEP, decisions["body_p1"]?.action)
        assertEquals(CleanupAction.KEEP, decisions["body_p2"]?.action)
    }

    @Test
    fun detector_removesStandalonePageNumbers() {
        val blocks = listOf(
            ContentBlock(
                blockId = "pg_1",
                pageIndex = 1,
                text = "1",
                normalizedTop = 0.95f,
                normalizedBottom = 0.98f
            ),
            ContentBlock(
                blockId = "pg_label",
                pageIndex = 2,
                text = "Page 2 of 10",
                normalizedTop = 0.95f,
                normalizedBottom = 0.98f
            )
        )

        val decisions = detector.analyze(blocks)

        assertEquals(CleanupAction.REMOVE_CANDIDATE, decisions["pg_1"]?.action)
        assertEquals(CleanupAction.REMOVE_CANDIDATE, decisions["pg_label"]?.action)
    }

    @Test
    fun detector_strictlyProtectsNumberedListsAndClauses() {
        assertTrue("Numbered list item must be protected", detector.isProtectedContent("1. Overview of Neural Networks"))
        assertTrue("Sub-clause must be protected", detector.isProtectedContent("2.1 Data Collection Pipeline"))
        assertTrue("Parenthesized list must be protected", detector.isProtectedContent("(a) First prerequisite"))
        assertTrue("Step instruction must be protected", detector.isProtectedContent("Step 3: Run the database migration"))
        assertTrue("Question number must be protected", detector.isProtectedContent("Question 4: What is backpropagation?"))
        assertTrue("Legal section must be protected", detector.isProtectedContent("Section 12.3 Termination of Agreement"))
        assertTrue("Mathematical equation must be protected", detector.isProtectedContent("E = mc^2"))
        assertTrue("Historical date must be protected", detector.isProtectedContent("1947"))
    }

    @Test
    fun tableConversationalizer_convertsPipedTableIntoNaturalSpeech() {
        val rawTable = """
            | Feature | Basic Plan | Pro Plan |
            |---------|------------|----------|
            | Storage | 5 GB       | 50 GB    |
            | Support | Email      | 24/7 Phone|
        """.trimIndent()

        assertTrue(tableConversationalizer.isTable(rawTable))

        val spoken = tableConversationalizer.conversationalize(rawTable)
        assertTrue(spoken.contains("Table details:"))
        assertTrue(spoken.contains("For Storage"))
        assertTrue(spoken.contains("Basic Plan is 5 GB"))
        assertTrue(spoken.contains("Pro Plan is 50 GB"))
        assertTrue(spoken.contains("For Support"))
    }

    @Test
    fun tableConversationalizer_detectsTabSeparatedTables() {
        val tabTable = "Name\tAge\tCity\nAlice\t30\tDelhi\nBob\t25\tMumbai"
        assertTrue(tableConversationalizer.isTable(tabTable))
        val spoken = tableConversationalizer.conversationalize(tabTable)
        assertTrue(spoken.contains("Table details:"))
        assertTrue(spoken.contains("Alice"))
    }

    @Test
    fun geminiValidator_handlesValidAndInvalidJsonSafely() {
        val validJson = """
            {
              "document_language": "en",
              "blocks": [
                {
                  "block_id": "blk_1",
                  "action": "keep",
                  "reason": "body",
                  "cleaned_text": "Sample text",
                  "confidence": 0.99
                }
              ],
              "uncertain_block_ids": []
            }
        """.trimIndent()

        val result = validator.validateAndMap(validJson, setOf("blk_1"))
        assertNotNull(result)
        assertEquals("keep", result?.get("blk_1")?.action)

        // Invalid JSON must safely return null without throwing exception
        val invalidJson = "Not a JSON output at all"
        val fallbackResult = validator.validateAndMap(invalidJson, setOf("blk_1"))
        assertNull(fallbackResult)
    }

    @Test
    fun pipeline_preservesBothOriginalAndCleanedTextWithAuditTrail() {
        val blocks = listOf(
            ContentBlock(
                blockId = "header",
                pageIndex = 1,
                text = "Doc Header",
                normalizedTop = 0.05f,
                normalizedBottom = 0.08f
            ),
            ContentBlock(
                blockId = "p1",
                pageIndex = 1,
                text = "1. Introduction to Speech With Abhi",
                normalizedTop = 0.2f,
                normalizedBottom = 0.3f
            )
        )

        val cleanResult = pipeline.process(
            documentId = "doc_123",
            blocks = blocks
        )

        assertEquals(2, cleanResult.sections.size)
        val introSection = cleanResult.sections.find { it.originalText.contains("Introduction") }
        assertNotNull(introSection)
        assertFalse(introSection!!.isRemovedCandidate)
        assertEquals("1. Introduction to Speech With Abhi", introSection.cleanedText)
    }
}
