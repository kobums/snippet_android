package com.gowoobro.snippet.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.data.AuthManager
import com.gowoobro.snippet.core.data.AuthState
import com.gowoobro.snippet.core.datastore.SettingsStore
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.AppThemeMode
import com.gowoobro.snippet.core.model.OcrEnginePreference
import com.gowoobro.snippet.core.model.SuggestionAddRequest
import com.gowoobro.snippet.core.model.SuggestionCategory
import com.gowoobro.snippet.core.model.UserProfile
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.api.SuggestionApi
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuggestionUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
)

data class ProfileUiState(
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val errorMessage: String? = null,
)

class ProfileViewModel(
    private val authManager: AuthManager,
    private val settingsStore: SettingsStore,
    private val suggestionApi: SuggestionApi,
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = authManager.authState
        .map { state -> (state as? AuthState.LoggedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<AppThemeMode> = settingsStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.SYSTEM)

    val ocrEngine: StateFlow<OcrEnginePreference> = settingsStore.ocrEngineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OcrEnginePreference.ON_DEVICE)

    private val _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState.asStateFlow()

    private val _suggestionUiState = MutableStateFlow(SuggestionUiState())
    val suggestionUiState: StateFlow<SuggestionUiState> = _suggestionUiState.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
        }
    }

    fun setOcrEngine(pref: OcrEnginePreference) {
        viewModelScope.launch {
            settingsStore.setOcrEngine(pref)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _profileUiState.update { it.copy(isLoggingOut = true) }
            authManager.logout()
            _profileUiState.update { it.copy(isLoggingOut = false) }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _profileUiState.update { it.copy(isDeletingAccount = true, errorMessage = null) }
            when (val result = authManager.deleteAccount()) {
                is AppResult.Success -> {
                    _profileUiState.update { it.copy(isDeletingAccount = false) }
                }
                is AppResult.Failure -> {
                    _profileUiState.update {
                        it.copy(isDeletingAccount = false, errorMessage = result.error.message)
                    }
                }
            }
        }
    }

    fun submitSuggestion(category: SuggestionCategory, title: String, content: String) {
        viewModelScope.launch {
            _suggestionUiState.update { it.copy(isLoading = true, errorMessage = null, isSuccess = false) }
            val request = SuggestionAddRequest(
                category = category,
                title = title.trim().ifBlank { null },
                content = content.trim(),
            )
            when (val result = safeApiCall { suggestionApi.add(request) }) {
                is AppResult.Success -> {
                    _suggestionUiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is AppResult.Failure -> {
                    _suggestionUiState.update {
                        it.copy(isLoading = false, errorMessage = result.error.message)
                    }
                }
            }
        }
    }

    fun consumeProfileError() {
        _profileUiState.update { it.copy(errorMessage = null) }
    }

    fun consumeSuggestionSuccess() {
        _suggestionUiState.update { it.copy(isSuccess = false) }
    }

    fun consumeSuggestionError() {
        _suggestionUiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProfileViewModel(
                        authManager = container.authManager,
                        settingsStore = container.settingsStore,
                        suggestionApi = container.suggestionApi,
                    ) as T
            }
    }
}
