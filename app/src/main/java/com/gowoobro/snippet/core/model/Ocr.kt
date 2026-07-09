package com.gowoobro.snippet.core.model

import kotlinx.serialization.Serializable

/** OCR 엔진 — multipart `engine` 필드 값 (JSON 직렬화 아님) */
enum class OcrEngine(val value: String) {
    GOOGLE("google"),
    NAVER("naver"),
}

/** POST /ocr/extract 의 `regions` 파트 — 이미지 픽셀 좌표 */
@Serializable
data class OcrRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/** POST /ocr/extract 응답 — confidence 0~100, 앱은 extractedText만 사용 */
@Serializable
data class OcrResponse(
    val extractedText: String = "",
    val confidence: Int = 0,
)
