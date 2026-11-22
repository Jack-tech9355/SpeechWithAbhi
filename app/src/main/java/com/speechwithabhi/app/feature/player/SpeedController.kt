package com.speechwithabhi.app.feature.player

/**
 * Controller and validator for pitch-preserving audio speed stretching.
 */
object SpeedController {

    val STANDARD_SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val EXTENDED_SPEEDS = listOf(2.25f, 2.5f, 3.0f)

    /**
     * Checks if a speed setting requires high-speed quality advisory.
     */
    fun requiresQualityWarning(speed: Float): Boolean {
        return speed > 2.0f
    }

    /**
     * Informative disclaimer for speeds exceeding 2.0x.
     */
    const val HIGH_SPEED_ADVISORY =
        "Speeds above 2.0x preserve voice pitch via time-stretching, but natural vocal cadence and syllable acoustics may exhibit slight compression artifacts."
}
