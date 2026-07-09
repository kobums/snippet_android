package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.FcmTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

/** /users (02-data-api.md §3.11) */
interface UserApi {

    /**
     * FCM 토큰 등록 (인증 필요).
     * 앱 시작 시 + onTokenRefresh 마다 호출. **실패는 무시** (로그인 전일 수 있음),
     * 로그인 성공 직후 재등록 시도.
     */
    @POST("users/fcmtoken")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest)
}
