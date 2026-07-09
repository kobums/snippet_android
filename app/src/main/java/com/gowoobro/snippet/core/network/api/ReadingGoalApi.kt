package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.ReadingGoalDto
import com.gowoobro.snippet.core.model.ReadingGoalUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/** /readinggoals — 인증 필요 (02-data-api.md §3.8) */
interface ReadingGoalApi {

    /** year 기본 올해 — 실패 시 ReadingGoalDto.empty() 폴백 권장 */
    @GET("readinggoals")
    suspend fun get(@Query("year") year: Int? = null): ReadingGoalDto

    @PUT("readinggoals")
    suspend fun update(@Body body: ReadingGoalUpdateRequest): ReadingGoalDto
}
