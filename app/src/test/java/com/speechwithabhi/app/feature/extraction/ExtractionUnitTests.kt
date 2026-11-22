package com.speechwithabhi.app.feature.extraction

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtractionUnitTests {

    @Test
    fun textParser_extractsHeadingsAndPreservesNumberedLists() = runBlocking {
        val parser = TextParser()
        val tempFile = File.createTempFile("sample_notes", ".md")
        tempFile.writeText(
            """
            # Chapter 1: Artificial Intelligence
            
            Artificial intelligence is transforming society.
            
            1. Machine Learning
            2. Deep Learning
            3. Speech Synthesis
            
            Here is a **bold concept** and some `inline code`.
            """.trimIndent()
        )

        val result = parser.parse(tempFile) { }
        tempFile.delete()

        assertEquals("Chapter 1: Artificial Intelligence", result.detectedTitle)
        assertTrue(result.blocks.isNotEmpty())

        val listItems = result.blocks.filter { it.isProtectedClause }
        assertTrue("Numbered list items should be protected clauses", listItems.isNotEmpty())

        // Verify markdown bold & code formatting was stripped
        val boldBlock = result.blocks.find { it.text.contains("bold concept") }
        assertTrue("Bold formatting syntax should be cleaned", boldBlock != null && !boldBlock.text.contains("**"))
    }

    @Test
    fun textParser_canHandleChecksExtensionsAccurately() {
        val parser = TextParser()
        assertTrue(parser.canHandle("text/plain", "notes.txt"))
        assertTrue(parser.canHandle("text/markdown", "README.md"))
        assertFalse(parser.canHandle("application/pdf", "book.pdf"))
        assertFalse(parser.canHandle("application/msword", "legacy.doc"))
    }

    @Test
    fun docxParser_canHandleDocxOnly() {
        val parser = DocxParser()
        assertTrue(parser.canHandle("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "document.docx"))
        assertFalse(parser.canHandle("application/msword", "legacy.doc"))
    }

    @Test
    fun pptxParser_canHandlePptxOnly() {
        val parser = PptxParser()
        assertTrue(parser.canHandle("application/vnd.openxmlformats-officedocument.presentationml.presentation", "slides.pptx"))
        assertFalse(parser.canHandle("application/vnd.ms-powerpoint", "slides.ppt"))
    }
}
