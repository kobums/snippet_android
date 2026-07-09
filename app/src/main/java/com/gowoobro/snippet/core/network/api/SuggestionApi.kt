package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.SuggestionAddRequest
import com.gowoobro.snippet.core.model.SuggestionDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** /suggestions — 인증 필요 (02-data-api.md §3.9) */
interface SuggestionApi {

    /** 건의 등록 — title이 빈 문자열이면 null로 전달해 키 생략 */
    @POST("suggestions")
    suspend fun add(@Body body: SuggestionAddRequest): SuggestionDto

    /** 내 건의 목록 (앱 미사용, 백엔드 존재) */
    @GET("suggestions/mine")
    suspend fun getMine(): List<SuggestionDto>
}
