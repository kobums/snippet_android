package com.gowoobro.snippet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 월 네비게이터 — `◀ 2026년 6월 ▶` 칩.
 * 글래스 칩 원본을 M3 tonal surface로 대체 (03-design-system.md §7.4).
 *
 * @param nextEnabled 미래로 이동 금지 시 false (▶ 비활성)
 * @param onTitleClick 가운데 라벨 탭 (년/월 피커 시트 열기 등). null이면 탭 불가.
 */
@Composable
fun MonthNavigator(
    year: Int,
    month: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
) {
    NavigatorChip(
        label = "${year}년 ${month}월",
        onPrevious = onPrevious,
        onNext = onNext,
        modifier = modifier,
        previousEnabled = previousEnabled,
        nextEnabled = nextEnabled,
        onTitleClick = onTitleClick,
    )
}

/**
 * 월/년 네비게이터 공용 칩 골격. (YearNavigator.kt에서도 사용)
 */
@Composable
internal fun NavigatorChip(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = previousEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "이전",
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (onTitleClick != null) {
                    TextButton(onClick = onTitleClick) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "다음",
                )
            }
        }
    }
}
