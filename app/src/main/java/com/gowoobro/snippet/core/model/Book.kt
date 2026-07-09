package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** GET /books/search 응답 항목 (알라딘 검색, 백엔드 프록시) */
@Serializable
data class BookSearchDto(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val pubDate: String = "",
    val isbn: String = "",
    val coverUrl: String = "",
    val totalPage: Int? = null,
)

/** GET /books/popular 응답 항목 (국립중앙도서관, 전 필드 null-safe 폴백) */
@Serializable
data class PopularBookDto(
    val rank: Int = 0,
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn13: String = "",
    val kdc: String = "",
    val kdcName: String = "",
    val loanCount: Int = 0,
    val coverUrl: String = "",
)

/** GET /books/recommend 응답 항목 (실패 시 빈 배열 흡수) */
@Serializable
data class BookRecommendDto(
    val id: Long,
    val title: String = "",
    val author: String = "",
    val coverUrl: String = "",
    val category: String? = null,
    val reason: String = "",
)
