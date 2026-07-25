package com.gowoobro.snippet.core.network

import com.gowoobro.snippet.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 모든 요청에 클라이언트 버전/플랫폼 헤더를 붙인다.
 * 서버가 요청 단위로 구버전 클라이언트를 식별할 수 있게 하기 위함
 * (구버전 로깅 및 향후 서버측 차단 백스톱용).
 */
class AppVersionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-App-Platform", "android")
            .build()
        return chain.proceed(request)
    }
}
