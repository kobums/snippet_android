package com.gowoobro.snippet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 연도 네비게이터 — `◀ 2026년 ▶` 칩. (연간 통계 등)
 * 골격은 [NavigatorChip] 공유.
 */
@Composable
fun YearNavigator(
    year: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
) {
    NavigatorChip(
        label = "${year}년",
        onPrevious = onPrevious,
        onNext = onNext,
        modifier = modifier,
        previousEnabled = previousEnabled,
        nextEnabled = nextEnabled,
        onTitleClick = onTitleClick,
    )
}
