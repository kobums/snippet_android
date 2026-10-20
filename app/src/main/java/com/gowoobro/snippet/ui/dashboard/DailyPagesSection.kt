package com.gowoobro.snippet.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pages
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.core.model.DailyPagesDto
import com.gowoobro.snippet.core.util.DateFormats
import com.gowoobro.snippet.ui.components.MonthNavigator
import com.gowoobro.snippet.ui.components.SectionHeader
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * 서버 응답(pages > 0인 날만)을 해당 월 일수 길이의 배열로 펼친다. 빠진 날은 0.
 * index 0 = 1일. 다른 달 날짜·파싱 실패 항목은 무시하고, 같은 날이 중복되면 합산한다.
 */
internal fun fillDailyPages(year: Int, month: Int, data: List<DailyPagesDto>): List<Int> {
    val ym = YearMonth.of(year, month)
    val pages = IntArray(ym.lengthOfMonth())
    data.forEach { item ->
        val date = DateFormats.parseDate(item.date) ?: return@forEach
        if (date.year == year && date.monthValue == month && item.pages > 0) {
            pages[date.dayOfMonth - 1] += item.pages
        }
    }
    return pages.toList()
}

/**
 * 대시보드 통계 탭 — 일별 읽은 페이지 섹션.
 * 월 이동은 대시보드 공용 선택 월(상단 년월 캡슐)과 동기화된다.
 */
@Composable
fun DailyPagesSection(
    year: Int,
    month: Int,
    dailyPages: List<DailyPagesDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    nextEnabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = remember(year, month, dailyPages) { fillDailyPages(year, month, dailyPages) }
    val total = values.sum()
    val daysRead = values.count { it > 0 }
    val bestIndex = if (total > 0) values.indices.maxByOrNull { values[it] } else null

    Column(modifier = modifier) {
        SectionHeader(title = "일별 읽은 페이지")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthNavigator(
                    year = year,
                    month = month,
                    onPrevious = onPrevMonth,
                    onNext = onNextMonth,
                    nextEnabled = nextEnabled,
                )

                when {
                    isLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DailyPagesBodyHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }

                    errorMessage != null -> DailyPagesMessage(
                        icon = { tint ->
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = tint)
                        },
                        title = "일별 기록을 불러오지 못했어요",
                        description = errorMessage,
                        actionLabel = "다시 시도",
                        onAction = onRetry,
                    )

                    total <= 0 -> DailyPagesMessage(
                        icon = { tint ->
                            Icon(Icons.Outlined.Pages, contentDescription = null, tint = tint)
                        },
                        title = "${month}월에 읽은 페이지가 없어요",
                        description = "책 진도를 기록하면 날짜별로 여기에 쌓여요",
                    )

                    else -> {
                        DailyPagesSummary(
                            total = total,
                            daysRead = daysRead,
                            average = (total.toDouble() / daysRead).roundToInt(),
                            bestDay = bestIndex?.plus(1),
                            bestPages = bestIndex?.let { values[it] } ?: 0,
                        )
                        DailyPagesBarChart(
                            month = month,
                            values = values,
                            bestIndex = bestIndex,
                            chartHeight = 140.dp,
                        )
                    }
                }
            }
        }
    }
}

/** 로딩/빈/에러 상태가 같은 높이를 차지하도록 맞춰 월 이동 시 카드가 출렁이지 않게 한다 */
private val DailyPagesBodyHeight = 200.dp

@Composable
private fun DailyPagesMessage(
    icon: @Composable (tint: Color) -> Unit,
    title: String,
    description: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(DailyPagesBodyHeight)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 요약 2×2 — 총 페이지 / 읽은 날 / 하루 평균 / 최고 기록 */
@Composable
private fun DailyPagesSummary(
    total: Int,
    daysRead: Int,
    average: Int,
    bestDay: Int?,
    bestPages: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(label = "이번 달 총", value = "${total}쪽", modifier = Modifier.weight(1f))
            SummaryCell(label = "읽은 날", value = "${daysRead}일", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(label = "하루 평균", value = "${average}쪽", modifier = Modifier.weight(1f))
            SummaryCell(
                label = "최고 기록",
                value = if (bestDay != null) "${bestDay}일 · ${bestPages}쪽" else "-",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * 일별 막대 차트 — MinimalMonthlyBarChart와 같은 미니멀 톤(그리드/축선 없음, 상단만 라운드).
 * - 0쪽인 날은 바닥에 작은 눈금만 표시해 날짜 칸을 가늠할 수 있게 한다
 * - 최고 기록 날은 primary 색으로 강조
 * - 막대를 탭하면 해당 날짜 값이 막대 위·하단 문구로 표시된다 (다시 탭하면 해제)
 */
@Composable
private fun DailyPagesBarChart(
    month: Int,
    values: List<Int>,
    bestIndex: Int?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
) {
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val days = values.size
    val maxVal = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    // X축 라벨: 1, 5, 10, 15, 20, 25, 말일
    val labeledDays = remember(days) { (listOf(1, 5, 10, 15, 20, 25) + days).distinct() }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics {
                    contentDescription = "${month}월 일별 읽은 페이지 차트, 총 ${values.sum()}쪽"
                }
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / days
                        val idx = (offset.x / slot).toInt().coerceIn(0, days - 1)
                        selected = if (selected == idx || values[idx] <= 0) null else idx
                    }
                },
        ) {
            val slot = size.width / days
            val barW = slot * 0.6f
            val valueSpace = 16.dp.toPx()
            val axisSpace = 16.dp.toPx()
            val chartH = size.height - valueSpace - axisSpace
            val baseline = valueSpace + chartH
            val radius = CornerRadius(barW / 2f, barW / 2f)

            values.forEachIndexed { idx, pages ->
                val left = slot * idx + (slot - barW) / 2f
                if (pages <= 0) {
                    // 빈 날 — 바닥 눈금
                    drawRect(
                        color = tickColor,
                        topLeft = Offset(left, baseline - 2.dp.toPx()),
                        size = Size(barW, 2.dp.toPx()),
                    )
                } else {
                    val barH = (chartH * pages / maxVal).coerceAtLeast(3.dp.toPx())
                    val top = baseline - barH
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(Offset(left, top), Size(barW, barH)),
                                topLeft = radius,
                                topRight = radius,
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero,
                            ),
                        )
                    }
                    val isHighlighted = idx == selected || (selected == null && idx == bestIndex)
                    drawPath(path = path, color = if (isHighlighted) highlightColor else barColor)

                    // 값 라벨 — 선택한 날(미선택 시 최고 기록 날)만 표기해 31칸이 겹치지 않게 한다
                    if (isHighlighted) {
                        val layout = textMeasurer.measure("$pages", valueStyle)
                        val x = (left + barW / 2f - layout.size.width / 2f)
                            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(x, (top - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)),
                        )
                    }
                }
            }

            // X축 날짜 라벨
            labeledDays.forEach { day ->
                val layout = textMeasurer.measure("$day", axisStyle)
                val center = slot * (day - 1) + slot / 2f
                val x = (center - layout.size.width / 2f)
                    .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x, baseline + 4.dp.toPx()),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        val sel = selected
        Text(
            text = if (sel != null) {
                "${month}월 ${sel + 1}일 · ${values[sel]}쪽"
            } else {
                "막대를 눌러 날짜별 기록을 확인하세요"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (sel != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
