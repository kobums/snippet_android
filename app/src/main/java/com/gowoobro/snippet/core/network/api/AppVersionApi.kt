package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.AppVersionDto
import retrofit2.http.GET
import retrofit2.http.Query

/** /appversion — 인증 불필요 (로그인 전에도 호출) */
interface AppVersionApi {

    @GET("appversion")
    suspend fun check(
        @Query("platform") platform: String = "android",
        @Query("version") version: String,
    ): AppVersionDto
}
