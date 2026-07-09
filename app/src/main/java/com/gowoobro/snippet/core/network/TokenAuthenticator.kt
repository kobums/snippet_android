package com.gowoobro.snippet.core.network

import com.gowoobro.snippet.core.datastore.TokenStore
import com.gowoobro.snippet.core.model.RefreshRequest
import com.gowoobro.snippet.core.network.api.AuthApi
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 토큰 갱신 (02-data-api.md §2.2).
 *
 * - 요청 경로에 `/auth/` 포함 → 갱신 시도 없이 에러 전파
 * - refreshToken 없음 → accessToken 삭제 후 에러 전파
 * - refreshToken 있음 → POST /auth/refresh 후 새 토큰으로 1회 재시도
 * - 갱신 실패 → 토큰 전체 삭제 + 강제 로그아웃 이벤트 발화
 *
 * 갱신 호출은 인증 인터셉터/Authenticator가 없는 별도 Retrofit([refreshApi])으로 보낸다 (무한루프 방지).
 * 동시 401에 대한 갱신은 synchronized 블록으로 직렬화한다 (문서 권고).
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: () -> AuthApi,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // /auth/ 경로는 갱신 시도 안 함 (refresh 자신 포함)
        if (response.request.url.encodedPath.contains("/auth/")) return null

        // 이미 한 번 재시도한 요청은 포기
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenStore.refreshTokenBlocking()
        if (refreshToken.isNullOrBlank()) {
            runBlocking { tokenStore.clearAccessToken() }
            return null
        }

        synchronized(lock) {
            // 다른 스레드가 먼저 갱신을 끝냈다면 새 토큰으로 즉시 재시도
            val failedToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()
            val currentToken = tokenStore.accessTokenBlocking()
            if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            return runBlocking {
                try {
                    val refreshed = refreshApi().refresh(RefreshRequest(refreshToken))
                    tokenStore.saveTokens(refreshed.token, refreshed.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.token}")
                        .build()
                } catch (e: Exception) {
                    tokenStore.clearTokens()
                    onSessionExpired()
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
