package com.gowoobro.snippet.ui.library

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google Code Scanner(GMS) 기반 ISBN 바코드 스캔.
 *
 * 별도 카메라 권한·프리뷰 UI 없이 Play Services가 제공하는 풀스크린 스캐너를 띄운다.
 * 도서 바코드는 EAN-13(ISBN-13)이 표준이며, 일부 부가기호는 EAN-8로 읽힐 수 있어 함께 허용한다.
 *
 * @param onIsbn 스캔 성공 시 인식된 숫자 코드(rawValue) 전달
 * @param onError 스캔 실패 시 사용자에게 보여줄 메시지 전달(취소는 호출되지 않음)
 */
fun launchIsbnScan(
    context: Context,
    onIsbn: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
        .build()

    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val code = barcode.rawValue?.filter { it.isDigit() }
            if (code.isNullOrBlank()) {
                onError("바코드를 인식하지 못했습니다")
            } else {
                onIsbn(code)
            }
        }
        .addOnCanceledListener { /* 사용자 취소 — 무시 */ }
        .addOnFailureListener {
            onError("바코드 스캔을 사용할 수 없습니다")
        }
}
