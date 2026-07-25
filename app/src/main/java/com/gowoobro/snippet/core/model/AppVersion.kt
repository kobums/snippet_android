package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/**
 * GET /appversion 응답 — 앱 버전 정책.
 *
 * 버전 비교는 전부 서버가 수행하므로 클라이언트는 [updateRequired] / [updateAvailable]
 * 두 값만 보고 동작한다.
 */
@Serializable
data class AppVersionDto(
    val platform: String = "unknown",
    val currentVersion: String? = null,
    val minVersion: String? = null,
    val latestVersion: String? = null,
    val updateRequired: Boolean = false,
    val updateAvailable: Boolean = false,
    val storeUrl: String? = null,
    val message: String? = null,
) {
    /** 스토어 URL — 서버 미설정 시 Play 스토어 주소로 폴백 */
    val resolvedStoreUrl: String
        get() = storeUrl?.takeIf { it.isNotBlank() } ?: PLAY_STORE_URL

    companion object {
        const val PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.gowoobro.snippet"

        /** 조회 실패 시 폴백 — 절대 차단하지 않는다 */
        val notRequired = AppVersionDto()
    }
}
