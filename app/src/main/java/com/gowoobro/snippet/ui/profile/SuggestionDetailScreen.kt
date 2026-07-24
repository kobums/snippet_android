package com.gowoobro.snippet.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gowoobro.snippet.core.model.SuggestionDto

/**
 * 건의 상세 화면 — 목록(SuggestionScreen) 항목 탭으로 진입.
 * 카테고리/상태, 제목, 작성일, 건의 내용 전문과 관리자 답변(또는 대기 안내)을 보여준다.
 * 데이터는 라우트 인자 대신 NavHolder(bookDetail과 동일 관례)로 전달받는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionDetailScreen(
    suggestion: SuggestionDto,
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("제안 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로가기",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 카테고리 + 상태 뱃지
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = suggestionCategoryLabel(suggestion.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SuggestionStatusBadge(status = suggestion.status)
            }

            // 제목 전체 (없으면 생략 — 카테고리 라벨이 이미 위에 있음)
            suggestion.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 작성일
            Text(
                text = formatSuggestionDate(suggestion.createDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 건의 내용 전문
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = suggestion.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // 관리자 답변 or 대기 안내
            val answer = suggestion.answer?.takeIf { it.isNotBlank() }
            if (answer != null) {
                SuggestionAnswerBlock(
                    answer = answer,
                    answerDate = suggestion.answerDate,
                )
            } else {
                SuggestionWaitingNotice()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** 관리자 답변 블록 — 라벨 + 답변일 + 전문 */
@Composable
private fun SuggestionAnswerBlock(
    answer: String,
    answerDate: String?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "답변",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                answerDate?.let { date ->
                    Text(
                        text = formatSuggestionDate(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 미답변 대기 안내 — 모래시계 아이콘 + 문구 */
@Composable
private fun SuggestionWaitingNotice() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "아직 답변을 기다리고 있어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
