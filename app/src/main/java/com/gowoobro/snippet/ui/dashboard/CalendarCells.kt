package com.gowoobro.snippet.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gowoobro.snippet.core.model.UserBookDto

/**
 * 규격화된 캘린더 날짜 셀 — 대시보드 미니 캘린더와 풀 캘린더 공용.
 *
 * - 모든 셀(빈 날짜 포함)은 고정 비율(aspectRatio 0.78). 표지 유무·크기에 절대 영향받지 않는다.
 * - 빈 날짜: 연한 타일(onSurface 5% 배경 + 0.5dp 테두리, 라운드 6) + 가운데 흐린 숫자. 오늘은 테두리 강조.
 * - 책 있는 날: 표지가 셀을 꽉 채우고(Crop) 클리핑. 좌상단 검은 날짜 배지(black 70%),
 *   2권 이상일 때만 우하단 "N권" 배지(primary 95% + onPrimary). 여러 권은 표지 스택(최대 4장).
 * - 날짜·N권 배지는 이미지가 아니라 셀에 앵커 — 모든 셀에서 같은 위치.
 *
 * @param day null이면 달 범위 밖의 빈 슬롯(자리만 유지)
 */
@Composable
fun CalendarDayCell(
    day: Int?,
    isToday: Boolean,
    books: List<UserBookDto>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .padding(1.5.dp),
    ) {
        if (day == null) return@Box

        if (books.isEmpty()) {
            // 빈 날짜 타일
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(
                        width = if (isToday) 1.dp else 0.5.dp,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        },
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$day",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                )
            }
        } else {
            // 표지 영역 (셀 클리핑)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(
                        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                    ),
            ) {
                CoverStack(books = books)
            }

            // 날짜 배지 — 셀 좌상단 앵커
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "$day",
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            // N권 배지 — 2권 이상일 때만, 셀 우하단 앵커
            if (books.size >= 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${books.size}권",
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 표지 스택 — 최대 4장, 레이어마다 10% 오프셋 + 전체 스케일 축소. */
@Composable
private fun CoverStack(books: List<UserBookDto>) {
    val show = books.take(4)
    val scale = (1f - (show.size - 1) * 0.1f).coerceIn(0.7f, 1f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layerW = maxWidth * scale
        val layerH = maxHeight * scale
        val offsetX = maxWidth * 0.10f
        val offsetY = maxHeight * 0.10f

        // 뒤쪽(index 큰)부터 그려 첫 번째 책이 맨 위로
        for (i in show.indices.reversed()) {
            CoverLayer(
                coverUrl = show[i].coverUrl,
                modifier = Modifier
                    .offset(x = offsetX * i, y = offsetY * i)
                    .size(width = layerW, height = layerH)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

@Composable
private fun CoverLayer(coverUrl: String, modifier: Modifier = Modifier) {
    if (coverUrl.isNotBlank()) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            ),
        )
    }
}
