package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.LibraryAddRequest
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.core.model.UserBookUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** /userbooks — 전부 인증 필요 (02-data-api.md §3.3) */
interface UserBookApi {

    /** 전체 목록 (앱 미사용, 백엔드 존재) */
    @GET("userbooks")
    suspend fun getAll(): List<UserBookDto>

    /** 단건 (앱 미사용) */
    @GET("userbooks/{id}")
    suspend fun getById(@Path("id") id: Long): UserBookDto

    /** 해당 월 활동 도서 (year/month 둘 다 줘야 적용, 생략 시 현재 월) */
    @GET("userbooks/monthly")
    suspend fun getMonthly(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
    ): List<UserBookDto>

    /** 대시보드 진행 탭: waiting/reading 전체 + 해당 월 completed */
    @GET("userbooks/progress")
    suspend fun getProgress(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
    ): List<UserBookDto>

    /** 서재용 페이지네이션 (0-base, 최신순) */
    @GET("userbooks/all")
    suspend fun getPaged(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): List<UserBookDto>

    /** 서재 추가 — ⚠️ 응답은 bare Long (userBookId). 수신 후 목록 재조회로 동기화 권장. */
    @POST("userbooks")
    suspend fun add(@Body body: LibraryAddRequest): Long

    /** 부분 업데이트 */
    @PATCH("userbooks/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UserBookUpdateRequest,
    ): UserBookDto

    /** PATCH와 동일 동작 (앱 미사용) */
    @PUT("userbooks/{id}")
    suspend fun replace(
        @Path("id") id: Long,
        @Body body: UserBookUpdateRequest,
    ): UserBookDto

    @DELETE("userbooks/{id}")
    suspend fun delete(@Path("id") id: Long)
}
