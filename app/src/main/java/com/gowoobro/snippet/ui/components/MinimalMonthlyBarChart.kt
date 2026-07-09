package com.gowoobro.snippet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.core.model.MonthlyStatsDto

/**
 * 미니멀 월별 완독 막대 차트.
 *
 * - 그리드라인·점선·축선 없음
 * - 막대는 칸 폭의 55%, 상단만 라운드, onSurface 모노크롬
 * - 값(>0)은 막대 위 작은 라벨로 직접 표기
 * - X축은 월 라벨만 흐린 색으로
 */
@Composable
fun MinimalMonthlyBarChart(
    data: List<MonthlyStatsDto>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
) {
    val barColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = barColor,
        fontWeight = FontWeight.SemiBold,
    )

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            val maxVal = data.maxOfOrNull { it.completedCount } ?: 0
            val effectiveMax = if (maxVal <= 0) 1 else maxVal
            val slot = size.width / 12f
            val barW = slot * 0.55f
            val labelSpace = 18.dp.toPx()
            val chartH = size.height - labelSpace

            for (month in 1..12) {
                val count = data.firstOrNull { it.month == month }?.completedCount ?: 0
                if (count <= 0) continue

                val barH = chartH * (count.toFloat() / effectiveMax)
                val left = slot * (month - 1) + (slot - barW) / 2f
                val top = labelSpace + (chartH - barH)
                val radius = CornerRadius(barW / 2f, barW / 2f)

                // 상단만 라운드된 막대
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(Offset(left, top), androidx.compose.ui.geometry.Size(barW, barH)),
                            topLeft = radius,
                            topRight = radius,
                            bottomLeft = CornerRadius.Zero,
                            bottomRight = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path = path, color = barColor)

                // 값 라벨 — 막대 위
                val layout = textMeasurer.measure("$count", valueStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = left + barW / 2f - layout.size.width / 2f,
                        y = (top - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 월 라벨 — 흐린 색
        Row(modifier = Modifier.fillMaxWidth()) {
            (1..12).forEach { month ->
                Text(
                    text = "$month",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
