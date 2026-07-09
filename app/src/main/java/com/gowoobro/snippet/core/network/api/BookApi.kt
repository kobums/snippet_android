package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.BookRecommendDto
import com.gowoobro.snippet.core.model.BookSearchDto
import com.gowoobro.snippet.core.model.PopularBookDto
import retrofit2.http.GET
import retrofit2.http.Query

/** /books (02-data-api.md §3.6) */
interface BookApi {

    /** 알라딘 검색 (백엔드 프록시, page 1-base) */
    @GET("books/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): List<BookSearchDto>

    /** 국립중앙도서관 인기 대출 도서 (백엔드 프록시) — 빈 값은 미전송(null) */
    @GET("books/popular")
    suspend fun getPopular(
        @Query("startDt") startDt: String? = null,
        @Query("endDt") endDt: String? = null,
        @Query("kdc") kdc: String? = null,
        @Query("dtlKdc") dtlKdc: String? = null,
        @Query("age") age: String? = null,
        @Query("gender") gender: String? = null,
        @Query("region") region: String? = null,
        @Query("dtlRegion") dtlRegion: String? = null,
        @Query("pageNo") pageNo: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): List<PopularBookDto>

    /** 추천 (인증 필요, 앱은 limit=6) — 실패 시 빈 배열로 흡수 */
    @GET("books/recommend")
    suspend fun getRecommend(@Query("limit") limit: Int = 6): List<BookRecommendDto>
}
