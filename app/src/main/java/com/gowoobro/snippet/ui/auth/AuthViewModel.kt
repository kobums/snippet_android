package com.gowoobro.snippet.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.data.AuthManager
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.network.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 로그인/회원가입 폼의 비동기 상태 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** 회원가입: 인증코드 발송 완료 여부 */
    val codeSent: Boolean = false,
)

/**
 * 인증 화면(로그인/회원가입) 공용 ViewModel.
 * 성공 시 [AuthManager]가 authState를 LoggedIn으로 바꾸므로, 화면 전환은 루트가 담당한다.
 */
class AuthViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        runAuth { authManager.login(email.trim(), password) }
    }

    fun register(email: String, password: String, name: String, code: String) {
        runAuth { authManager.register(email.trim(), password, name.trim(), code.trim()) }
    }

    fun sendEmailCode(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authManager.sendEmailCode(email.trim())) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isLoading = false, codeSent = true) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runAuth(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = block()) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isLoading = false) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthViewModel(container.authManager) as T
            }
    }
}
