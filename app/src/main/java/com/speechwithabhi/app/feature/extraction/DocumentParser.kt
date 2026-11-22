package com.speechwithabhi.app.feature.extraction

import com.speechwithabhi.app.core.model.ContentBlock
import java.io.File

/**
 * Progress updates during document extraction.
 */
sealed interface ParseProgress {
    data class Scanning(val current: Int, val total: Int) : ParseProgress
    data class Extracting(val message: String) : ParseProgress
}

/**
 * Result of document extraction containing raw content blocks with structural layout coordinates.
 */
data class ParseResult(
    val blocks: List<ContentBlock>,
    val pageCount: Int,
    val detectedTitle: String = "",
    val isScannedDocument: Boolean = false,
    val warnings: List<String> = emptyList()
)

/**
 * Interface contract for format-specific document extraction.
 */
interface DocumentParser {
    /**
     * Checks if this parser can handle the provided MIME type and file extension.
     */
    fun canHandle(mimeType: String, filename: String): Boolean

    /**
     * Parses the file into ordered, structurally tagged content blocks.
     */
    suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult
}
