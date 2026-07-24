package com.gowoobro.snippet.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.SuggestionCategory
import com.gowoobro.snippet.core.model.SuggestionDto
import com.gowoobro.snippet.core.util.DateFormats
import java.time.format.DateTimeFormatter

/**
 * 기능 제안 목록 화면 — 내 건의 내역을 먼저 보여주고,
 * 상단 액션(+)으로 작성 화면(AddSuggestionScreen)으로 이동한다.
 * 작성 화면에서 복귀하면 재컴포즈되며 목록을 다시 로드한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionScreen(
    onBack: () -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
    onNavigateToDetail: (SuggestionDto) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(context.appContainer))
    val uiState by vm.suggestionUiState.collectAsStateWithLifecycle()

    // 화면 진입/복귀 시 내 건의 내역 로드 (작성 화면에서 돌아오면 재실행됨)
    LaunchedEffect(Unit) {
        vm.loadMySuggestions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기능 제안") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로가기",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAdd) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "제안 작성",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            // 첫 로딩 (아직 목록이 없을 때만 전체 로딩 표시)
            uiState.isListLoading && uiState.mySuggestions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // 빈 상태 — 안내 + 작성 유도
            uiState.mySuggestions.isEmpty() -> {
                SuggestionEmptyState(
                    onNavigateToAdd = onNavigateToAdd,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            // 목록
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.mySuggestions, key = { it.id }) { suggestion ->
                        SuggestionHistoryItem(
                            suggestion = suggestion,
                            onClick = { onNavigateToDetail(suggestion) },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/** 빈 목록 안내 — 전구 배지 + 문구 + 작성 유도 버튼 */
@Composable
private fun SuggestionEmptyState(
    onNavigateToAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "아직 남긴 제안이 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Snippet을 더 좋게 만들 아이디어를 알려주세요.\n버그 신고, 기능 제안 모두 환영해요!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToAdd,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("제안 작성하기")
            }
        }
    }
}

/** 내 건의 내역 항목 카드 — 카테고리/상태/제목/작성일 + (있다면) 관리자 답변 블록. 탭 시 상세로 이동. */
@Composable
private fun SuggestionHistoryItem(
    suggestion: SuggestionDto,
    onClick: () -> Unit = {},
) {
    val categoryLabel = suggestionCategoryLabel(suggestion.category)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SuggestionStatusBadge(status = suggestion.status)
            }

            Text(
                text = suggestion.title
                    ?.takeIf { it.isNotBlank() }
                    ?: suggestion.content.lineSequence().firstOrNull().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = formatSuggestionDate(suggestion.createDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 관리자 답변 블록
            suggestion.answer?.takeIf { it.isNotBlank() }?.let { answer ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "답변",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        suggestion.answerDate?.let { date ->
                            Text(
                                text = formatSuggestionDate(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** wire 카테고리 → 표시 라벨 (모르는 값은 그대로 노출) */
internal fun suggestionCategoryLabel(category: String): String =
    SuggestionCategory.entries.firstOrNull { it.wire == category }?.label ?: category

/** 상태 뱃지 — PENDING(대기중)/COMPLETED(답변완료), M3 tonal 스타일 */
@Composable
internal fun SuggestionStatusBadge(status: String) {
    val (label, container, content) = when (status) {
        "COMPLETED" -> Triple(
            "답변완료",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> Triple(
            "대기중",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** ISO LocalDateTime → "yyyy.MM.dd" (파싱 실패 시 앞 10자 폴백) */
internal fun formatSuggestionDate(iso: String): String =
    DateFormats.parseDateTime(iso)
        ?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        ?: iso.take(10).replace("-", ".")
