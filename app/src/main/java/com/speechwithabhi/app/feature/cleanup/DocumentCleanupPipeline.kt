package com.speechwithabhi.app.feature.cleanup

import com.speechwithabhi.app.core.model.CleanResult
import com.speechwithabhi.app.core.model.CleanupAction
import com.speechwithabhi.app.core.model.CleanupSensitivity
import com.speechwithabhi.app.core.model.ContentBlock
import com.speechwithabhi.app.core.model.DocumentSection
import com.speechwithabhi.app.core.model.SectionType
import java.util.UUID

/**
 * End-to-end intelligent document cleanup pipeline.
 * Executes multi-pass deterministic filtering and table conversationalization.
 * Produces structured DocumentSections with a full audit trail for user restoration.
 */
class DocumentCleanupPipeline(
    private val noiseDetector: DeterministicNoiseDetector = DeterministicNoiseDetector(),
    private val tableConversationalizer: TableConversationalizer = TableConversationalizer(),
    private val geminiValidator: GeminiCleanupValidator = GeminiCleanupValidator()
) {

    /**
     * Executes cleanup on raw content blocks extracted from a document.
     */
    fun process(
        documentId: String,
        blocks: List<ContentBlock>,
        sensitivity: CleanupSensitivity = CleanupSensitivity.BALANCED,
        geminiRawJsonResponse: String? = null
    ): CleanResult {
        val deterministicDecisions = noiseDetector.analyze(blocks)

        // Try AI validation if Gemini response is provided
        val expectedIds = blocks.map { it.blockId }.toSet()
        val aiDecisions = if (!geminiRawJsonResponse.isNullOrBlank()) {
            geminiValidator.validateAndMap(geminiRawJsonResponse, expectedIds)
        } else {
            null
        }

        val sections = mutableListOf<DocumentSection>()
        val detectedNoisePatterns = mutableListOf<String>()
        var removedCount = 0

        for ((index, block) in blocks.withIndex()) {
            val deterministic = deterministicDecisions[block.blockId]
            val ai = aiDecisions?.get(block.blockId)

            val rawText = block.text.trim()
            if (rawText.isEmpty()) continue

            // Determine final action:
            // Protected content is NEVER removed, regardless of AI or sensitivity
            val isProtected = noiseDetector.isProtectedContent(rawText) || block.isProtectedClause

            val isRemoveCandidate = if (isProtected) {
                false
            } else if (ai != null) {
                ai.action == "remove_candidate"
            } else {
                deterministic?.action == CleanupAction.REMOVE_CANDIDATE
            }

            val reason = if (isProtected) {
                "protected_item"
            } else {
                ai?.reason ?: deterministic?.reason ?: "body_content"
            }

            if (isRemoveCandidate) {
                removedCount++
                if (reason !in detectedNoisePatterns) {
                    detectedNoisePatterns.add(reason)
                }
            }

            // Conversationalize tables if applicable
            val isTable = tableConversationalizer.isTable(rawText)
            val cleanedText = if (isTable) {
                tableConversationalizer.conversationalize(rawText)
            } else if (ai != null && ai.cleaned_text.isNotBlank()) {
                ai.cleaned_text
            } else {
                rawText
            }

            val sectionType = when {
                isTable -> SectionType.TABLE
                block.isHeaderCandidate && rawText.length < 80 -> SectionType.HEADING
                rawText.matches(Regex("""^(\d+(\.\d+)*[\.\):]|\([0-9a-zA-Z]+\)|Step\s+\d+).*""")) -> SectionType.LIST_ITEM
                else -> SectionType.PARAGRAPH
            }

            sections.add(
                DocumentSection(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    orderIndex = index,
                    title = if (sectionType == SectionType.HEADING) rawText else "",
                    sectionType = sectionType,
                    originalText = rawText,
                    cleanedText = cleanedText,
                    isSelected = !isRemoveCandidate, // Default unselects removed noise, allowing one-tap restore
                    isRemovedCandidate = isRemoveCandidate,
                    removalReason = reason,
                    confidence = ai?.confidence ?: deterministic?.confidence ?: 0.95f
                )
            )
        }

        return CleanResult(
            originalBlockCount = blocks.size,
            cleanedBlockCount = sections.count { !it.isRemovedCandidate },
            removedCandidateCount = removedCount,
            detectedNoisePatterns = detectedNoisePatterns,
            sections = sections
        )
    }
}
