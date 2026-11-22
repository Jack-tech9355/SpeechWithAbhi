package com.speechwithabhi.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speechwithabhi.app.ai.synthesis.GeminiSpeechSynthesisProvider
import com.speechwithabhi.app.core.datastore.UserPreferencesRepository
import com.speechwithabhi.app.core.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VoiceStudioUiState(
    val voices: List<VoiceProfile> = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES,
    val selectedVoiceId: String = "Kore",
    val previewingVoiceId: String? = null,
    val favoriteVoiceIds: Set<String> = setOf("Kore", "Puck", "Charon")
)

class VoiceStudioViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceStudioUiState())
    val uiState: StateFlow<VoiceStudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    selectedVoiceId = prefs.defaultVoiceId
                )
            }
        }
    }

    fun selectVoice(voiceId: String) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultVoice(voiceId)
            _uiState.value = _uiState.value.copy(selectedVoiceId = voiceId)
        }
    }

    fun toggleFavorite(voiceId: String) {
        val currentFavs = _uiState.value.favoriteVoiceIds.toMutableSet()
        if (currentFavs.contains(voiceId)) {
            currentFavs.remove(voiceId)
        } else {
            currentFavs.add(voiceId)
        }
        _uiState.value = _uiState.value.copy(favoriteVoiceIds = currentFavs)
    }

    fun previewVoice(voiceId: String) {
        // Play short neutral sample preview
        _uiState.value = _uiState.value.copy(previewingVoiceId = voiceId)
    }

    fun stopPreview() {
        _uiState.value = _uiState.value.copy(previewingVoiceId = null)
    }
}
