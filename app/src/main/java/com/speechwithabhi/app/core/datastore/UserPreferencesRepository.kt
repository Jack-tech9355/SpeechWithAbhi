package com.speechwithabhi.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class AppUserPreferences(
    val defaultVoiceId: String = "Kore",
    val playbackSpeed: Float = 1.0f,
    val narrationMode: String = "FAITHFUL_NARRATOR",
    val cleanupSensitivity: String = "BALANCED",
    val appLanguage: String = "en",
    val isDarkMode: Boolean = true,
    val readPageLabels: Boolean = false
)

/**
 * DataStore preferences repository for SpeechWithAbhi user settings.
 */
class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_VOICE_ID = stringPreferencesKey("default_voice_id")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val NARRATION_MODE = stringPreferencesKey("narration_mode")
        val CLEANUP_SENSITIVITY = stringPreferencesKey("cleanup_sensitivity")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val READ_PAGE_LABELS = booleanPreferencesKey("read_page_labels")
    }

    val userPreferencesFlow: Flow<AppUserPreferences> = context.dataStore.data.map { prefs ->
        AppUserPreferences(
            defaultVoiceId = prefs[PreferencesKeys.DEFAULT_VOICE_ID] ?: "Kore",
            playbackSpeed = prefs[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f,
            narrationMode = prefs[PreferencesKeys.NARRATION_MODE] ?: "FAITHFUL_NARRATOR",
            cleanupSensitivity = prefs[PreferencesKeys.CLEANUP_SENSITIVITY] ?: "BALANCED",
            appLanguage = prefs[PreferencesKeys.APP_LANGUAGE] ?: "en",
            isDarkMode = prefs[PreferencesKeys.IS_DARK_MODE] ?: true,
            readPageLabels = prefs[PreferencesKeys.READ_PAGE_LABELS] ?: false
        )
    }

    suspend fun updateDefaultVoice(voiceId: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEFAULT_VOICE_ID] = voiceId
        }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 3.0f)
        }
    }

    suspend fun updateNarrationMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NARRATION_MODE] = mode
        }
    }

    suspend fun updateCleanupSensitivity(sensitivity: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CLEANUP_SENSITIVITY] = sensitivity
        }
    }

    suspend fun updateAppLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.APP_LANGUAGE] = lang
        }
    }

    suspend fun updateDarkMode(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_DARK_MODE] = isDark
        }
    }

    suspend fun updateReadPageLabels(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.READ_PAGE_LABELS] = enabled
        }
    }
}
