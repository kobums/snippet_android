package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.CategoryStatsDto
import com.gowoobro.snippet.core.model.MonthlyStatsDto
import com.gowoobro.snippet.core.model.ReadingInsightsDto
import com.gowoobro.snippet.core.model.YearlyStatsDto
import retrofit2.http.GET
import retrofit2.http.Query

/** `/userbooks/stats/...` — 인증 필요 (02-data-api.md §3.4) */
interface UserBookStatsApi {

    /** 월별 통계 (year 기본 올해) */
    @GET("userbooks/stats/monthly")
    suspend fun getMonthly(@Query("year") year: Int? = null): List<MonthlyStatsDto>

    @GET("userbooks/stats/yearly")
    suspend fun getYearly(): List<YearlyStatsDto>

    @GET("userbooks/stats/category")
    suspend fun getCategory(@Query("year") year: Int? = null): List<CategoryStatsDto>

    @GET("userbooks/stats/insights")
    suspend fun getInsights(@Query("year") year: Int? = null): ReadingInsightsDto
}
