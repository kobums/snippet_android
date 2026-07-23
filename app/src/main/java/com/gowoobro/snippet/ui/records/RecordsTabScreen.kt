package com.gowoobro.snippet.ui.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.getOrNull
import com.gowoobro.snippet.core.network.safeApiCall
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.FloatingSubTabBar
import com.gowoobro.snippet.ui.components.SectionHeader
import com.gowoobro.snippet.ui.components.YearMonthPickerSheet
import kotlinx.coroutines.launch

/** 플로팅 바 아래로 콘텐츠가 시작하도록 하는 보정값 */
private val FloatingBarAreaHeight = 60.dp

/**
 * 독서기록 탭 루트 화면 — 스니펫 | 일기 | 리뷰 | 세션.
 *
 * 플로팅 한 줄: [4탭 FloatingSubTabBar] (캘린더 원형) (+ 원형).
 * 세션 탭에서는 캘린더·+ 버튼이 fade+scale로 사라진다(바는 좌측 고정이라 점프 없음).
 * 콘텐츠는 엣지-투-엣지, Crossfade 전환.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsTabScreen(
    onNavigateToAddRecord: (RecordType) -> Unit = {},
    onNavigateToEditRecord: (RecordDto) -> Unit = {},
    onNavigateToBookDetail: (UserBookDto) -> Unit = {},
    // 기록 추가 화면에서 돌아왔을 때 목록 새로고침 트리거 (값이 바뀌면 재조회)
    refreshSignal: Int = 0,
    // 엣지-투-엣지 시 하단 내비게이션 바에 콘텐츠가 가리지 않도록 하는 오버레이 패딩
    bottomOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val vm: RecordsViewModel = viewModel(
        factory = RecordsViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 책 그룹 헤더 탭 → 서재에서 책을 찾아 상세로 이동
    val openBookDetailByBookId: (Long) -> Unit = { bookId ->
        scope.launch {
            safeApiCall { context.appContainer.userBookApi.getAll() }
                .getOrDefault(emptyList())
                .firstOrNull { it.bookId == bookId }
                ?.let(onNavigateToBookDetail)
        }
    }
    val openBookDetailByUserBookId: (Long) -> Unit = { userBookId ->
        scope.launch {
            safeApiCall { context.appContainer.userBookApi.getById(userBookId) }
                .getOrNull()
                ?.let(onNavigateToBookDetail)
        }
    }

    // 탭: 0=스니펫, 1=일기, 2=리뷰, 3=세션
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // 탭별 스크롤 상태 유지 (Crossfade dispose 대응)
    val listStates = remember { List(4) { LazyListState() } }

    // 탭 → RecordType 매핑 (null = 세션)
    val tabToType: RecordType? = when (selectedTab) {
        0 -> RecordType.SNIPPET
        1 -> RecordType.DIARY
        2 -> RecordType.REVIEW
        else -> null
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentTopPadding = statusBarTop + FloatingBarAreaHeight
    val contentBottomPadding = bottomOverlayPadding + 24.dp

    // 탭 변경 또는 기록 추가 후(refreshSignal 변경) 해당 타입으로 재조회
    LaunchedEffect(selectedTab, refreshSignal) {
        if (selectedTab < 3) {
            vm.loadMonthlyRecords(
                state.selectedYear,
                state.selectedMonth,
                tabToType,
            )
        } else {
            vm.loadSessions()
        }
    }

    // 에러 스낵바
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
            vm.consumeError()
        }
    }

    if (showMonthPicker) {
        YearMonthPickerSheet(
            initialYear = state.selectedYear,
            initialMonth = state.selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { year, month ->
                vm.selectMonth(year, month, tabToType)
                showMonthPicker = false
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 콘텐츠 — 엣지-투-엣지 (플로팅 바 뒤까지)
        Crossfade(targetState = selectedTab, label = "recordsSection") { tab ->
            if (tab < 3) {
                val type = when (tab) {
                    0 -> RecordType.SNIPPET
                    1 -> RecordType.DIARY
                    else -> RecordType.REVIEW
                }
                RecordListContent(
                    state = state,
                    listState = listStates[tab],
                    contentTopPadding = contentTopPadding,
                    contentBottomPadding = contentBottomPadding,
                    onRefresh = {
                        vm.loadMonthlyRecords(
                            state.selectedYear,
                            state.selectedMonth,
                            type,
                        )
                    },
                    onEditRecord = onNavigateToEditRecord,
                    onBookHeaderClick = openBookDetailByBookId,
                    onDeleteRecord = { record ->
                        vm.deleteRecord(
                            id = record.id,
                            onSuccess = {
                                scope.launch { snackbarHostState.showSnackbar("기록이 삭제되었습니다") }
                            },
                            onError = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                        )
                    },
                )
            } else {
                SessionsListContent(
                    state = state,
                    listState = listStates[3],
                    contentTopPadding = contentTopPadding,
                    contentBottomPadding = contentBottomPadding,
                    onRefresh = vm::loadSessions,
                    onBookHeaderClick = openBookDetailByUserBookId,
                )
            }
        }

        // 플로팅 한 줄 — [서브탭 바] (캘린더 원형) (+ 원형)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingSubTabBar(
                tabs = listOf(0 to "스니펫", 1 to "일기", 2 to "리뷰", 3 to "세션"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
            Spacer(Modifier.weight(1f))

            // 세션 탭에서는 캘린더·+ 버튼이 fade+scale로 사라진다
            AnimatedVisibility(
                visible = selectedTab < 3,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.6f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.6f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { showMonthPicker = true }) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = "년/월 선택 (${state.selectedYear}년 ${state.selectedMonth}월)",
                        )
                    }
                    // 로딩 중에도 사라지지 않고 비활성화만
                    FilledTonalIconButton(
                        onClick = { onNavigateToAddRecord(tabToType ?: RecordType.SNIPPET) },
                        enabled = !state.isLoading && !state.isSaving,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "기록 추가")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomOverlayPadding),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 기록 목록 (스니펫/일기/리뷰 탭 공통)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordListContent(
    state: RecordsUiState,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    onRefresh: () -> Unit,
    onEditRecord: (RecordDto) -> Unit,
    onBookHeaderClick: (Long) -> Unit,
    onDeleteRecord: (RecordDto) -> Unit,
) {
    PullToRefreshBox(
        // 초기 로딩(중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
        isRefreshing = state.isLoading && state.records.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentTopPadding,
                bottom = contentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isLoading && state.records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.records.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.EditNote,
                        title = "아직 기록이 없습니다",
                        description = "첫 기록을 추가해보세요!",
                    )
                }
            } else {
                // 섹션 헤더
                item {
                    SectionHeader(title = "기록 (${state.records.size})")
                }

                // 책 제목별 그룹핑
                val grouped = state.records.groupByBook()
                grouped.forEach { (bookTitle, bookRecords) ->
                    item {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            // 헤더 탭 → 책 상세 (bookId로 서재에서 조회)
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookHeaderClick(bookRecords.first().bookId) }
                                .padding(vertical = 4.dp),
                        )
                    }
                    items(bookRecords, key = { it.id }) { record ->
                        SwipeableRecordCard(
                            record = record,
                            onTap = { onEditRecord(record) },
                            onDelete = { onDeleteRecord(record) },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RecordCard with swipe-to-delete
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRecordCard(
    record: RecordDto,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
            }
            false // 항상 false로 되돌림 (다이얼로그 확인 후 처리)
        },
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("기록 삭제") },
            text = { Text("이 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        RecordCard(record = record, onClick = onTap)
    }
}

@Composable
private fun RecordCard(record: RecordDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 상단 행: 태그(좌) / 페이지 + 날짜(우)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!record.tag.isNullOrBlank()) {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text("#${record.tag}") },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (record.relatedPage != null) {
                        Text(
                            text = "p.${record.relatedPage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = record.createDate.take(10).replace("-", "."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 본문 (최대 5줄)
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 세션 탭 콘텐츠
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsListContent(
    state: RecordsUiState,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    onRefresh: () -> Unit,
    onBookHeaderClick: (Long) -> Unit,
) {
    PullToRefreshBox(
        // 초기 로딩(중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
        isRefreshing = state.isSessionsLoading && state.sessions.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.isSessionsLoading && state.sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = contentTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.sessions.isEmpty() && !state.isSessionsLoading) {
            EmptyState(
                icon = Icons.Outlined.EditNote,
                title = "아직 독서 세션이 없습니다",
                description = "독서 세션을 시작해보세요!",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding)
                    .padding(16.dp),
            )
        } else {
            val grouped = state.sessions.groupSessionsByBook()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentTopPadding,
                    bottom = contentBottomPadding,
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
                            // 헤더 탭 → 책 상세 (userBookId 단건 조회)
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookHeaderClick(sessions.first().userBookId) }
                                .padding(vertical = 4.dp),
                        )
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: com.gowoobro.snippet.core.model.ReadingSessionDto) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                verticalAlignment = Alignment.CenterVertically,
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
