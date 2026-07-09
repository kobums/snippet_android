package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.RecordAddRequest
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** /records — 인증 필요 (02-data-api.md §3.5) */
interface RecordApi {

    /** 내 기록 전체 (앱 미사용) */
    @GET("records")
    suspend fun getAll(): List<RecordDto>

    /** 책별 기록 — type은 RecordType.wire 값 ("snippet"/"diary"/"review") */
    @GET("records/bybook")
    suspend fun getByBook(
        @Query("bookId") bookId: Long,
        @Query("type") type: String? = null,
    ): List<RecordDto>

    /** 월별 기록 */
    @GET("records/monthly")
    suspend fun getMonthly(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("type") type: String? = null,
    ): List<RecordDto>

    /** 생성 — ⚠️ 응답은 bare Long (recordId). 객체 파싱 금지. */
    @POST("records")
    suspend fun add(@Body body: RecordAddRequest): Long

    @PATCH("records/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: RecordUpdateRequest,
    ): RecordDto

    @DELETE("records/{id}")
    suspend fun delete(@Path("id") id: Long)
}
