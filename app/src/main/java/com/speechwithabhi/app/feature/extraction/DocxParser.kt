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
 * Memory-efficient, high-speed streaming XML parser for Microsoft Word (.docx) documents.
 * Extracts paragraphs, headings, bullet lists, and tables directly from word/document.xml.
 */
class DocxParser : DocumentParser {

    override fun canHandle(mimeType: String, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                lower.endsWith(".docx")
    }

    override suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult = withContext(Dispatchers.IO) {
        onProgress(ParseProgress.Extracting("Opening DOCX archive..."))

        val blocks = mutableListOf<ContentBlock>()
        var detectedTitle = ""
        var order = 0

        try {
            ZipFile(file).use { zip ->
                val documentEntry = zip.getEntry("word/document.xml")
                    ?: throw IllegalArgumentException("Corrupt DOCX: Missing word/document.xml")

                zip.getInputStream(documentEntry).use { stream ->
                    parseDocumentXml(stream) { text, isHeading, isTable, isList ->
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            if (detectedTitle.isEmpty() && isHeading) {
                                detectedTitle = trimmed
                            }

                            blocks.add(
                                ContentBlock(
                                    blockId = UUID.randomUUID().toString(),
                                    pageIndex = 1,
                                    text = trimmed,
                                    normalizedTop = if (isHeading) 0.1f else 0.5f,
                                    normalizedBottom = 0.5f,
                                    isHeaderCandidate = false,
                                    isFooterCandidate = false,
                                    isProtectedClause = isList || isHeading || isTable,
                                    rawOrder = order++
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to extract DOCX: ${e.message}", e)
        }

        if (detectedTitle.isEmpty() && blocks.isNotEmpty()) {
            detectedTitle = blocks.first().text.take(60)
        }

        ParseResult(
            blocks = blocks,
            pageCount = 1,
            detectedTitle = detectedTitle
        )
    }

    private fun parseDocumentXml(
        stream: InputStream,
        onParagraph: (text: String, isHeading: Boolean, isTable: Boolean, isList: Boolean) -> Unit
    ) {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        val currentParagraphText = StringBuilder()
        var insideParagraph = false
        var insideTable = false
        var isHeading = false
        var isList = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val rawName = parser.name ?: ""
            val localName = rawName.substringAfterLast(':')

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (localName) {
                        "tbl" -> insideTable = true
                        "p" -> {
                            insideParagraph = true
                            currentParagraphText.clear()
                            isHeading = false
                            isList = false
                        }
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val")
                                ?: parser.getAttributeValue(null, "w:val")
                                ?: ""
                            if (styleVal.contains("Heading", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                isHeading = true
                            }
                        }
                        "numPr" -> {
                            isList = true
                        }
                        "t" -> {
                            val text = parser.nextText()
                            currentParagraphText.append(text)
                        }
                        "tab" -> {
                            currentParagraphText.append("\t")
                        }
                        "br" -> {
                            currentParagraphText.append(" ")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localName) {
                        "p" -> {
                            insideParagraph = false
                            val text = currentParagraphText.toString().trim()
                            if (text.isNotEmpty()) {
                                onParagraph(text, isHeading, insideTable, isList)
                            }
                            currentParagraphText.clear()
                        }
                        "tbl" -> {
                            insideTable = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }
}
