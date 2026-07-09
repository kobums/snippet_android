package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.AuthResponse
import com.gowoobro.snippet.core.model.EmailCodeRequest
import com.gowoobro.snippet.core.model.LoginRequest
import com.gowoobro.snippet.core.model.RefreshRequest
import com.gowoobro.snippet.core.model.RefreshResponse
import com.gowoobro.snippet.core.model.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/** /auth — 인증 불필요 (02-data-api.md §3.1) */
interface AuthApi {

    /** 가입 전 이메일 인증코드 발송 */
    @POST("auth/emailcode")
    suspend fun sendEmailCode(@Body body: EmailCodeRequest)

    /** 회원가입 (토큰 즉시 발급) */
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    /** ⚠️ 인증 인터셉터 없는 클라이언트로 호출해야 한다 (TokenAuthenticator 참조) */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    /** 회원탈퇴 — Bearer 토큰으로 본인 식별, 성공 후 로컬 인증 데이터 전체 삭제 */
    @DELETE("auth/account")
    suspend fun deleteAccount()

    /** 토큰 유효성 검증용 */
    @GET("auth/me")
    suspend fun me(): AuthResponse
}
