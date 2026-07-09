package com.gowoobro.snippet.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.SectionHeader

/**
 * 전체 독서 세션 기록 조회 화면 — 책 제목별 그룹핑.
 * 세션 시작/타이머 기능은 이번 범위 제외. 조회 전용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsListScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: RecordsViewModel = viewModel(
        factory = RecordsViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var detailSession by remember { mutableStateOf<ReadingSessionDto?>(null) }
    var detailBookTitle by remember { mutableStateOf("") }

    detailSession?.let { session ->
        ModalBottomSheet(
            onDismissRequest = { detailSession = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            SessionDetailSheet(session = session, bookTitle = detailBookTitle)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("독서 세션") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isSessionsLoading,
            onRefresh = vm::loadSessions,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.sessions.isEmpty() && !state.isSessionsLoading) {
                EmptyState(
                    icon = Icons.Outlined.Timer,
                    title = "아직 독서 세션이 없습니다",
                    description = "독서 세션을 시작해보세요!",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            } else {
                val grouped = state.sessions.groupSessionsByBook()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SectionHeader(title = "독서 세션 (${state.sessions.size})")
                    }
                    grouped.forEach { (bookTitle, sessions) ->
                        item {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(sessions, key = { it.id }) { session ->
                            SessionDetailCard(
                                session = session,
                                onClick = {
                                    detailBookTitle = bookTitle
                                    detailSession = session
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailCard(
    session: ReadingSessionDto,
    onClick: () -> Unit = {},
) {
    val totalMinutes = session.durationSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText = when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
    val paceText = if (session.secondsPerPage > 0)
        String.format("%.1f min/p", session.secondsPerPage / 60.0)
    else "-"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = session.sessionDate.replace("-", "."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${session.startPage}p → ${session.endPage}p",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (session.pagesRead > 0) {
                    Text(
                        text = "+${session.pagesRead}p",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "페이스: $paceText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 세션 카드 탭 시 표시되는 상세 바텀시트 — 같은 데이터를 통계 타일로 강조.
 */
@Composable
private fun SessionDetailSheet(
    session: ReadingSessionDto,
    bookTitle: String,
) {
    val totalMinutes = session.durationSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    val paceText = if (session.secondsPerPage > 0)
        String.format("%.1f min/p", session.secondsPerPage / 60.0)
    else "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (bookTitle.isNotBlank()) {
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }

        // 소요 시간 강조
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = session.sessionDate.replace("-", "."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 통계 타일
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(value = "${session.pagesRead}p", label = "읽은 페이지", modifier = Modifier.weight(1f))
            StatTile(value = "${session.startPage}–${session.endPage}", label = "페이지 구간", modifier = Modifier.weight(1f))
            StatTile(value = paceText, label = "페이스", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.padding(vertical = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
