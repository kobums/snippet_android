package com.gowoobro.snippet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

/**
 * 책 표지 썸네일 크기 프리셋 (Flutter 원본 치수 유지 — 03-design-system.md §3.5).
 */
enum class BookCoverSize(val width: Dp, val height: Dp, val cornerRadius: Dp) {
    Small(45.dp, 68.dp, 6.dp),
    Medium(50.dp, 75.dp, 8.dp),
    Large(60.dp, 90.dp, 8.dp),
    Header(72.dp, 100.dp, 8.dp),
}

/**
 * 네트워크 책 표지 이미지.
 * - Coil [SubcomposeAsyncImage] 사용, 로딩/실패/URL 없음 시 책 아이콘 플레이스홀더.
 * - 라운딩 + 약한 그림자 엘리베이션 (카드 형태 언어 유지).
 *
 * @param coverUrl 표지 이미지 URL. null/blank면 플레이스홀더만 표시.
 */
@Composable
fun BookCover(
    coverUrl: String?,
    modifier: Modifier = Modifier,
    size: BookCoverSize = BookCoverSize.Medium,
    contentDescription: String? = null,
) {
    Surface(
        modifier = modifier.size(width = size.width, height = size.height),
        shape = RoundedCornerShape(size.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp,
    ) {
        if (coverUrl.isNullOrBlank()) {
            BookCoverPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { BookCoverPlaceholder() },
                error = { BookCoverPlaceholder() },
            )
        }
    }
}

@Composable
private fun BookCoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
