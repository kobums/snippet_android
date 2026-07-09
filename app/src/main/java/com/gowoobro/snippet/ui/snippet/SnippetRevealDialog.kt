package com.gowoobro.snippet.ui.snippet

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gowoobro.snippet.core.model.SnippetArchive
import com.gowoobro.snippet.ui.components.BookCover
import com.gowoobro.snippet.ui.components.BookCoverSize

/**
 * 좋아요 후 책 공개 Reveal 다이얼로그.
 * 01-screens.md §2.2: 책 표지+제목+저자 페이드인, 제휴 링크 버튼.
 */
@Composable
fun SnippetRevealDialog(
    archive: SnippetArchive,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showBookInfo by remember { mutableStateOf(false) }

    // 진입 직후 애니메이션 트리거
    LaunchedEffect(Unit) {
        showBookInfo = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // "보관함에 추가되었어요" 타이틀
                Text(
                    text = "보관함에 추가되었어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                // 인용 텍스트 (간략)
                val previewText = if (archive.text.length > 80) {
                    "\"${archive.text.take(80)}...\""
                } else {
                    "\"${archive.text}\""
                }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 책 정보 페이드인 (Reveal 연출)
                AnimatedVisibility(
                    visible = showBookInfo,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        tween(400),
                        initialOffsetY = { it / 4 },
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 책 표지
                        BookCover(
                            coverUrl = archive.coverUrl.ifBlank { null },
                            size = BookCoverSize.Large,
                            contentDescription = archive.bookTitle,
                        )

                        // 제목 + 저자
                        if (archive.bookTitle.isNotBlank()) {
                            Text(
                                text = archive.bookTitle,
                                // 공개 연출 책 제목은 세리프 — 책 감성
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (archive.bookAuthor.isNotBlank()) {
                            Text(
                                text = archive.bookAuthor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        // 제휴 링크 버튼
                        if (archive.affiliateUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(archive.affiliateUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("이 책 구매하기")
                            }
                        }
                    }
                }

                // 닫기 버튼
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("닫기")
                }
            }
        }
    }
}
