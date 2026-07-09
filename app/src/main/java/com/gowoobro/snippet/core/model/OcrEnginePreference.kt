package com.gowoobro.snippet.core.model

/**
 * OCR 엔진 사용자 설정.
 *
 * Flutter 앱의 "OCR 엔진 선택"(Google/Naver) 재현 + 네이티브 온디바이스(ML Kit) 옵션.
 * ON_DEVICE면 [com.gowoobro.snippet.ui.ocr.TextRecognizer], 그 외는 백엔드 `/ocr/extract`([OcrEngine]) 사용.
 */
enum class OcrEnginePreference(val value: String, val label: String) {
    ON_DEVICE("ondevice", "온디바이스 (기본)"),
    GOOGLE("google", "Google Vision (정확도)"),
    NAVER("naver", "Naver Clova (한글 특화)");

    /** 백엔드 엔진 (온디바이스면 null) */
    val backendEngine: OcrEngine?
        get() = when (this) {
            ON_DEVICE -> null
            GOOGLE -> OcrEngine.GOOGLE
            NAVER -> OcrEngine.NAVER
        }

    companion object {
        fun fromValue(value: String?): OcrEnginePreference =
            entries.firstOrNull { it.value == value } ?: ON_DEVICE
    }
}
