package com.gowoobro.snippet.ui.snippet

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.core.model.SnippetArchive
import com.gowoobro.snippet.ui.components.BookCover
import com.gowoobro.snippet.ui.components.BookCoverSize
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.share.ShareCardRenderer
import com.gowoobro.snippet.ui.share.ShareSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 보관함 탭 — 좋아요한 스니펫 목록.
 * 각 카드 탭 → AnimatedVisibility로 책 정보 패널 확장 (Reveal 인터랙션).
 */
@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    onRetry: () -> Unit,
    onRemove: (SnippetArchive) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()

            state.errorMessage != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) { Text("다시 시도") }
                }
            }

            state.items.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "아직 모은 문장이 없어요",
                    description = "마음에 드는 문장을 오른쪽으로 스와이프하면\n여기에 모을 수 있어요",
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items, key = { it.id }) { archive ->
                        DismissableArchiveCard(
                            archive = archive,
                            onRemove = { onRemove(archive) },
                        )
                    }
                }
            }
        }
    }
}

/** 스와이프(EndToStart) 삭제를 지원하는 ArchiveCard 래퍼 */
@Composable
private fun DismissableArchiveCard(
    archive: SnippetArchive,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ArchiveCard(archive = archive)
    }
}

/** ArchiveCard — 탭 시 책 정보 패널 확장 (Reveal) */
@Composable
private fun ArchiveCard(
    archive: SnippetArchive,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 태그 pill (옵션)
            if (!archive.tag.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = archive.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 스니펫 텍스트 (bodyLarge + 세리프 — 책 감성)
            Text(
                text = "\"${archive.text}\"",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 책제목 - 저자 (우측 정렬)
            if (archive.bookTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append(archive.bookTitle)
                        if (archive.bookAuthor.isNotBlank()) {
                            append(" - ")
                            append(archive.bookAuthor)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Reveal: 책 정보 패널 확장/축소 (AnimatedVisibility 300ms)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)),
                exit = shrinkVertically(tween(300)),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 책 표지 (60×90, 실패 시 플레이스홀더)
                        BookCover(
                            coverUrl = archive.coverUrl.ifBlank { null },
                            size = BookCoverSize.Large,
                            contentDescription = archive.bookTitle,
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (archive.bookTitle.isNotBlank()) {
                                Text(
                                    text = archive.bookTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (archive.bookAuthor.isNotBlank()) {
                                Text(
                                    text = archive.bookAuthor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // 제휴 링크 버튼
                            if (archive.affiliateUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(archive.affiliateUrl),
                                            )
                                            context.startActivity(intent)
                                        }
                                    },
                                ) {
                                    Text(
                                        text = "이 책 구매하기",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                            }

                            // 스니펫 공유 버튼
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (isSharing) return@OutlinedButton
                                    isSharing = true
                                    scope.launch {
                                        val uri = withContext(Dispatchers.IO) {
                                            ShareCardRenderer.renderSnippetCard(
                                                context = context,
                                                quoteText = archive.text,
                                                bookTitle = archive.bookTitle,
                                                bookAuthor = archive.bookAuthor,
                                            )
                                        }
                                        ShareSheet.shareImage(context, uri)
                                        isSharing = false
                                    }
                                },
                                enabled = !isSharing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isSharing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .padding(end = 8.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Text("이 문장 공유하기")
                            }
                        }
                    }
                }
            }
        }
    }
}
