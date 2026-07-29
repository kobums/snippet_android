package com.gowoobro.snippet.core.network

import com.gowoobro.snippet.core.datastore.TokenStore
import com.gowoobro.snippet.core.model.RefreshRequest
import com.gowoobro.snippet.core.network.api.AuthApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

/**
 * 401 토큰 갱신 (02-data-api.md §2.2).
 *
 * - 요청 경로에 `/auth/` 포함 → 갱신 시도 없이 에러 전파
 * - refreshToken 없음 → accessToken 삭제 후 에러 전파
 * - refreshToken 있음 → POST /auth/refresh 후 새 토큰으로 1회 재시도
 * - 갱신 실패 처리: 서버가 리프레시 토큰을 **명시적으로 거부(400/401/403)** 한 경우에만
 *   토큰 전체 삭제 + 강제 로그아웃. 네트워크 오류·5xx(배포 중 재시작 등)·기타 일시적
 *   실패는 토큰을 유지한 채 이번 요청만 실패시킨다 (다음 401에서 자연 재시도).
 *   네트워크 오류는 1초 뒤 1회 자동 재시도한다 (백그라운드 복귀 직후 연결 끊김 대비).
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
                    val refreshed = refreshWithRetry(refreshToken)
                    tokenStore.saveTokens(refreshed.token, refreshed.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.token}")
                        .build()
                } catch (e: HttpException) {
                    // 서버가 리프레시 토큰 자체를 무효로 판정한 경우에만 세션 종료.
                    // 5xx 등 나머지 상태는 토큰을 유지하고 이번 요청만 실패시킨다.
                    if (e.code() in REJECTED_CODES) {
                        tokenStore.clearTokens()
                        onSessionExpired()
                    }
                    null
                } catch (e: Exception) {
                    // 네트워크 오류 등 일시적 실패 → 토큰 유지 (로그아웃하지 않음).
                    null
                }
            }
        }
    }

    /** 네트워크 오류(IOException)는 1초 뒤 한 번 더 시도. HttpException은 그대로 전파. */
    private suspend fun refreshWithRetry(refreshToken: String) = try {
        refreshApi().refresh(RefreshRequest(refreshToken))
    } catch (e: IOException) {
        delay(RETRY_DELAY_MS)
        refreshApi().refresh(RefreshRequest(refreshToken))
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

    companion object {
        /** 서버가 리프레시 토큰을 명시적으로 거부한 것으로 간주하는 상태 코드 — 유일한 로그아웃 사유. */
        private val REJECTED_CODES = setOf(400, 401, 403)
        private const val RETRY_DELAY_MS = 1_000L
    }
}
