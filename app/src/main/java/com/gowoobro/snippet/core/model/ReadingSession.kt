package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** GET /readingsessions 응답 공통 DTO */
@Serializable
data class ReadingSessionDto(
    val id: Long,
    val userBookId: Long = 0,
    val bookId: Long = 0,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookCoverUrl: String = "",
    val durationSeconds: Int = 0,
    val startPage: Int = 0,
    val endPage: Int = 0,
    val pagesRead: Int = 0,
    val secondsPerPage: Double = 0.0,
    /** yyyy-MM-dd */
    val sessionDate: String = "",
    /** LocalDateTime.toString() */
    val createDate: String = "",
)

/**
 * POST /readingsessions Request.
 * ⚠️ 응답은 bare Long (id).
 */
@Serializable
data class ReadingSessionAddRequest(
    val userBookId: Long,
    val durationSeconds: Int,
    val startPage: Int,
    val endPage: Int,
    /** yyyy-MM-dd */
    val sessionDate: String,
)

/** GET /readingsessions/stats 응답 — 서버는 Long으로 직렬화 */
@Serializable
data class ReadingSessionStatsDto(
    val totalSessions: Long = 0,
    val totalSeconds: Long = 0,
    val totalPagesRead: Long = 0,
    val avgSecondsPerPage: Double = 0.0,
) {
    companion object {
        fun empty() = ReadingSessionStatsDto()
    }
}

/** GET /readingsessions/streak 응답 */
@Serializable
data class StreakDto(
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    /** yyyy-MM-dd */
    val lastReadDate: String? = null,
)
