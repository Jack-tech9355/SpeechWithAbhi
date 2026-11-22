package com.speechwithabhi.app.feature.extraction

import android.content.Context
import java.io.File

/**
 * Registry and dispatcher for all supported document parsers.
 * Communicates format limitations honestly and prevents silent parser failures.
 */
class DocumentParserRegistry(context: Context) {

    private val imageOcrParser = ImageOcrParser()
    private val parsers: List<DocumentParser> = listOf(
        PdfParser(context, imageOcrParser),
        DocxParser(),
        PptxParser(),
        TextParser(),
        imageOcrParser
    )

    /**
     * Resolves the matching parser or throws a descriptive, user-friendly exception.
     */
    fun getParser(mimeType: String, filename: String): DocumentParser {
        val lower = filename.lowercase()

        // Explicit check for legacy unsupported formats
        if (lower.endsWith(".doc")) {
            throw UnsupportedOperationException("Legacy Word (.doc) binary format is not directly supported. Please convert it to modern Word (.docx) or PDF.")
        }
        if (lower.endsWith(".ppt")) {
            throw UnsupportedOperationException("Legacy PowerPoint (.ppt) binary format is not directly supported. Please convert it to modern presentation (.pptx) or PDF.")
        }
        if (lower.endsWith(".ddx")) {
            throw UnsupportedOperationException("The .ddx extension is ambiguous and unsupported. If this is a Word document, please rename or save as .docx.")
        }

        val matchingParser = parsers.firstOrNull { it.canHandle(mimeType, filename) }
        if (matchingParser == null) {
            val extension = filename.substringAfterLast('.', "unknown")
            throw UnsupportedOperationException("The file format '.$extension' ($mimeType) is not supported. Supported formats: PDF, DOCX, PPTX, TXT, Markdown, and Images (JPG/PNG).")
        }

        return matchingParser
    }

    /**
     * Parses the file using the resolved parser.
     */
    suspend fun parseDocument(
        file: File,
        mimeType: String,
        filename: String,
        onProgress: (ParseProgress) -> Unit
    ): ParseResult {
        val parser = getParser(mimeType, filename)
        return parser.parse(file, onProgress)
    }
}
