package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** GET/PUT /readinggoals 응답 — 실패 시 empty 폴백 */
@Serializable
data class ReadingGoalDto(
    val year: Int,
    val targetBooks: Int = 0,
    val completedBooks: Int = 0,
) {
    /** 0.0 ~ 1.0 */
    val progress: Float
        get() = if (targetBooks <= 0) 0f else (completedBooks.toFloat() / targetBooks).coerceIn(0f, 1f)

    companion object {
        fun empty(year: Int) = ReadingGoalDto(year = year, targetBooks = 0, completedBooks = 0)
    }
}

/** PUT /readinggoals Request (생략 시 서버 기본: 올해, 12권) */
@Serializable
data class ReadingGoalUpdateRequest(
    val year: Int? = null,
    val targetBooks: Int? = null,
)
