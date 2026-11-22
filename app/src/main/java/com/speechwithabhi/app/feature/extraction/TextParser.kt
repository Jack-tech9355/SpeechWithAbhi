package com.speechwithabhi.app.feature.extraction

import com.speechwithabhi.app.core.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * High-performance parser for plain text (.txt), Markdown (.md), and rich text (.rtf).
 * Cleans formatting markup while strictly preserving reading order and semantic hierarchy.
 */
class TextParser : DocumentParser {

    override fun canHandle(mimeType: String, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType.startsWith("text/") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".md") ||
                lower.endsWith(".markdown") ||
                lower.endsWith(".rtf")
    }

    override suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult = withContext(Dispatchers.IO) {
        val rawText = file.readText(Charsets.UTF_8)
        onProgress(ParseProgress.Extracting("Processing text blocks..."))

        val lines = rawText.lines()
        val blocks = mutableListOf<ContentBlock>()
        var detectedTitle = ""
        var order = 0

        val currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraph.isNotEmpty()) {
                    val text = cleanMarkdownSyntax(currentParagraph.toString().trim())
                    if (text.isNotEmpty()) {
                        blocks.add(
                            ContentBlock(
                                blockId = UUID.randomUUID().toString(),
                                pageIndex = 1,
                                text = text,
                                normalizedTop = 0.5f,
                                normalizedBottom = 0.5f,
                                isHeaderCandidate = false,
                                isFooterCandidate = false,
                                isProtectedClause = isNumberedClauseOrItem(text),
                                rawOrder = order++
                            )
                        )
                    }
                    currentParagraph.clear()
                }
                continue
            }

            // Check for Markdown heading (# Heading)
            if (trimmed.startsWith("#")) {
                if (currentParagraph.isNotEmpty()) {
                    val text = cleanMarkdownSyntax(currentParagraph.toString().trim())
                    if (text.isNotEmpty()) {
                        blocks.add(
                            ContentBlock(
                                blockId = UUID.randomUUID().toString(),
                                pageIndex = 1,
                                text = text,
                                normalizedTop = 0.5f,
                                normalizedBottom = 0.5f,
                                isProtectedClause = isNumberedClauseOrItem(text),
                                rawOrder = order++
                            )
                        )
                    }
                    currentParagraph.clear()
                }

                val headingText = trimmed.trimStart('#', ' ').trim()
                if (detectedTitle.isEmpty() && trimmed.startsWith("# ")) {
                    detectedTitle = headingText
                }

                blocks.add(
                    ContentBlock(
                        blockId = UUID.randomUUID().toString(),
                        pageIndex = 1,
                        text = headingText,
                        normalizedTop = 0.1f,
                        normalizedBottom = 0.2f,
                        isProtectedClause = true,
                        rawOrder = order++
                    )
                )
                continue
            }

            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append(" ")
            }
            currentParagraph.append(trimmed)
        }

        // Flush trailing paragraph
        if (currentParagraph.isNotEmpty()) {
            val text = cleanMarkdownSyntax(currentParagraph.toString().trim())
            if (text.isNotEmpty()) {
                blocks.add(
                    ContentBlock(
                        blockId = UUID.randomUUID().toString(),
                        pageIndex = 1,
                        text = text,
                        normalizedTop = 0.5f,
                        normalizedBottom = 0.5f,
                        isProtectedClause = isNumberedClauseOrItem(text),
                        rawOrder = order++
                    )
                )
            }
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

    private fun cleanMarkdownSyntax(input: String): String {
        return input
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1") // Bold **text**
            .replace(Regex("""\*(.*?)\*"""), "$1")     // Italic *text*
            .replace(Regex("""__(.*?)__"""), "$1")     // Underline __text__
            .replace(Regex("""~~(.*?)~~"""), "$1")     // Strikethrough ~~text~~
            .replace(Regex("""\[(.*?)\]\((.*?)\)"""), "$1") // Links [text](url)
            .replace(Regex("""`{1,3}(.*?)`{1,3}"""), "$1")  // Inline code `code`
            .trim()
    }

    private fun isNumberedClauseOrItem(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.matches(Regex("""^(\d+(\.\d+)*[\.\):]|\([0-9a-zA-Z]+\)|Step\s+\d+|Question\s+\d+|Q\d+[\.:]).*""", RegexOption.IGNORE_CASE))
    }
}
