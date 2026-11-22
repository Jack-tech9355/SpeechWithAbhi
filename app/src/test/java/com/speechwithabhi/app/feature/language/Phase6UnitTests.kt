package com.speechwithabhi.app.feature.language

import com.speechwithabhi.app.feature.player.SpeedController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6UnitTests {

    @Test
    fun languageManager_detectsHindiScriptAccurately() {
        val hindiText = "नमस्ते, यह स्पीच विद अभि ऐप है।"
        val result = LanguageManager.detectLanguage(hindiText)

        assertEquals("hi", result.languageCode)
        assertTrue(result.confidence > 0.8f)
        assertFalse(result.isMixedLanguage)
    }

    @Test
    fun languageManager_detectsEnglishScriptAccurately() {
        val englishText = "Welcome to SpeechWithAbhi document narrator."
        val result = LanguageManager.detectLanguage(englishText)

        assertEquals("en", result.languageCode)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun speedController_flagsSpeedsAboveTwoXForQualityAdvisory() {
        assertFalse(SpeedController.requiresQualityWarning(1.0f))
        assertFalse(SpeedController.requiresQualityWarning(1.5f))
        assertFalse(SpeedController.requiresQualityWarning(2.0f))
        assertTrue(SpeedController.requiresQualityWarning(2.25f))
        assertTrue(SpeedController.requiresQualityWarning(2.5f))
        assertTrue(SpeedController.requiresQualityWarning(3.0f))
    }
}
