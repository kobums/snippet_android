package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/**
 * GET /readinglogs/daily 응답 항목.
 * 읽은 페이지가 0보다 큰 날만 내려오므로, 빠진 날은 클라이언트에서 0으로 채운다.
 * (수동 진도 수정·타이머 세션·완독 처리 등 모든 readPage 변화가 집계됨)
 */
@Serializable
data class DailyPagesDto(
    /** yyyy-MM-dd */
    val date: String = "",
    val pages: Int = 0,
)
