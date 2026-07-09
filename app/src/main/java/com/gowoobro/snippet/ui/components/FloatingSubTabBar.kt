package com.gowoobro.snippet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.ui.theme.SnippetTheme

/**
 * 공용 플로팅 서브탭 바 — One UI 8.5 감성의 캡슐(완전 라운드) 컨트롤.
 *
 * 하단 NavigationBar와 어울리는 반투명 tonal surface 캡슐 안에
 * 탭 버튼들을 배치하고, 선택 항목 뒤에 은은한 하이라이트 필(onSurface 10%)을
 * 컬러 crossfade 애니메이션으로 표시한다.
 *
 * - 선택 글자색: primary (다크 모노크롬 정책에서 = 흰색)
 * - 미선택 글자색: onSurfaceVariant
 *
 * @param tabs (값, 라벨) 쌍 목록
 * @param selected 현재 선택된 값
 * @param onSelect 탭 선택 콜백
 * @param compact true면 항목 패딩/폰트를 줄인 컴팩트 사이즈
 */
@Composable
fun <T> FloatingSubTabBar(
    tabs: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val itemHorizontal = if (compact) 8.dp else 12.dp
    val itemVertical = if (compact) 8.dp else 10.dp
    val textStyle = if (compact) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            tabs.forEach { (value, label) ->
                val isSelected = value == selected

                // 선택 하이라이트 필 — 은은한 crossfade 슬라이드 느낌
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 250),
                    label = "subTabPill",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 250),
                    label = "subTabLabel",
                )

                Text(
                    text = label,
                    style = textStyle,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = labelColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) }
                        .padding(horizontal = itemHorizontal, vertical = itemVertical),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingSubTabBarPreview() {
    SnippetTheme {
        var selected by remember { mutableStateOf(0) }
        FloatingSubTabBar(
            tabs = listOf(0 to "스와이프", 1 to "보관함"),
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingSubTabBarCompactPreview() {
    SnippetTheme {
        var selected by remember { mutableStateOf("stats") }
        FloatingSubTabBar(
            tabs = listOf("stats" to "통계", "progress" to "진행", "shelf" to "서재"),
            selected = selected,
            onSelect = { selected = it },
            compact = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
