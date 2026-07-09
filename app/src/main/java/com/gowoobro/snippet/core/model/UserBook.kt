package com.gowoobro.snippet.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 서재 구분. wire 값은 서버 전송 문자열 원문 (02-data-api.md §4.1).
 * `return`은 Kotlin 예약어가 아니지만 wire 일치를 위해 @SerialName으로 고정.
 */
@Serializable
enum class BookType(val wire: String) {
    @SerialName("wish") WISH("wish"),
    @SerialName("have") HAVE("have"),
    @SerialName("borrow") BORROW("borrow"),
    @SerialName("return") RETURN("return"),
}

/** 독서 상태. `none`은 wish 전용. */
@Serializable
enum class BookStatus(val wire: String) {
    @SerialName("none") NONE("none"),
    @SerialName("waiting") WAITING("waiting"),
    @SerialName("reading") READING("reading"),
    @SerialName("completed") COMPLETED("completed"),
    @SerialName("dropped") DROPPED("dropped"),
}

/** GET/PATCH /userbooks 응답 공통 DTO */
@Serializable
data class UserBookDto(
    val id: Long,
    val bookId: Long = 0,
    val title: String = "",
    val author: String = "",
    val coverUrl: String = "",
    val type: BookType = BookType.WISH,
    val status: BookStatus = BookStatus.NONE,
    val readPage: Int = 0,
    val totalPage: Int = 0,
    /** ISO LocalDateTime 문자열 */
    val createDate: String = "",
    val startDate: String = "",
    val endDate: String = "",
    /** 1~5 */
    val rating: Int? = null,
    /** 대출 반납 예정일 */
    val returnDate: String? = null,
) {
    /** 0.0 ~ 1.0, totalPage == 0이면 0 */
    val progress: Float
        get() = if (totalPage <= 0) 0f else (readPage.toFloat() / totalPage).coerceIn(0f, 1f)
}

/**
 * POST /userbooks Request (LibraryAddRequestDto).
 * ⚠️ 응답은 bare Long (userBookId) — 수신 후 목록 재조회로 동기화 권장.
 */
@Serializable
data class LibraryAddRequest(
    val title: String,
    val author: String,
    val publisher: String = "",
    val pubDate: String = "",
    val isbn: String = "",
    val coverUrl: String = "",
    val totalPage: Int = 0,
    val type: BookType,
    val status: BookStatus,
    val readPage: Int? = 0,
    /** ISO-8601, 미선택 시 now */
    val startDate: String,
    val endDate: String,
    val createDate: String,
)

/**
 * PATCH /userbooks/{id} 부분 업데이트.
 * Json(explicitNulls=false, encodeDefaults=false) 설정으로 null 필드는 바디에서 생략된다.
 */
@Serializable
data class UserBookUpdateRequest(
    val type: BookType? = null,
    val status: BookStatus? = null,
    val readPage: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val rating: Int? = null,
    val returnDate: String? = null,
)
