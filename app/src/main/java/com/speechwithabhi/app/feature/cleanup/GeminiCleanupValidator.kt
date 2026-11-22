package com.speechwithabhi.app.feature.cleanup

import com.speechwithabhi.app.core.model.CleanupAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class GeminiBlockDecision(
    val block_id: String,
    val action: String,
    val reason: String = "",
    val cleaned_text: String = "",
    val confidence: Float = 1.0f
)

@Serializable
data class GeminiCleanupResponse(
    val document_language: String = "en",
    val blocks: List<GeminiBlockDecision> = emptyList(),
    val uncertain_block_ids: List<String> = emptyList()
)

/**
 * Validates Gemini structured output against schema and input block IDs.
 * Treats AI responses as untrusted input: if parsing fails or IDs mismatch,
 * falls back to deterministic cleanup without crashing or losing user text.
 */
class GeminiCleanupValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Validates raw JSON string from Gemini API against expected input block IDs.
     * Returns mapped decisions if valid, or null to trigger deterministic fallback.
     */
    fun validateAndMap(rawJson: String, expectedBlockIds: Set<String>): Map<String, GeminiBlockDecision>? {
        if (rawJson.isBlank()) return null

        try {
            // Clean potential markdown fences (```json ... ```)
            val cleanedJson = rawJson
                .replace(Regex("""^```(json)?\s*"""), "")
                .replace(Regex("""\s*```$"""), "")
                .trim()

            val response = json.decodeFromString<GeminiCleanupResponse>(cleanedJson)
            if (response.blocks.isEmpty()) return null

            val mapped = mutableMapOf<String, GeminiBlockDecision>()
            for (block in response.blocks) {
                // Must be an existing block ID
                if (block.block_id in expectedBlockIds) {
                    val normalizedAction = when (block.action.lowercase()) {
                        "keep" -> "keep"
                        "remove_candidate" -> "remove_candidate"
                        "merge_duplicate_candidate", "merge_duplicate" -> "merge_duplicate_candidate"
                        "needs_review" -> "needs_review"
                        else -> "keep"
                    }
                    mapped[block.block_id] = block.copy(action = normalizedAction)
                }
            }

            // Must match at least 70% of requested blocks to be deemed trustworthy
            if (mapped.size < (expectedBlockIds.size * 0.70)) {
                return null
            }

            return mapped
        } catch (_: Exception) {
            return null // Transparent fallback on any schema anomaly
        }
    }
}
