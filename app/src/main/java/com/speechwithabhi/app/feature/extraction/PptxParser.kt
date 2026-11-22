package com.speechwithabhi.app.feature.extraction

import android.util.Xml
import com.speechwithabhi.app.core.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Memory-efficient streaming XML parser for Microsoft PowerPoint (.pptx) presentations.
 * Extracts slide titles, body shapes, bullet points, and speaker notes in presentation order.
 */
class PptxParser : DocumentParser {

    override fun canHandle(mimeType: String, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
                lower.endsWith(".pptx")
    }

    override suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult = withContext(Dispatchers.IO) {
        onProgress(ParseProgress.Extracting("Analyzing PPTX presentation..."))

        val blocks = mutableListOf<ContentBlock>()
        var detectedTitle = ""
        var globalOrder = 0

        try {
            ZipFile(file).use { zip ->
                // Collect slide entries sorted by index
                val slideEntries = zip.entries().asSequence()
                    .filter { it.name.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
                    .sortedBy { entry ->
                        Regex("""slide(\d+)\.xml""").find(entry.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                    .toList()

                val totalSlides = slideEntries.size

                slideEntries.forEachIndexed { index, entry ->
                    val slideIndex = index + 1
                    onProgress(ParseProgress.Scanning(slideIndex, totalSlides))

                    zip.getInputStream(entry).use { stream ->
                        val slideBlocks = parseSlideXml(stream, slideIndex)
                        slideBlocks.forEach { block ->
                            if (detectedTitle.isEmpty() && block.text.isNotBlank()) {
                                detectedTitle = block.text.take(60)
                            }
                            blocks.add(block.copy(rawOrder = globalOrder++))
                        }
                    }

                    // Optional: Check for speaker notes in ppt/notesSlides/notesSlideX.xml
                    val notesEntry = zip.getEntry("ppt/notesSlides/notesSlide$slideIndex.xml")
                    if (notesEntry != null) {
                        zip.getInputStream(notesEntry).use { notesStream ->
                            val notesBlocks = parseSlideXml(notesStream, slideIndex)
                            notesBlocks.forEach { block ->
                                blocks.add(
                                    block.copy(
                                        text = "[Note: ${block.text}]",
                                        isProtectedClause = true,
                                        rawOrder = globalOrder++
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to extract PPTX: ${e.message}", e)
        }

        ParseResult(
            blocks = blocks,
            pageCount = blocks.maxOfOrNull { it.pageIndex } ?: 1,
            detectedTitle = detectedTitle
        )
    }

    private fun parseSlideXml(stream: InputStream, slideIndex: Int): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        val currentParagraphText = StringBuilder()
        var isFirstParagraphInShape = true

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val rawName = parser.name ?: ""
            val localName = rawName.substringAfterLast(':')

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (localName) {
                        "sp" -> {
                            isFirstParagraphInShape = true
                        }
                        "p" -> {
                            currentParagraphText.clear()
                        }
                        "t" -> {
                            val text = parser.nextText()
                            currentParagraphText.append(text)
                        }
                        "br" -> {
                            currentParagraphText.append(" ")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localName == "p") {
                        val text = currentParagraphText.toString().trim()
                        if (text.isNotEmpty()) {
                            // Check if standalone slide number
                            val isSlideNumber = text.matches(Regex("""^\d+$"""))

                            blocks.add(
                                ContentBlock(
                                    blockId = UUID.randomUUID().toString(),
                                    pageIndex = slideIndex,
                                    text = text,
                                    normalizedTop = if (isFirstParagraphInShape) 0.15f else 0.5f,
                                    normalizedBottom = 0.5f,
                                    isHeaderCandidate = isFirstParagraphInShape,
                                    isPageNumberCandidate = isSlideNumber,
                                    isProtectedClause = !isSlideNumber,
                                    rawOrder = 0
                                )
                            )
                            isFirstParagraphInShape = false
                        }
                        currentParagraphText.clear()
                    }
                }
            }
            eventType = parser.next()
        }
        return blocks
    }
}
