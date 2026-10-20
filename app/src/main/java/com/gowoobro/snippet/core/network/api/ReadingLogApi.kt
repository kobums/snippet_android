package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.DailyPagesDto
import retrofit2.http.GET
import retrofit2.http.Query

/** /readinglogs — 인증 필요 */
interface ReadingLogApi {

    /** 해당 월의 일별 읽은 페이지 (pages > 0인 날만) */
    @GET("readinglogs/daily")
    suspend fun getDaily(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): List<DailyPagesDto>
}
