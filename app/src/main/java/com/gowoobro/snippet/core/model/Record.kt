package com.gowoobro.snippet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 기록 타입. 알 수 없는 값은 SNIPPET 폴백 (Json coerceInputValues + 기본값). */
@Serializable
enum class RecordType(val wire: String) {
    @SerialName("snippet") SNIPPET("snippet"),
    @SerialName("diary") DIARY("diary"),
    @SerialName("review") REVIEW("review"),
}

/** GET/PATCH /records 응답 공통 DTO. bookAuthor/bookCoverUrl은 구 데이터 호환 "" 폴백. */
@Serializable
data class RecordDto(
    val id: Long,
    val bookId: Long = 0,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookCoverUrl: String = "",
    val type: RecordType = RecordType.SNIPPET,
    val text: String = "",
    val tag: String? = null,
    val relatedPage: Int? = null,
    /** ISO LocalDateTime 문자열 */
    val createDate: String = "",
)

/**
 * POST /records Request.
 * ⚠️ 응답은 bare Long (recordId) — 객체 파싱 금지.
 */
@Serializable
data class RecordAddRequest(
    val bookId: Long,
    val type: RecordType,
    val text: String,
    val tag: String? = null,
    val relatedPage: Int? = null,
)

/** PATCH /records/{id} — null 필드는 바디에서 생략 */
@Serializable
data class RecordUpdateRequest(
    val type: RecordType? = null,
    val text: String? = null,
    val tag: String? = null,
    val relatedPage: Int? = null,
)
