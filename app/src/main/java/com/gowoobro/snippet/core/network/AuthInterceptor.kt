package com.gowoobro.snippet.core.network

import com.gowoobro.snippet.core.datastore.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 모든 요청에 `Authorization: Bearer {accessToken}` 자동 주입 (02-data-api.md §2.1).
 * 토큰이 없으면 헤더 없이 전송한다 (스니펫 카드는 비로그인도 허용).
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessTokenBlocking()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
