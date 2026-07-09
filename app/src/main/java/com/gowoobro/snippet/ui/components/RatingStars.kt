package com.gowoobro.snippet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.ui.theme.StatusWarning

/**
 * 별점 표시/입력 (5점 만점).
 *
 * - 표시 전용: [onRatingChange] = null. 0.5 단위 반별 렌더링.
 * - 입력 모드: [onRatingChange] 제공 시 별 탭으로 1~5 정수 선택.
 *
 * @param rating 현재 별점 (0.0 ~ 5.0)
 */
@Composable
fun RatingStars(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp,
    onRatingChange: ((Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (star in 1..5) {
            val icon = when {
                rating >= star -> Icons.Rounded.Star
                rating >= star - 0.5 -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Rounded.StarBorder
            }
            val tint = if (rating >= star - 0.5) {
                StatusWarning
            } else {
                MaterialTheme.colorScheme.outline
            }
            Icon(
                imageVector = icon,
                contentDescription = "${star}점",
                tint = tint,
                modifier = Modifier
                    .size(starSize)
                    .let { base ->
                        if (onRatingChange != null) {
                            base.clickable { onRatingChange(star) }
                        } else {
                            base
                        }
                    },
            )
        }
    }
}
