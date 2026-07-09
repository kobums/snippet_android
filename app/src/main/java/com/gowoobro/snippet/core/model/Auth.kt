package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** POST /auth/emailcode */
@Serializable
data class EmailCodeRequest(
    val email: String,
)

/** POST /auth/register */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    /** 이메일로 받은 인증코드 */
    val code: String,
)

/** POST /auth/login */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/** POST /auth/refresh */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/** POST /auth/refresh 응답 — token/refreshToken 둘 다 새로 발급 */
@Serializable
data class RefreshResponse(
    val token: String,
    val refreshToken: String,
)

/** /auth/register, /auth/login, /auth/me 공통 응답 */
@Serializable
data class AuthResponse(
    val id: Long,
    val email: String,
    val name: String,
    val token: String? = null,
    val refreshToken: String? = null,
)

/**
 * 로컬 저장용 사용자 프로필.
 * ⚠️ 토큰은 포함하지 않는다 — 토큰은 TokenStore에만 저장 (02-data-api.md §5.4 개선 권장 반영).
 */
@Serializable
data class UserProfile(
    val id: Long,
    val email: String,
    val name: String,
)

/** POST /users/fcmtoken */
@Serializable
data class FcmTokenRequest(
    val fcmToken: String,
)
