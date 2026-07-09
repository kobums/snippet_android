package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** 스와이프 카드 (GET /snippets/cards 의 cards 항목) */
@Serializable
data class Snippet(
    val id: Long,
    val text: String,
    val tag: String? = null,
    val bookTitle: String? = null,
)

/** GET /snippets/cards 응답 */
@Serializable
data class SnippetCardsResponse(
    val cards: List<Snippet> = emptyList(),
    /** -1 = 비로그인(무제한), 0 = 일일 제한 도달 */
    val remainingToday: Int = -1,
)

/** GET /snippets/archive 응답 항목 — 책 메타는 null 시 "" 폴백 */
@Serializable
data class SnippetArchive(
    val id: Long,
    val text: String,
    val tag: String? = null,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val coverUrl: String = "",
    val affiliateUrl: String = "",
)

/** POST /snippets/archive */
@Serializable
data class ArchiveAddRequest(
    val snippetId: Long,
)
