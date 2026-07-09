package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** GET /userbooks/stats/monthly 응답 항목 */
@Serializable
data class MonthlyStatsDto(
    val month: Int,
    val completedCount: Int = 0,
    val totalPages: Int = 0,
    /** 카테고리명 → 권수 */
    val categoryCount: Map<String, Int> = emptyMap(),
)

/** GET /userbooks/stats/yearly 응답 항목 */
@Serializable
data class YearlyStatsDto(
    val year: Int,
    val completedCount: Int = 0,
    val totalPages: Int = 0,
)

/** GET /userbooks/stats/category 응답 항목 */
@Serializable
data class CategoryStatsDto(
    val category: String = "",
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val completionRate: Double = 0.0,
)

/** GET /userbooks/stats/insights 응답 */
@Serializable
data class ReadingInsightsDto(
    val averageReadingDays: Double = 0.0,
    val topCategory: String = "",
    val longestReadingDays: Int = 0,
    val longestBook: String = "",
)
