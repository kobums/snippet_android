package com.gowoobro.snippet.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * SNS 공유 카드 이미지(Bitmap) 생성기.
 *
 * 스펙 출처: docs/native-migration/04-platform.md §5
 * - 크기: 1080 × 1350 (Instagram 피드 4:5 비율), pixelRatio 3 상당
 * - 배경: 브랜드 그라데이션(다크 그린–블랙)
 * - 레이아웃: 상단 앱 워드마크 "Snippet" → 중앙 인용 텍스트 → 하단 책 제목·저자 + 구분선 + 통계
 * - 생성된 PNG를 cacheDir/share_images/share_{ts}.png 에 저장 후 FileProvider Uri 반환
 */
object ShareCardRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val CORNER_RADIUS = 36f
    private const val PADDING_H = 80f
    private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * 세션 완료 공유 카드 생성.
     *
     * @param context  Context (cacheDir, FileProvider 접근용)
     * @param bookTitle 책 제목
     * @param elapsedSeconds 독서 시간(초)
     * @param pagesRead 읽은 페이지 수
     * @param paceMinPerPage 분당 페이지(min/p)
     * @return FileProvider Uri (image/png)
     */
    fun renderSessionCard(
        context: Context,
        bookTitle: String,
        elapsedSeconds: Long,
        pagesRead: Int,
        paceMinPerPage: Double,
    ): Uri {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawWordmark(canvas)
        drawDivider(canvas, y = 160f, alpha = 80)
        drawSessionContent(canvas, bookTitle, elapsedSeconds, pagesRead, paceMinPerPage)
        drawBottomBranding(canvas)

        return saveBitmapAndGetUri(context, bitmap)
    }

    /**
     * 스니펫(인용문) 공유 카드 생성.
     *
     * @param context   Context
     * @param quoteText 인용 문장
     * @param bookTitle 책 제목 (비공개 상태에서 공유 시 빈 문자열 가능)
     * @param bookAuthor 저자
     * @return FileProvider Uri (image/png)
     */
    fun renderSnippetCard(
        context: Context,
        quoteText: String,
        bookTitle: String,
        bookAuthor: String,
    ): Uri {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawWordmark(canvas)
        drawDivider(canvas, y = 160f, alpha = 80)
        drawSnippetContent(canvas, quoteText, bookTitle, bookAuthor)
        drawBottomBranding(canvas)

        return saveBitmapAndGetUri(context, bitmap)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private drawing helpers
    // ──────────────────────────────────────────────────────────────────────

    /** 브랜드 그라데이션 배경 (다크 그린 → 딥 블랙) */
    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(
                    0xFF1A2E1F.toInt(), // 다크 그린
                    0xFF0D1A0F.toInt(), // 딥 그린-블랙
                    0xFF0A0A0A.toInt(), // 거의 블랙
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val rect = RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, paint)
    }

    /** 앱 워드마크 "Snippet" */
    private fun drawWordmark(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.25f
            alpha = 230
        }
        canvas.drawText("Snippet", PADDING_H, 108f, paint)

        // 서브타이틀: "Blind Book Curation"
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 28f
            alpha = 128
        }
        canvas.drawText("Blind Book Curation", PADDING_H, 148f, subPaint)
    }

    /** 수평 구분선 */
    private fun drawDivider(canvas: Canvas, y: Float, alpha: Int = 60) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            this.alpha = alpha
            strokeWidth = 1.5f
        }
        canvas.drawLine(PADDING_H, y, WIDTH - PADDING_H, y, paint)
    }

    /** 세션 완료 카드 본문 */
    private fun drawSessionContent(
        canvas: Canvas,
        bookTitle: String,
        elapsedSeconds: Long,
        pagesRead: Int,
        paceMinPerPage: Double,
    ) {
        val centerX = WIDTH / 2f

        // 이모지 + "독서 완료"
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF34C759.toInt()  // BrandGreen
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("독서 완료", centerX, 300f, titlePaint)

        // 책 제목
        val bookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            alpha = 230
        }
        val truncatedTitle = if (bookTitle.length > 24) "${bookTitle.take(24)}…" else bookTitle
        canvas.drawText(truncatedTitle, centerX, 380f, bookPaint)

        // 큰 타이머 표시
        val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 120f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(formatElapsed(elapsedSeconds), centerX, 560f, timerPaint)

        val timerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
            alpha = 160
        }
        canvas.drawText("독서 시간", centerX, 610f, timerLabelPaint)

        drawDivider(canvas, y = 670f, alpha = 50)

        // 통계 3열 (읽은 페이지 / 페이스)
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF34C759.toInt()
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 28f
            textAlign = Paint.Align.CENTER
            alpha = 160
        }

        val col1 = WIDTH / 3f
        val col2 = WIDTH * 2f / 3f

        canvas.drawText("${pagesRead}p", col1, 770f, statValuePaint)
        canvas.drawText("읽은 페이지", col1, 810f, statLabelPaint)

        val paceStr = if (paceMinPerPage > 0) "${"%.1f".format(paceMinPerPage)}m/p" else "-"
        canvas.drawText(paceStr, col2, 770f, statValuePaint)
        canvas.drawText("페이스", col2, 810f, statLabelPaint)
    }

    /** 스니펫(인용문) 카드 본문 */
    private fun drawSnippetContent(
        canvas: Canvas,
        quoteText: String,
        bookTitle: String,
        bookAuthor: String,
    ) {
        val centerX = WIDTH / 2f

        // 여는 인용 부호 (큰 장식)
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF34C759.toInt()
            textSize = 160f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            alpha = 180
        }
        canvas.drawText("“", PADDING_H - 12f, 340f, quotePaint)

        // 인용 텍스트 — 멀티라인 수동 처리
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 46f
            alpha = 230
        }
        val maxWidth = WIDTH - PADDING_H * 2
        val lines = wrapText(quoteText, textPaint, maxWidth.toInt(), maxLines = 8)
        var textY = 400f
        for (line in lines) {
            canvas.drawText(line, PADDING_H, textY, textPaint)
            textY += 64f
        }

        // 닫는 인용 부호
        val closeQuotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF34C759.toInt()
            textSize = 160f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            alpha = 180
        }
        canvas.drawText("”", WIDTH - PADDING_H + 12f, textY + 60f, closeQuotePaint)

        // 책 제목 / 저자 (하단)
        if (bookTitle.isNotBlank()) {
            drawDivider(canvas, y = 1080f, alpha = 50)
            val bookTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                alpha = 200
            }
            val truncated = if (bookTitle.length > 28) "${bookTitle.take(28)}…" else bookTitle
            canvas.drawText(truncated, centerX, 1140f, bookTitlePaint)

            if (bookAuthor.isNotBlank()) {
                val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 30f
                    textAlign = Paint.Align.CENTER
                    alpha = 140
                }
                canvas.drawText(bookAuthor, centerX, 1185f, authorPaint)
            }
        }
    }

    /** 하단 워터마크 / 브랜딩 영역 */
    private fun drawBottomBranding(canvas: Canvas) {
        drawDivider(canvas, y = HEIGHT - 130f, alpha = 50)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 28f
            alpha = 100
        }
        canvas.drawText("snippet · Blind Book Curation", PADDING_H, HEIGHT - 80f, brandPaint)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ──────────────────────────────────────────────────────────────────────

    /** 초 → HH:MM:SS 또는 MM:SS */
    private fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    /**
     * 주어진 Paint 기준으로 텍스트를 maxWidth 이하로 줄 바꿈.
     * 공백 기준 word-wrap, 최대 [maxLines]줄.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Int, maxLines: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (lines.size >= maxLines) break
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    // 단어 하나가 너무 길면 그냥 추가
                    lines.add(word)
                }
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) {
            lines.add(current.toString())
        }
        return lines
    }

    /** Bitmap → cacheDir/share_images/share_{ts}.png 저장 후 FileProvider Uri 반환 */
    private fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val authority = "${context.packageName}$PROVIDER_AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
