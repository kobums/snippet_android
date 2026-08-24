package com.gowoobro.snippet.core.data

import com.gowoobro.snippet.core.datastore.SettingsStore
import com.gowoobro.snippet.core.datastore.TokenStore
import com.gowoobro.snippet.core.model.AuthResponse
import com.gowoobro.snippet.core.model.EmailCodeRequest
import com.gowoobro.snippet.core.model.LoginRequest
import com.gowoobro.snippet.core.model.RegisterRequest
import com.gowoobro.snippet.core.model.UserProfile
import com.gowoobro.snippet.core.network.AppError
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.api.AuthApi
import com.gowoobro.snippet.core.network.onSuccess
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 로그인 상태 */
sealed interface AuthState {
    /** 자동로그인 체크 전 (스플래시) */
    data object Unknown : AuthState

    data object LoggedOut : AuthState

    data class LoggedIn(val user: UserProfile) : AuthState
}

/**
 * 세션 관리 (02-data-api.md §5).
 *
 * - [authState]: 로그인 상태 StateFlow — 루트 내비게이션이 구독해 로그인/메인 분기
 * - [checkAuth]: 자동로그인 — 서버 호출 없이 로컬(토큰 + 프로필)만 확인
 * - refresh 실패 시 [sessionExpiredEvents] 수신 → 강제 로그아웃 (LoggedOut으로 리셋)
 */
class AuthManager(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    externalScope: CoroutineScope,
    sessionExpiredEvents: SharedFlow<Unit>,
    /** 로그아웃/탈퇴 시 인증 외 사용자 데이터 정리 훅 (독서 세션 스냅샷·알림·위젯 등) */
    private val onClearUserData: suspend () -> Unit = {},
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        externalScope.launch {
            sessionExpiredEvents.collect { forceLogout() }
        }
    }

    /**
     * 자동 로그인 체크 (§5.2) — 서버 호출 없이 로컬만 확인.
     * 토큰 만료는 이후 첫 API의 401 → Authenticator refresh로 처리된다.
     */
    suspend fun checkAuth() {
        val token = tokenStore.accessToken()
        val user = settingsStore.currentUser()
        _authState.value = if (!token.isNullOrBlank() && user != null) {
            AuthState.LoggedIn(user)
        } else {
            AuthState.LoggedOut
        }
    }

    /** 가입 전 이메일 인증코드 발송 */
    suspend fun sendEmailCode(email: String): AppResult<Unit> =
        safeApiCall { authApi.sendEmailCode(EmailCodeRequest(email)) }

    suspend fun login(email: String, password: String): AppResult<UserProfile> =
        authenticate { authApi.login(LoginRequest(email = email, password = password)) }

    suspend fun register(
        email: String,
        password: String,
        name: String,
        code: String,
    ): AppResult<UserProfile> =
        authenticate {
            authApi.register(RegisterRequest(email = email, password = password, name = name, code = code))
        }

    /** 로그아웃 (§5.3) — 서버 호출 없음, 로컬 데이터 삭제만 */
    suspend fun logout() {
        clearLocalAuthData()
        _authState.value = AuthState.LoggedOut
    }

    /** 회원탈퇴 — 성공 시 로컬 인증 데이터 전체 삭제 */
    suspend fun deleteAccount(): AppResult<Unit> =
        safeApiCall { authApi.deleteAccount() }.onSuccess {
            logoutLocally()
        }

    // ----- 내부 -----

    private suspend fun authenticate(call: suspend () -> AuthResponse): AppResult<UserProfile> {
        return when (val result = safeApiCall(call)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val response = result.data
                val token = response.token
                if (token.isNullOrBlank()) {
                    AppResult.Failure(AppError.AuthError(AppError.MSG_AUTH, "응답에 토큰이 없습니다"))
                } else {
                    // 새 세션에 refresh 토큰이 없으면 이전 계정 것을 반드시 지운다.
                    // 남겨두면 401 → refresh가 이전 계정으로 되살아나 화면은 새 계정,
                    // 쓰기는 이전 계정으로 나가는 교차 오염이 생긴다.
                    if (response.refreshToken.isNullOrBlank()) {
                        tokenStore.clearRefreshToken()
                    }
                    tokenStore.saveTokens(token, response.refreshToken)
                    val profile = UserProfile(id = response.id, email = response.email, name = response.name)
                    settingsStore.saveUserProfile(profile)
                    _authState.value = AuthState.LoggedIn(profile)
                    AppResult.Success(profile)
                }
            }
        }
    }

    private suspend fun forceLogout() {
        // 토큰은 TokenAuthenticator가 이미 삭제했지만 멱등하게 한 번 더 정리
        logoutLocally()
    }

    private suspend fun logoutLocally() {
        clearLocalAuthData()
        _authState.value = AuthState.LoggedOut
    }

    private suspend fun clearLocalAuthData() {
        tokenStore.clearTokens()
        settingsStore.clearUserProfile()
        // 진행 중이던 독서 세션·홈 위젯 스니펫 등도 함께 정리해
        // 다음 계정에 이전 사용자의 데이터가 남지 않게 한다.
        onClearUserData()
    }
}
