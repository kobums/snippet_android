package com.gowoobro.snippet.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.gowoobro.snippet.core.model.UserBookDto
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * 독서 캘린더 공유 이미지(4:5, 1080×1350) 렌더러.
 *
 * Flutter 원본: features/dashboard/presentation/widgets/shareable_reading_calendar.dart
 * - 월 달력 그리드. 각 날짜 셀에 그 날 완독(endDate)한 책 표지를 쌓아 표시.
 * - 날짜 배지(좌상단), 여러 권이면 "N권" 배지(우하단).
 * - 타이틀 "{year}년 {month}월". 라이트/다크 대응.
 *
 * ⚠️ 표지는 네트워크 이미지이므로 Canvas 그리기 전에 Coil로 Bitmap을 먼저 다운로드해야 한다.
 */
object CalendarShareRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * 캘린더를 렌더링해 PNG로 저장 후 FileProvider Uri 반환.
     * 표지 이미지는 내부에서 Coil로 다운로드한다. (suspend — IO 디스패처에서 호출 권장)
     *
     * @param showStats true면 마지막 행 목~토 4칸 자리에 통계 셀 2개(완독한 책/총 페이지)를 인라인 표시
     */
    suspend fun renderToUri(
        context: Context,
        year: Int,
        month: Int,
        completedBooks: List<UserBookDto>,
        isDark: Boolean,
        showStats: Boolean = true,
    ): Uri {
        // 1) 표지 다운로드 (URL → Bitmap 캐시 맵)
        val covers = downloadCovers(context, completedBooks)
        // 2) Canvas 렌더링
        val bitmap = render(year, month, completedBooks, covers, isDark, showStats)
        // 3) 저장 + Uri
        return saveBitmapAndGetUri(context, bitmap)
    }

    /** coverUrl → Bitmap. 실패/빈 URL은 맵에서 제외. */
    private suspend fun downloadCovers(
        context: Context,
        books: List<UserBookDto>,
    ): Map<String, Bitmap> {
        val loader = ImageLoader(context)
        val urls = books.map { it.coverUrl }.filter { it.isNotBlank() }.distinct()
        val result = mutableMapOf<String, Bitmap>()
        for (url in urls) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // Canvas에 그리려면 소프트웨어 Bitmap 필요
                    .build()
                val res = loader.execute(request)
                if (res is SuccessResult) {
                    (res.drawable as? BitmapDrawable)?.bitmap?.let { result[url] = it }
                }
            } catch (_: Exception) {
                // 개별 표지 실패는 무시 (플레이스홀더로 대체)
            }
        }
        return result
    }

    // ──────────────────────────────────────────────────────────────────────
    // Canvas 렌더링
    // ──────────────────────────────────────────────────────────────────────

    private fun render(
        year: Int,
        month: Int,
        completedBooks: List<UserBookDto>,
        covers: Map<String, Bitmap>,
        isDark: Boolean,
        showStats: Boolean,
    ): Bitmap {
        val bgColor = if (isDark) 0xFF1C1C1E.toInt() else 0xFFF2F2F7.toInt()
        val cardBg = if (isDark) 0xFF2C2C2E.toInt() else 0xFFFFFFFF.toInt()
        val textPrimary = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
        val textSecondary = if (isDark) 0xFFAEAEB2.toInt() else 0xFF6C6C70.toInt()
        val textTertiary = if (isDark) 0xFF636366.toInt() else 0xFFAEAEB2.toInt()
        val primary = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A2E1F.toInt()
        val emptyCellBg = if (isDark) 0x0DFFFFFF else 0x0D000000
        val emptyCellBorder = if (isDark) 0x1AFFFFFF else 0x1A000000
        val sundayColor = 0xFFE53935.toInt()
        val saturdayColor = 0xFF1E88E5.toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        // ── 타이틀 — 작게(전체 폭의 ~4%), 아래 여백 ──
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = WIDTH * 0.04f
            color = textPrimary
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${year}년 ${month}월", WIDTH / 2f, 88f, titlePaint)

        // ── 카드 영역 — 남은 세로 공간 전부 사용 ──
        val cardMargin = 32f
        val cardTop = 136f
        val cardBottom = HEIGHT - cardMargin
        val cardRect = RectF(cardMargin, cardTop, WIDTH - cardMargin, cardBottom)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
        canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)

        val cardPad = 28f
        val gridLeft = cardRect.left + cardPad
        val gridRight = cardRect.right - cardPad
        val gridWidth = gridRight - gridLeft
        val colWidth = gridWidth / 7f

        // ── 요일 헤더 ──
        val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerY = cardRect.top + cardPad + 36f
        weekdays.forEachIndexed { idx, wd ->
            headerPaint.color = when (idx) {
                0 -> sundayColor
                6 -> saturdayColor
                else -> textSecondary
            }
            canvas.drawText(wd, gridLeft + colWidth * idx + colWidth / 2, headerY, headerPaint)
        }

        // ── 그리드 셀 ──
        val firstDay = LocalDate.of(year, month, 1)
        val daysInMonth = firstDay.lengthOfMonth()
        val startOffset = firstDay.dayOfWeek.value % 7
        val completedByDay = completedBooks.groupBy { dayOfEndDate(it, year, month) }

        val gridTop = headerY + 28f
        val gridBottom = cardRect.bottom - cardPad
        val rows = 6
        val cellGap = 8f
        val rowHeight = (gridBottom - gridTop) / rows

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }

        for (row in 0 until rows) {
            for (col in 0..6) {
                // 통계 표시 시 마지막 행 목~토 4칸은 통계 셀 자리로 비워둔다
                if (showStats && row == rows - 1 && col >= 3) continue

                val cellIdx = row * 7 + col
                val day = cellIdx - startOffset + 1
                if (day !in 1..daysInMonth) continue

                val cellLeft = gridLeft + colWidth * col + cellGap / 2
                val cellTop = gridTop + rowHeight * row + cellGap / 2
                val cellRight = cellLeft + colWidth - cellGap
                val cellBottom = cellTop + rowHeight - cellGap
                val cellRect = RectF(cellLeft, cellTop, cellRight, cellBottom)

                val booksOnDay = completedByDay[day].orEmpty()

                if (booksOnDay.isEmpty()) {
                    // 빈 셀
                    val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emptyCellBg }
                    canvas.drawRoundRect(cellRect, 12f, 12f, emptyPaint)
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = emptyCellBorder
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    canvas.drawRoundRect(cellRect, 12f, 12f, borderPaint)
                    datePaint.color = textTertiary
                    val fm = datePaint.fontMetrics
                    val cy = cellRect.centerY() - (fm.ascent + fm.descent) / 2
                    canvas.drawText("$day", cellRect.centerX(), cy, datePaint)
                } else {
                    drawBookCell(canvas, cellRect, day, booksOnDay, covers, primary, isDark)
                }
            }
        }

        // ── 통계 셀 2개 — 마지막 행 목~토 4칸 자리에 인라인 (각 2칸 폭) ──
        if (showStats) {
            val statRow = rows - 1
            val statTop = gridTop + rowHeight * statRow + cellGap / 2
            val statBottom = statTop + rowHeight - cellGap
            val stat1 = RectF(
                gridLeft + colWidth * 3 + cellGap / 2,
                statTop,
                gridLeft + colWidth * 5 - cellGap / 2,
                statBottom,
            )
            val stat2 = RectF(
                gridLeft + colWidth * 5 + cellGap / 2,
                statTop,
                gridLeft + colWidth * 7 - cellGap / 2,
                statBottom,
            )
            val totalPages = completedBooks.sumOf { it.totalPage }
            drawStatCell(canvas, stat1, "${completedBooks.size}권", "완독한 책", primary)
            drawStatCell(canvas, stat2, "${totalPages}쪽", "총 페이지", primary)
        }

        return bitmap
    }

    /**
     * 통계 셀 — (책 아이콘 + 값 + 라벨) 세로 배치.
     * 아이콘은 심플한 펼친 책 모양을 직접 그린다. 다크에서는 primary=흰색 계열.
     */
    private fun drawStatCell(
        canvas: Canvas,
        rect: RectF,
        value: String,
        label: String,
        primary: Int,
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()

        // 아이콘: 라운드 사각(책) + 중앙 스파인
        val iconW = 52f
        val iconH = 42f
        val iconTop = cy - 78f
        val iconRect = RectF(cx - iconW / 2, iconTop, cx + iconW / 2, iconTop + iconH)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(iconRect, 8f, 8f, iconPaint)
        canvas.drawLine(cx, iconRect.top + 4f, cx, iconRect.bottom - 4f, iconPaint)

        // 값
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 40f
            color = primary
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(value, cx, cy + 18f, valuePaint)

        // 라벨
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            color = primary
            alpha = (0.7f * 255).toInt()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, cx, cy + 56f, labelPaint)
    }

    /** 표지가 있는 날짜 셀 (표지 스택 + 날짜 배지 + N권 배지). */
    private fun drawBookCell(
        canvas: Canvas,
        cell: RectF,
        day: Int,
        books: List<UserBookDto>,
        covers: Map<String, Bitmap>,
        primary: Int,
        isDark: Boolean,
    ) {
        val radius = 12f
        // 클립 적용
        canvas.save()
        val clipPath = android.graphics.Path().apply {
            addRoundRect(cell, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        if (books.size == 1) {
            drawCover(canvas, books.first().coverUrl, covers, cell)
        } else {
            // 최대 4장 스택 (Flutter: reversed.take(4), 10% offset)
            val show = books.take(4)
            val offsetX = cell.width() * 0.10f
            val offsetY = cell.height() * 0.10f
            val scale = (1.0f - ((show.size - 1) * 0.1f)).coerceIn(0.7f, 1.0f)
            val baseW = cell.width() * scale
            val baseH = cell.height() * scale
            // 뒤쪽(index 큰)부터 그려 앞쪽이 위로 오게
            for (i in show.indices.reversed()) {
                val layerRect = RectF(
                    cell.left + offsetX * i,
                    cell.top + offsetY * i,
                    cell.left + offsetX * i + baseW,
                    cell.top + offsetY * i + baseH,
                )
                drawCover(canvas, show[i].coverUrl, covers, layerRect)
            }
        }
        canvas.restore()

        // 날짜 배지 (좌상단, 검정 반투명)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            alpha = (0.7f * 255).toInt()
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dayStr = "$day"
        val dtw = badgeTextPaint.measureText(dayStr)
        val bx = cell.left + 8f
        val by = cell.top + 8f
        val badgeRect = RectF(bx, by, bx + dtw + 20f, by + 40f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
        canvas.drawText(dayStr, bx + 10f, by + 30f, badgeTextPaint)

        // N권 배지 (우하단)
        if (books.size > 1) {
            val nBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primary
                alpha = (0.95f * 255).toInt()
            }
            val nTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 26f
                color = if (isDark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val nStr = "${books.size}권"
            val ntw = nTextPaint.measureText(nStr)
            val nRight = cell.right - 8f
            val nBottom = cell.bottom - 8f
            val nRect = RectF(nRight - ntw - 20f, nBottom - 38f, nRight, nBottom)
            canvas.drawRoundRect(nRect, 8f, 8f, nBadgePaint)
            canvas.drawText(nStr, nRect.left + 10f, nBottom - 10f, nTextPaint)
        }
    }

    /** 표지 Bitmap을 셀에 cover(중앙 크롭)로 그림. 없으면 플레이스홀더. */
    private fun drawCover(canvas: Canvas, url: String, covers: Map<String, Bitmap>, dst: RectF) {
        val bmp = covers[url]
        if (bmp == null) {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF2196F3.toInt()
                alpha = (0.2f * 255).toInt()
            }
            canvas.drawRect(dst, ph)
            return
        }
        // BoxFit.cover: 종횡비 유지하며 셀을 가득 채우도록 소스 crop
        val srcRatio = bmp.width.toFloat() / bmp.height
        val dstRatio = dst.width() / dst.height()
        val src: Rect
        if (srcRatio > dstRatio) {
            // 소스가 더 넓음 → 좌우 crop
            val newW = (bmp.height * dstRatio).toInt()
            val left = (bmp.width - newW) / 2
            src = Rect(left, 0, left + newW, bmp.height)
        } else {
            // 소스가 더 높음 → 상하 crop
            val newH = (bmp.width / dstRatio).toInt()
            val top = (bmp.height - newH) / 2
            src = Rect(0, top, bmp.width, top + newH)
        }
        canvas.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** endDate가 해당 year/month에 속하면 day, 아니면 -1. */
    private fun dayOfEndDate(book: UserBookDto, year: Int, month: Int): Int {
        val ed = book.endDate
        if (ed.length < 10) return -1
        return try {
            val date = LocalDate.parse(ed.take(10))
            if (date.year == year && date.monthValue == month) date.dayOfMonth else -1
        } catch (_: Exception) {
            -1
        }
    }

    /** Bitmap → cacheDir/share_images/calendar_{ts}.png 저장 후 FileProvider Uri 반환. */
    private fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val file = File(dir, "calendar_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val authority = "${context.packageName}$PROVIDER_AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
