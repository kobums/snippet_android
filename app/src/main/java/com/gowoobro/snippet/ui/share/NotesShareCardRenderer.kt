package com.gowoobro.snippet.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordType
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 독서 기록 "메모 이미지" 내보내기용 렌더러.
 *
 * Flutter 원본: features/records/presentation/widgets/notes_export_section.dart
 * - 기록 본문을 4:5(1080×1350) "줄지어진 노트" 카드 1~N장으로 분할 렌더링.
 * - 첫 장: 타입 배지 + 날짜("yyyy년 M월 d일") + 책 제목(굵게) + 저자.
 * - 이어지는 장: 미니 헤더(책 제목 + "i / N").
 * - 본문: 가로 괘선 배경 위에 줄 간격 고정 텍스트.
 * - 푸터: 우측 정렬 "snippet" 워드마크.
 *
 * 페이지 분할은 StaticLayout으로 본문 영역 높이에 들어가는 줄 수를 측정해 줄(line) 경계에서 끊는다.
 * 텍스트가 잘리지 않도록 보장한다.
 */
object NotesShareCardRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    // ── 레이아웃 상수 (1080px 좌표계) ────────────────────────────────────────────
    // Flutter 논리 px(cardW≈360 기준) 대비 약 3배 스케일.
    private const val BODY_H_PAD = 60f          // 본문 좌우 패딩
    private const val BODY_V_PAD = 42f          // 본문 상하 패딩
    private const val LINE_H = 64f              // 한 줄 높이(괘선 간격)
    private const val BODY_FONT = 42f           // 본문 글자 크기

    private const val FIRST_HEADER_H = 250f     // 첫 장 헤더(배지/날짜 + 제목/저자 + 구분선) 높이
    private const val CONT_HEADER_H = 130f       // 이어지는 장 미니 헤더 높이
    private const val FOOTER_H = 110f            // 하단 구분선 + 워드마크 영역 높이

    // ── 색상 ────────────────────────────────────────────────────────────────────
    private const val YELLOW = 0xFFFFCC00.toInt()
    private const val YELLOW_LABEL = 0xFF8B6914.toInt()

    /**
     * 기록을 1~N장의 PNG로 내보내 FileProvider Uri 목록을 반환한다.
     *
     * @param context Context (cacheDir, FileProvider 접근용)
     * @param record  대상 기록
     * @param isDark  다크 카드 여부
     * @return 페이지별 FileProvider Uri 목록 (순서 = 페이지 순서)
     */
    fun renderPages(context: Context, record: RecordDto, isDark: Boolean): List<Uri> {
        val bitmaps = renderBitmaps(record, isDark)
        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val ts = System.currentTimeMillis()
        val authority = "${context.packageName}$PROVIDER_AUTHORITY_SUFFIX"
        return bitmaps.mapIndexed { i, bmp ->
            val file = File(dir, "notes_${ts}_${i + 1}.png")
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bmp.recycle()
            FileProvider.getUriForFile(context, authority, file)
        }
    }

    /** 기록을 페이지별 Bitmap 목록으로 렌더링. */
    fun renderBitmaps(record: RecordDto, isDark: Boolean): List<Bitmap> {
        val pages = splitIntoPages(record.text)
        return pages.mapIndexed { i, body ->
            renderPage(
                record = record,
                bodyText = body,
                isDark = isDark,
                isFirstPage = i == 0,
                pageIndex = i,
                totalPages = pages.size,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 페이지 분할
    // ──────────────────────────────────────────────────────────────────────

    private fun bodyTextPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = BODY_FONT
        typeface = Typeface.DEFAULT
    }

    private fun bodyWidth(): Int = (WIDTH - BODY_H_PAD * 2).toInt()

    /** 해당 장의 본문 영역에 들어갈 수 있는 최대 줄 수. */
    private fun maxLines(isFirst: Boolean): Int {
        val bodyH = HEIGHT - (if (isFirst) FIRST_HEADER_H else CONT_HEADER_H) - FOOTER_H - BODY_V_PAD * 2
        return (bodyH / LINE_H).toInt().coerceAtLeast(1)
    }

    /**
     * 본문 텍스트를 여러 페이지로 분할.
     * StaticLayout으로 전체 텍스트를 본문 폭에 배치한 뒤, 각 장의 maxLines만큼 줄 경계에서 끊는다.
     * 줄 경계 = StaticLayout의 lineEnd offset이므로 단어/줄바꿈이 어색하게 잘리지 않는다.
     */
    fun splitIntoPages(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        val paint = bodyTextPaint()
        val width = bodyWidth()

        val pages = mutableListOf<String>()
        var remaining = text
        var isFirst = true

        while (remaining.isNotEmpty()) {
            val layout = buildLayout(remaining, paint, width)
            val limit = maxLines(isFirst)
            if (layout.lineCount <= limit) {
                pages.add(remaining.trimEnd())
                break
            }
            // limit번째 줄 끝 offset에서 끊는다.
            val cut = layout.getLineEnd(limit - 1).coerceIn(1, remaining.length)
            pages.add(remaining.substring(0, cut).trimEnd())
            remaining = remaining.substring(cut).trimStart()
            isFirst = false
        }
        return if (pages.isEmpty()) listOf(text) else pages
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            // 줄 높이를 LINE_H로 고정 (Flutter forceStrutHeight 대응). 음수 방지.
            .setLineSpacing((LINE_H - paint.fontSpacing).coerceAtLeast(0f), 1f)
        return builder.build()
    }

    // ──────────────────────────────────────────────────────────────────────
    // 단일 페이지 렌더링
    // ──────────────────────────────────────────────────────────────────────

    private fun renderPage(
        record: RecordDto,
        bodyText: String,
        isDark: Boolean,
        isFirstPage: Boolean,
        pageIndex: Int,
        totalPages: Int,
    ): Bitmap {
        val bg = if (isDark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val textPrimary = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
        val textSecondary = if (isDark) 0xFF8E8E93.toInt() else 0xFF6C6C70.toInt()
        val dividerColor = if (isDark) 0xFF38383A.toInt() else 0xFFE5E5EA.toInt()
        val lineColor = if (isDark) 0xFF252527.toInt() else 0xFFF0F0F5.toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)

        val headerH = if (isFirstPage) FIRST_HEADER_H else CONT_HEADER_H

        if (isFirstPage) {
            drawFirstHeader(canvas, record, textPrimary, textSecondary)
        } else {
            drawContHeader(canvas, record, pageIndex, totalPages, textPrimary, textSecondary)
        }

        // 헤더 하단 구분선
        drawHLine(canvas, headerH - 1f, dividerColor)

        // 본문 영역
        val bodyTop = headerH + BODY_V_PAD
        drawBody(canvas, bodyText, bodyTop, textPrimary, lineColor)

        // 푸터 구분선 + 워드마크
        val footerLineY = HEIGHT - FOOTER_H
        drawHLine(canvas, footerLineY, dividerColor)
        drawFooter(canvas, footerLineY, textSecondary)

        return bitmap
    }

    private fun drawFirstHeader(
        canvas: Canvas,
        record: RecordDto,
        textPrimary: Int,
        textSecondary: Int,
    ) {
        // 타입 배지 (좌상단)
        val badgeText = typeLabel(record.type)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = YELLOW_LABEL
        }
        val badgePadH = 22f
        val badgePadV = 10f
        val badgeTextW = badgePaint.measureText(badgeText)
        val badgeTop = 42f
        val badgeH = 30f + badgePadV * 2
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = YELLOW
            alpha = (0.18f * 255).toInt()
        }
        canvas.drawRoundRect(
            BODY_H_PAD, badgeTop, BODY_H_PAD + badgeTextW + badgePadH * 2, badgeTop + badgeH,
            14f, 14f, bgPaint,
        )
        canvas.drawText(
            badgeText, BODY_H_PAD + badgePadH, badgeTop + badgePadV + 26f, badgePaint,
        )

        // 날짜 (우상단)
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            color = textSecondary
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            formatDate(record.createDate), WIDTH - BODY_H_PAD, badgeTop + badgePadV + 26f, datePaint,
        )

        // 책 제목 (굵게)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = textPrimary
        }
        val titleY = badgeTop + badgeH + 70f
        val title = ellipsize(record.bookTitle, titlePaint, WIDTH - BODY_H_PAD * 2)
        canvas.drawText(title, BODY_H_PAD, titleY, titlePaint)

        // 저자
        if (record.bookAuthor.isNotBlank()) {
            val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                color = textSecondary
            }
            canvas.drawText(record.bookAuthor, BODY_H_PAD, titleY + 44f, authorPaint)
        }
    }

    private fun drawContHeader(
        canvas: Canvas,
        record: RecordDto,
        pageIndex: Int,
        totalPages: Int,
        textPrimary: Int,
        textSecondary: Int,
    ) {
        val baseY = 78f
        // "i / N" (우측)
        val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            color = textSecondary
            textAlign = Paint.Align.RIGHT
        }
        val pageLabel = "${pageIndex + 1} / $totalPages"
        canvas.drawText(pageLabel, WIDTH - BODY_H_PAD, baseY, pagePaint)
        val pageLabelW = pagePaint.measureText(pageLabel)

        // 책 제목 (좌측, 남은 폭에서 말줄임)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = textPrimary
        }
        val avail = WIDTH - BODY_H_PAD * 2 - pageLabelW - 24f
        canvas.drawText(ellipsize(record.bookTitle, titlePaint, avail), BODY_H_PAD, baseY, titlePaint)
    }

    private fun drawBody(
        canvas: Canvas,
        bodyText: String,
        bodyTop: Float,
        textColor: Int,
        lineColor: Int,
    ) {
        val width = bodyWidth()
        val paint = bodyTextPaint().apply { color = textColor }
        val layout = buildLayout(bodyText, paint, width)

        // 괘선: 각 줄 baseline 아래에 그림 (텍스트와 함께 자연스럽게 정렬)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = 2f
        }
        val bottomLimit = HEIGHT - FOOTER_H - BODY_V_PAD / 2
        var y = bodyTop + LINE_H - 12f
        while (y < bottomLimit) {
            canvas.drawLine(BODY_H_PAD, y, WIDTH - BODY_H_PAD, y, linePaint)
            y += LINE_H
        }

        // 본문 텍스트
        canvas.save()
        canvas.translate(BODY_H_PAD, bodyTop)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, footerLineY: Float, textSecondary: Int) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = textSecondary
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.1f
        }
        canvas.drawText("snippet", WIDTH - BODY_H_PAD, footerLineY + 60f, brandPaint)
    }

    private fun drawHLine(canvas: Canvas, y: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
        }
        canvas.drawLine(BODY_H_PAD, y, WIDTH - BODY_H_PAD, y, paint)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 유틸
    // ──────────────────────────────────────────────────────────────────────

    private fun typeLabel(type: RecordType): String = when (type) {
        RecordType.SNIPPET -> "스니펫"
        RecordType.DIARY -> "독서일기"
        RecordType.REVIEW -> "리뷰"
    }

    /** ISO LocalDateTime/Date → "yyyy년 M월 d일". 파싱 실패 시 원본 반환. */
    private fun formatDate(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val date = runCatching { LocalDateTime.parse(iso).toLocalDate() }
                .getOrElse { LocalDate.parse(iso.take(10)) }
            date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
        } catch (_: Exception) {
            iso
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
