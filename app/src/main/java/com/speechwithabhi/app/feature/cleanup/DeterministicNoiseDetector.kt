package com.speechwithabhi.app.feature.cleanup

import com.speechwithabhi.app.core.model.CleanupAction
import com.speechwithabhi.app.core.model.ContentBlock
import kotlin.math.max

data class BlockNoiseDecision(
    val blockId: String,
    val action: CleanupAction,
    val reason: String?,
    val confidence: Float
)

/**
 * Deterministic multi-page noise detector.
 * Identifies repeated headers, running footers, and standalone page numbers across multiple pages
 * while strictly protecting numbered lists, legal clauses, dates, equations, and questions.
 */
class DeterministicNoiseDetector {

    /**
     * Analyzes document blocks and returns noise removal decisions with explicit reasons.
     */
    fun analyze(blocks: List<ContentBlock>): Map<String, BlockNoiseDecision> {
        val decisions = mutableMapOf<String, BlockNoiseDecision>()

        val totalPages = blocks.maxOfOrNull { it.pageIndex } ?: 1

        // 1. Group candidate headers and footers by normalized position
        val topMarginBlocks = blocks.filter { it.normalizedTop <= 0.12f }
        val bottomMarginBlocks = blocks.filter { it.normalizedBottom >= 0.88f }

        // Frequency map of normalized text appearing in headers across different pages
        val headerPageFrequency = mutableMapOf<String, MutableSet<Int>>()
        for (block in topMarginBlocks) {
            val key = normalizeForComparison(block.text)
            if (key.length >= 3 && !isProtectedContent(block.text)) {
                headerPageFrequency.getOrPut(key) { mutableSetOf() }.add(block.pageIndex)
            }
        }

        // Frequency map of normalized text appearing in footers across different pages
        val footerPageFrequency = mutableMapOf<String, MutableSet<Int>>()
        for (block in bottomMarginBlocks) {
            val key = normalizeForComparison(block.text)
            if (key.length >= 3 && !isProtectedContent(block.text)) {
                footerPageFrequency.getOrPut(key) { mutableSetOf() }.add(block.pageIndex)
            }
        }

        // 2. Classify each block
        for (block in blocks) {
            val text = block.text.trim()

            // Check if protected content (numbered list, date, clause, equation, question)
            if (isProtectedContent(text)) {
                decisions[block.blockId] = BlockNoiseDecision(
                    blockId = block.blockId,
                    action = CleanupAction.KEEP,
                    reason = "protected_clause_or_numbered_item",
                    confidence = 1.0f
                )
                continue
            }

            // Check standalone page numbers
            if (isStandalonePageNumber(text, block.normalizedTop, block.normalizedBottom)) {
                decisions[block.blockId] = BlockNoiseDecision(
                    blockId = block.blockId,
                    action = CleanupAction.REMOVE_CANDIDATE,
                    reason = "standalone_page_number",
                    confidence = 0.98f
                )
                continue
            }

            val normalized = normalizeForComparison(text)

            // Check repeated header across multiple pages
            val headerPages = headerPageFrequency[normalized]
            if (totalPages > 1 && headerPages != null && headerPages.size >= 2 && block.normalizedTop <= 0.14f) {
                decisions[block.blockId] = BlockNoiseDecision(
                    blockId = block.blockId,
                    action = CleanupAction.REMOVE_CANDIDATE,
                    reason = "repeated_page_header (appears on ${headerPages.size} pages)",
                    confidence = 0.95f
                )
                continue
            }

            // Check repeated footer across multiple pages
            val footerPages = footerPageFrequency[normalized]
            if (totalPages > 1 && footerPages != null && footerPages.size >= 2 && block.normalizedBottom >= 0.86f) {
                decisions[block.blockId] = BlockNoiseDecision(
                    blockId = block.blockId,
                    action = CleanupAction.REMOVE_CANDIDATE,
                    reason = "repeated_page_footer (appears on ${footerPages.size} pages)",
                    confidence = 0.95f
                )
                continue
            }

            // Default: Keep content
            decisions[block.blockId] = BlockNoiseDecision(
                blockId = block.blockId,
                action = CleanupAction.KEEP,
                reason = "content_body",
                confidence = 0.90f
            )
        }

        return decisions
    }

    /**
     * Determines whether text represents protected, meaningful content that must NEVER be discarded.
     */
    fun isProtectedContent(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // 1. Numbered lists & sub-clauses: 1. / 1) / 1.1 / 2.1 / (1) / (a) / a)
        if (trimmed.matches(Regex("""^(\d+(\.\d+)*[\.\):]?|\([0-9a-zA-Z]+\)|[a-zA-Z]\))\s+.*"""))) {
            return true
        }

        // 2. Step instructions: Step 1 / Step 2
        if (trimmed.matches(Regex("""^Step\s+\d+.*""", RegexOption.IGNORE_CASE))) {
            return true
        }

        // 3. Questions / Problems: Question 1 / Q1. / Problem 2
        if (trimmed.matches(Regex("""^(Question|Q|Problem)\s*\d+[\.:\)].*""", RegexOption.IGNORE_CASE))) {
            return true
        }

        // 4. Legal clauses / sections: Section 4.1 / Article II / Clause (b)
        if (trimmed.matches(Regex("""^(Section|Article|Clause|Rule|Act|Part|Paragraph)\s+([0-9IVXLCDM\.]+|[a-zA-Z]+).*""", RegexOption.IGNORE_CASE))) {
            return true
        }

        // 5. Mathematical equations: Contains '=', '±', '≠', '≤', '≥', '∫', '∑', '√'
        if (trimmed.contains(Regex("""[=±≠≤≥∫∑√^]""")) && trimmed.length < 120) {
            return true
        }

        // 6. Citation / reference: [1] / (Smith, 2021)
        if (trimmed.matches(Regex("""^\[\d+\].*"""))) {
            return true
        }

        // 7. Significant date: e.g. 1947, 2024
        if (trimmed.matches(Regex("""^(19\d{2}|20\d{2})$"""))) {
            return true
        }

        return false
    }

    /**
     * Identifies page numbers, e.g. "1", "12", "Page 5", "Page 5 of 20", "- 12 -".
     */
    fun isStandalonePageNumber(text: String, top: Float, bottom: Float): Boolean {
        val trimmed = text.trim()
        val atMargin = top <= 0.12f || bottom >= 0.88f

        // Exact digits: "1", "42" at page top/bottom
        if (atMargin && trimmed.matches(Regex("""^\d{1,4}$"""))) {
            return true
        }

        // Formats: "Page 12", "Page 12 of 150", "- 12 -", "12 / 50"
        if (trimmed.matches(Regex("""^(Page\s+\d+(\s+of\s+\d+)?|-\s*\d+\s*-|\d+\s*\/\s*\d+)$""", RegexOption.IGNORE_CASE))) {
            return true
        }

        return false
    }

    private fun normalizeForComparison(input: String): String {
        return input.lowercase().replace(Regex("""\s+"""), " ").trim()
    }
}
