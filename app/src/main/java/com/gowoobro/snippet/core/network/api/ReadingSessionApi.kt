package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.ReadingSessionAddRequest
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.core.model.ReadingSessionStatsDto
import com.gowoobro.snippet.core.model.StreakDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** /readingsessions — 인증 필요 (02-data-api.md §3.7) */
interface ReadingSessionApi {

    /** 전체, 최신순 */
    @GET("readingsessions")
    suspend fun getAll(): List<ReadingSessionDto>

    /** 생성 — ⚠️ 응답은 bare Long (id) */
    @POST("readingsessions")
    suspend fun add(@Body body: ReadingSessionAddRequest): Long

    @GET("readingsessions/bybook")
    suspend fun getByBook(@Query("userBookId") userBookId: Long): List<ReadingSessionDto>

    @GET("readingsessions/stats")
    suspend fun getStats(@Query("userBookId") userBookId: Long): ReadingSessionStatsDto

    @GET("readingsessions/streak")
    suspend fun getStreak(): StreakDto
}
