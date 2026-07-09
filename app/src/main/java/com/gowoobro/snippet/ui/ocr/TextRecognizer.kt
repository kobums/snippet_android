package com.gowoobro.snippet.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.mlkit.vision.text.TextRecognition as MlKitTextRecognition

/**
 * ML Kit 온디바이스 한국어 텍스트 인식 래퍼.
 *
 * 사용 방법:
 * ```
 * val result = TextRecognizer.recognize(context, imageUri)
 * ```
 *
 * 구현 방식:
 * - KoreanTextRecognizerOptions 사용 (한국어 특화, 온디바이스)
 * - Uri → InputImage 변환 후 recognizer.process() 를 코루틴 await으로 래핑
 * - 성공 시 전체 인식 텍스트 반환 (줄 구분 유지)
 *
 * 참고: 04-platform.md §3 "온디바이스 대안 검토 가능: Android ML Kit Text Recognition v2 (korean)"
 * - 현재 snippet_android는 온디바이스 ML Kit 방식을 채택
 * - 백엔드 경유 방식(POST /ocr/extract)으로 교체하려면 이 클래스 대신
 *   OcrApiDataSource(Retrofit multipart)를 구현하면 됨
 */
object TextRecognizer {

    /**
     * 이미지 Uri에서 텍스트를 인식해 반환한다.
     *
     * @param context Android Context
     * @param imageUri 카메라 촬영 또는 갤러리에서 선택한 이미지 Uri
     * @return 인식된 텍스트 (빈 문자열 가능)
     * @throws Exception ML Kit 처리 실패 시
     */
    suspend fun recognize(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = MlKitTextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

            val image = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                cont.resumeWithException(e)
                return@suspendCancellableCoroutine
            }

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
                .addOnCompleteListener {
                    recognizer.close()
                }

            cont.invokeOnCancellation { recognizer.close() }
        }

    /**
     * 이미 디코딩된 Bitmap(영역 크롭본)에서 텍스트를 인식한다.
     * 영역 선택 OCR에서 크롭한 Bitmap을 그대로 넘긴다.
     */
    suspend fun recognizeBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = MlKitTextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
                .addOnCompleteListener { recognizer.close() }
            cont.invokeOnCancellation { recognizer.close() }
        }

    /**
     * 여러 영역(밑줄)을 각각 OCR한 뒤 위→아래 순서로 "\n" 으로 이어 붙인 결과를 반환한다.
     *
     * 다중 밑줄 하이라이터에서 사용한다. 각 [regions] 요소는 원본 Bitmap의
     * 픽셀 좌표(좌상단 원점) 사각형이며, 해당 영역을 크롭해 [recognizeBitmap]로 인식한다.
     * 영역은 호출 측에서 이미 위→아래로 정렬돼 있다고 가정하지 않고, 여기서 top 기준 정렬한다.
     *
     * @param bitmap 원본(업라이트) Bitmap
     * @param regions 인식할 영역들의 픽셀 좌표 Rect 목록
     * @return 영역별 인식 텍스트를 위→아래로 "\n"으로 이어 붙인 문자열 (빈 영역은 제외)
     */
    suspend fun recognizeRegions(bitmap: Bitmap, regions: List<Rect>): String {
        if (regions.isEmpty()) return ""
        val parts = regions
            .sortedBy { it.top }
            .mapNotNull { region ->
                val crop = cropToRegion(bitmap, region) ?: return@mapNotNull null
                val text = runCatching { recognizeBitmap(crop) }.getOrDefault("")
                if (crop != bitmap) crop.recycle()
                text.trim().ifBlank { null }
            }
        return parts.joinToString("\n")
    }

    /** 픽셀 좌표 Rect로 Bitmap을 크롭한다. 영역이 이미지 밖이거나 비어 있으면 null. */
    private fun cropToRegion(src: Bitmap, region: Rect): Bitmap? {
        val left = region.left.coerceIn(0, src.width - 1)
        val top = region.top.coerceIn(0, src.height - 1)
        val width = region.width().coerceIn(1, src.width - left)
        val height = region.height().coerceIn(1, src.height - top)
        if (width <= 0 || height <= 0) return null
        return runCatching { Bitmap.createBitmap(src, left, top, width, height) }.getOrNull()
    }

    /**
     * Uri를 EXIF 회전까지 반영해 업라이트 Bitmap으로 디코딩한다.
     * 화면 표시용·크롭용 모두 이 Bitmap을 쓰면 좌표가 일치한다.
     */
    fun loadUprightBitmap(context: Context, imageUri: Uri): Bitmap {
        val source = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("이미지를 불러올 수 없습니다")

        val orientation = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
