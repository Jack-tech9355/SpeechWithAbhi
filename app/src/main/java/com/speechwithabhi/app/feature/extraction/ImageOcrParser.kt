package com.speechwithabhi.app.feature.extraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.speechwithabhi.app.core.model.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Image text extraction using on-device ML Kit Text Recognition.
 * Sorts detected text blocks into natural reading order (top-to-bottom, left-to-right)
 * and normalizes geometric layout coordinates.
 */
class ImageOcrParser : DocumentParser {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun canHandle(mimeType: String, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType.startsWith("image/") ||
                lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".bmp")
    }

    override suspend fun parse(file: File, onProgress: (ParseProgress) -> Unit): ParseResult = withContext(Dispatchers.IO) {
        onProgress(ParseProgress.Extracting("Loading image for OCR..."))

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Cannot decode image file. File may be corrupt or unsupported.")

        try {
            val blocks = parseBitmap(bitmap, pageIndex = 1)
            val detectedTitle = blocks.firstOrNull()?.text?.take(60) ?: "Image Document"

            ParseResult(
                blocks = blocks,
                pageCount = 1,
                detectedTitle = detectedTitle,
                isScannedDocument = true
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Parses an in-memory Bitmap and extracts sorted ContentBlocks.
     */
    suspend fun parseBitmap(bitmap: Bitmap, pageIndex: Int): List<ContentBlock> = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = try {
            recognizer.process(image).await()
        } catch (e: Exception) {
            // Fallback gracefully if ML Kit play services are not available
            return@withContext emptyList()
        }

        val height = bitmap.height.toFloat().coerceAtLeast(1f)

        // Sort recognized text blocks top-to-bottom, left-to-right
        val sortedBlocks = visionText.textBlocks.sortedWith(
            compareBy<Text.TextBlock> {
                it.boundingBox?.top ?: 0
            }.thenBy {
                it.boundingBox?.left ?: 0
            }
        )

        var order = 0
        sortedBlocks.mapNotNull { block ->
            val text = block.text.trim()
            if (text.isEmpty()) return@mapNotNull null

            val top = (block.boundingBox?.top ?: 0) / height
            val bottom = (block.boundingBox?.bottom ?: 0) / height

            val isHeader = top < 0.10f && text.length < 80
            val isFooter = bottom > 0.90f && text.length < 80
            val isPageNum = text.matches(Regex("""^\d+$|^Page\s+\d+(\s+of\s+\d+)?$""", RegexOption.IGNORE_CASE))

            ContentBlock(
                blockId = UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                text = text,
                normalizedTop = top,
                normalizedBottom = bottom,
                isHeaderCandidate = isHeader,
                isFooterCandidate = isFooter,
                isPageNumberCandidate = isPageNum,
                isProtectedClause = isNumberedClauseOrItem(text),
                rawOrder = order++
            )
        }
    }

    private fun isNumberedClauseOrItem(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.matches(Regex("""^(\d+(\.\d+)*[\.\):]|\([0-9a-zA-Z]+\)|Step\s+\d+|Question\s+\d+|Q\d+[\.:]).*""", RegexOption.IGNORE_CASE))
    }
}
