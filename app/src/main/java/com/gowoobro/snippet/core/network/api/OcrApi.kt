package com.gowoobro.snippet.core.network.api

import com.gowoobro.snippet.core.model.OcrResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** /ocr — 백엔드 프록시 (02-data-api.md §3.10) */
interface OcrApi {

    /**
     * OCR 텍스트 추출 (multipart/form-data).
     *
     * @param image 촬영/선택한 이미지 파일 파트 (필수)
     * @param engine "google"(기본) 또는 "naver" — text/plain RequestBody
     * @param regions 인식 영역 JSON 배열 문자열 `[{"left":..,"top":..,"right":..,"bottom":..}]` (이미지 픽셀 좌표)
     */
    @Multipart
    @POST("ocr/extract")
    suspend fun extract(
        @Part image: MultipartBody.Part,
        @Part("engine") engine: RequestBody? = null,
        @Part("regions") regions: RequestBody? = null,
    ): OcrResponse
}
