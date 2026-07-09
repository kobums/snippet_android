package com.gowoobro.snippet.ui.snippet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.core.model.Snippet
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.theme.QuoteTextStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 스와이프 탭 — 카드 스택 + 좋아요/패스 버튼.
 *
 * 스와이프 물리:
 * - pointerInput + detectDragGestures로 X 오프셋 추적
 * - 화면폭 40% 초과 → animateTo로 화면 밖 날리기 (좋아요/패스 판정)
 * - 미달 → spring 복귀
 * - 드래그 비율에 비례해 LIKE/NOPE 오버레이 alpha 조정
 * - 판정 시 HapticFeedback(LongPress) 진동
 */
@Composable
fun SnippetSwipeScreen(
    state: SwipeUiState,
    onLike: (Snippet) -> Unit,
    onPass: (Snippet) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val thresholdDp = screenWidthDp * 0.40f

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading && state.cards.isEmpty() -> {
                CircularProgressIndicator()
            }

            state.cards.isEmpty() && state.remainingToday == 0 -> {
                EmptyState(
                    icon = Icons.Filled.Favorite,
                    title = "오늘의 스니펫을 모두 읽었어요",
                    description = "내일 다시 새로운 문장을 만나보세요\n하루 5문장 · 매일 자정 초기화",
                )
            }

            state.cards.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "더 이상 카드가 없습니다.",
                    description = "보관함이나 위젯을 확인해보세요!",
                )
            }

            else -> {
                val visibleCards = state.cards.take(3)

                Column(modifier = Modifier.fillMaxSize()) {
                    // 카드 스택 영역 — 남는 높이 전부 사용
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 뒤 카드들(i=2,1) 먼저 그리기 (z-order: 인덱스 큰 것 먼저)
                        for (idx in visibleCards.indices.reversed()) {
                            if (idx > 0) {
                                BackCard(stackIndex = idx)
                            }
                        }
                        // 최상단 카드 (idx=0) — 드래그 가능
                        TopSwipeCard(
                            snippet = visibleCards[0],
                            thresholdDp = thresholdDp,
                            onLike = { onLike(visibleCards[0]) },
                            onPass = { onPass(visibleCards[0]) },
                        )
                    }

                    // 하단 컨트롤 한 줄: ← Pass 힌트 · X · 하트 · Like → 힌트
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "← Pass",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalIconButton(
                            onClick = { onPass(visibleCards[0]) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "패스",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        FilledTonalIconButton(
                            onClick = { onLike(visibleCards[0]) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "좋아요",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Like →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 뒤쪽 카드 (인덱스 1~2) — scale + Y 오프셋으로 입체감 */
@Composable
private fun BackCard(stackIndex: Int) {
    val scale = 1f - stackIndex * 0.05f
    val yOffsetPx = stackIndex * 24f

    Surface(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = yOffsetPx
            },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

/** 최상단 카드 — 드래그 제스처 + LIKE/NOPE 오버레이 */
@Composable
private fun TopSwipeCard(
    snippet: Snippet,
    thresholdDp: Float,
    onLike: () -> Unit,
    onPass: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember(snippet.id) { Animatable(0f) }
    var dragRatio by remember(snippet.id) { mutableFloatStateOf(0f) }

    val rotation = (offsetX.value / 20f).coerceIn(-10f, 10f)

    Box(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = rotation
            }
            .pointerInput(snippet.id) {
                val thresholdPx = thresholdDp * density
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val currentOffset = offsetX.value
                            if (abs(currentOffset) > thresholdPx) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val target = if (currentOffset > 0)
                                    size.width.toFloat() * 1.5f
                                else
                                    -size.width.toFloat() * 1.5f
                                offsetX.animateTo(target, animationSpec = tween(200))
                                if (currentOffset > 0) onLike() else onPass()
                            } else {
                                offsetX.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                                dragRatio = 0f
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(
                                0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                            dragRatio = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            val thresholdPxLocal = thresholdDp * density
                            dragRatio = (offsetX.value / thresholdPxLocal).coerceIn(-1f, 1f)
                        }
                    },
                )
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 태그 pill
                if (!snippet.tag.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = snippet.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                // 인용 텍스트 — 카드의 남는 높이 전부 사용.
                // 짧은 문장은 세로 중앙 정렬(weight의 min 제약이 scroll을 통과해 Box가 영역을 채움),
                // 긴 문장은 카드 내부 세로 스크롤.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\"${snippet.text}\"",
                        style = QuoteTextStyle.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

                // 책 제목 (저자/표지 블라인드)
                if (!snippet.bookTitle.isNullOrBlank()) {
                    Text(
                        text = snippet.bookTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 하단 구분선 바 48×2
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
            }
        }

        // LIKE 오버레이
        if (dragRatio > 0.05f) {
            SwipeLabel(
                text = "LIKE",
                alpha = dragRatio,
                isLike = true,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // NOPE 오버레이
        if (dragRatio < -0.05f) {
            SwipeLabel(
                text = "NOPE",
                alpha = -dragRatio,
                isLike = false,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun SwipeLabel(
    text: String,
    alpha: Float,
    isLike: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .alpha(alpha.coerceIn(0f, 1f)),
        shape = RoundedCornerShape(8.dp),
        color = if (isLike) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isLike) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
