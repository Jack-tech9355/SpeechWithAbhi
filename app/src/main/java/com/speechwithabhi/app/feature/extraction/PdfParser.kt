package com.speechwithabhi.app.feature.extraction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.speechwithabhi.app.core.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Intelligent dual-mode PDF parser:
 * 1. Fast native PDF text stream extraction.
 * 2. Automatic fallback to ML Kit OCR via Android PdfRenderer if pages are scanned or image-based.
 * Detects password protection and encrypted documents with clear, honest error reporting.
 */
class PdfParser(
    private val context: Context,
    private val imageOcrParser: ImageOcrParser = ImageOcrParser()
) : DocumentParser {

    override fun canHandle(mimeType: String, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType == "application/pdf" || lower.endsWith(".pdf")
    }

    override suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult = withContext(Dispatchers.IO) {
        onProgress(ParseProgress.Extracting("Inspecting PDF structure..."))

        // First verify PDF header
        checkPdfHeader(file)

        val blocks = mutableListOf<ContentBlock>()
        var detectedTitle = ""
        var isScannedDocument = false
        var order = 0

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            onProgress(ParseProgress.Extracting("Analyzing $pageCount PDF pages..."))

            // Attempt fast text stream extraction first
            val nativeTextByPage = extractNativeTextStreams(file)

            for (pageIndex in 0 until pageCount) {
                val pageNumber = pageIndex + 1
                onProgress(ParseProgress.Scanning(pageNumber, pageCount))

                val pageText = nativeTextByPage[pageNumber] ?: ""

                if (pageText.length > 40) {
                    // Page has valid selectable native text
                    val pageBlocks = splitPageIntoBlocks(pageText, pageNumber, order)
                    order += pageBlocks.size
                    blocks.addAll(pageBlocks)
                } else {
                    // Page has no or very sparse text -> likely a scanned page! Render and OCR
                    isScannedDocument = true
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val ocrResult = imageOcrParser.parseBitmap(bitmap, pageNumber)
                    ocrResult.forEach { block ->
                        blocks.add(block.copy(rawOrder = order++))
                    }
                    bitmap.recycle()
                }
            }

            if (blocks.isNotEmpty()) {
                detectedTitle = blocks.first().text.take(60)
            }

            ParseResult(
                blocks = blocks,
                pageCount = pageCount,
                detectedTitle = detectedTitle,
                isScannedDocument = isScannedDocument
            )
        } catch (e: SecurityException) {
            throw IllegalArgumentException("This PDF is password-protected or encrypted. Please provide an unlocked document.", e)
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true) {
                throw IllegalArgumentException("Password-protected PDF cannot be parsed.", e)
            }
            throw IllegalArgumentException("Failed to parse PDF document: ${e.message}", e)
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun checkPdfHeader(file: File) {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(1024)
            val read = raf.read(header)
            val headerStr = String(header, 0, minOf(read, header.size))
            if (!headerStr.contains("%PDF-")) {
                throw IllegalArgumentException("Not a valid PDF file (missing %PDF- header).")
            }
            if (headerStr.contains("/Encrypt")) {
                throw IllegalArgumentException("This PDF is encrypted. Encrypted documents are not supported.")
            }
        }
    }

    /**
     * Extracts uncompressed and readable text tokens from PDF content streams.
     * Uses bounded streaming to prevent OutOfMemory errors on large documents.
     */
    private fun extractNativeTextStreams(file: File): Map<Int, String> {
        val pageTexts = mutableMapOf<Int, StringBuilder>()
        try {
            val maxBytesToRead = minOf(file.length(), 25L * 1024 * 1024).toInt()
            val bytes = ByteArray(maxBytesToRead)
            file.inputStream().use { stream ->
                var totalRead = 0
                while (totalRead < maxBytesToRead) {
                    val read = stream.read(bytes, totalRead, maxBytesToRead - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
            }
            val content = String(bytes, 0, bytes.size, Charsets.ISO_8859_1)
            var currentPage = 1

            // Search for BT ... ET text blocks
            val btRegex = Regex("""BT\s*(.*?)\s*ET""", RegexOption.DOT_MATCHES_ALL)
            val tjRegex = Regex("""\((.*?)\)\s*T[jJ]|\[(.*?)\]\s*TJ""")

            btRegex.findAll(content).forEach { match ->
                val blockContent = match.groupValues[1]
                tjRegex.findAll(blockContent).forEach { tjMatch ->
                    val text1 = tjMatch.groupValues[1]
                    val text2 = tjMatch.groupValues[2]
                    val rawText = if (text1.isNotEmpty()) text1 else parseTJArray(text2)
                    val unescaped = unescapePdfString(rawText)
                    if (unescaped.isNotBlank()) {
                        val sb = pageTexts.getOrPut(currentPage) { StringBuilder() }
                        sb.append(unescaped).append(" ")
                    }
                }
            }
        } catch (_: Exception) {
            // If stream extraction encounters binary complexity, rendering + OCR will handle it
        }
        return pageTexts.mapValues { it.value.toString() }
    }

    private fun parseTJArray(tjArray: String): String {
        val result = StringBuilder()
        val arrayRegex = Regex("""\((.*?)\)""")
        arrayRegex.findAll(tjArray).forEach {
            result.append(it.groupValues[1])
        }
        return result.toString()
    }

    private fun unescapePdfString(input: String): String {
        return input
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\r", "\n")
            .replace("\\n", "\n")
            .replace(Regex("""\\(\d{3})""")) {
                val octal = it.groupValues[1].toInt(8)
                octal.toChar().toString()
            }
            .trim()
    }

    private fun splitPageIntoBlocks(text: String, pageNumber: Int, startingOrder: Int): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val paragraphs = text.split(Regex("""\n{2,}|\.\s{2,}"""))
        var order = startingOrder

        paragraphs.forEachIndexed { index, p ->
            val trimmed = p.trim()
            if (trimmed.isNotEmpty()) {
                val isFirst = index == 0
                val isLast = index == paragraphs.lastIndex
                val isPageNum = trimmed.matches(Regex("""^\d+$|^Page\s+\d+(\s+of\s+\d+)?$""", RegexOption.IGNORE_CASE))

                blocks.add(
                    ContentBlock(
                        blockId = UUID.randomUUID().toString(),
                        pageIndex = pageNumber,
                        text = trimmed,
                        normalizedTop = if (isFirst) 0.05f else if (isLast) 0.95f else 0.5f,
                        normalizedBottom = if (isFirst) 0.1f else if (isLast) 0.98f else 0.5f,
                        isHeaderCandidate = isFirst && trimmed.length < 80,
                        isFooterCandidate = isLast && trimmed.length < 80,
                        isPageNumberCandidate = isPageNum,
                        isProtectedClause = isNumberedClause(trimmed),
                        rawOrder = order++
                    )
                )
            }
        }
        return blocks
    }

    private fun isNumberedClause(text: String): Boolean {
        return text.matches(Regex("""^(\d+(\.\d+)*[\.\):]|\([0-9a-zA-Z]+\)|Section\s+\d+|Article\s+\d+|Clause\s+\d+).*""", RegexOption.IGNORE_CASE))
    }
}
