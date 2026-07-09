package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.ArchiveAddRequest
import com.gowoobro.snippet.core.model.SnippetArchive
import com.gowoobro.snippet.core.model.SnippetCardsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** /snippets — cards는 비로그인 허용 (02-data-api.md §3.2) */
interface SnippetApi {

    /** 스와이프 카드 조회 (count 기본 10) */
    @GET("snippets/cards")
    suspend fun getCards(
        @Query("count") count: Int = 10,
        @Query("excludeIds") excludeIds: List<Long>? = null,
    ): SnippetCardsResponse

    /** 아카이브 목록 (인증 필요) */
    @GET("snippets/archive")
    suspend fun getArchive(): List<SnippetArchive>

    /** 아카이브 추가 (스와이프 오른쪽) — ⚠️ 응답은 bare Long (archive id) */
    @POST("snippets/archive")
    suspend fun addArchive(@Body body: ArchiveAddRequest): Long

    /** 아카이브 제거 */
    @DELETE("snippets/archive/{snippetId}")
    suspend fun removeArchive(@Path("snippetId") snippetId: Long)

    /** 스킵 (스와이프 왼쪽) — 비로그인 시 서버가 no-op */
    @POST("snippets/{id}/skip")
    suspend fun skip(@Path("id") id: Long)
}
