package com.speechwithabhi.app.feature.language

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isTtsSupported: Boolean = true
)

data class LanguageDetectionResult(
    val languageCode: String,
    val confidence: Float,
    val isMixedLanguage: Boolean = false
)

/**
 * Multilingual language detection, script analysis, and glossary management.
 */
object LanguageManager {

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("hi", "Hindi", "हिन्दी"),
        SupportedLanguage("es", "Spanish", "Español"),
        SupportedLanguage("fr", "French", "Français"),
        SupportedLanguage("de", "German", "Deutsch"),
        SupportedLanguage("ja", "Japanese", "日本語")
    )

    /**
     * Inspects Unicode character blocks to detect language and script.
     */
    fun detectLanguage(sampleText: String): LanguageDetectionResult {
        if (sampleText.isBlank()) {
            return LanguageDetectionResult("en", 0.5f)
        }

        var devanagariCount = 0
        var latinCount = 0
        var totalLetters = 0

        for (char in sampleText) {
            val block = Character.UnicodeBlock.of(char)
            if (block == Character.UnicodeBlock.DEVANAGARI) {
                devanagariCount++
                totalLetters++
            } else if (Character.isLetter(char)) {
                latinCount++
                totalLetters++
            }
        }

        if (totalLetters == 0) return LanguageDetectionResult("en", 0.5f)

        val devanagariRatio = devanagariCount.toFloat() / totalLetters
        val latinRatio = latinCount.toFloat() / totalLetters

        return when {
            devanagariRatio > 0.6f -> LanguageDetectionResult("hi", devanagariRatio, latinRatio > 0.15f)
            latinRatio > 0.6f -> LanguageDetectionResult("en", latinRatio, devanagariRatio > 0.15f)
            else -> LanguageDetectionResult("en", 0.6f, isMixedLanguage = true)
        }
    }
}
