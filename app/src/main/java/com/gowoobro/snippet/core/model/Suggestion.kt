package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** 건의 카테고리 — wire 값은 **대문자** (enum name 그대로 직렬화) */
@Serializable
enum class SuggestionCategory(val wire: String, val label: String) {
    FEATURE("FEATURE", "기능 추가"),
    BUG("BUG", "버그 신고"),
    IMPROVEMENT("IMPROVEMENT", "개선 제안"),
    OTHER("OTHER", "기타"),
}

/**
 * POST /suggestions Request.
 * title이 빈 문자열이면 null로 보내 키 자체를 생략한다 (Json explicitNulls=false).
 */
@Serializable
data class SuggestionAddRequest(
    val category: SuggestionCategory,
    val title: String? = null,
    val content: String,
)

/** POST /suggestions, GET /suggestions/mine 응답 */
@Serializable
data class SuggestionDto(
    val id: Long,
    val category: String = "",
    val title: String? = null,
    val content: String = "",
    val status: String = "",
    /** ISO LocalDateTime 문자열 */
    val createDate: String = "",
)
