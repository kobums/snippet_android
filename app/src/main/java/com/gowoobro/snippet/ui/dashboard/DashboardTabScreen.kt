package com.gowoobro.snippet.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Pages
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.CategoryStatsDto
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.FloatingCapsuleButton
import com.gowoobro.snippet.ui.components.FloatingSubTabBar
import com.gowoobro.snippet.ui.components.MinimalMonthlyBarChart
import com.gowoobro.snippet.ui.components.SectionHeader
import com.gowoobro.snippet.ui.components.StatCard
import com.gowoobro.snippet.ui.components.YearMonthPickerSheet
import com.gowoobro.snippet.ui.theme.ChartColors
import com.gowoobro.snippet.ui.theme.StatusCaution
import com.gowoobro.snippet.ui.theme.StatusSuccess
import java.time.LocalDate

/** 플로팅 바(캡슐 높이 + 상단 4dp + 아래 여백) 아래로 콘텐츠가 시작하도록 하는 보정값 */
private val FloatingBarAreaHeight = 60.dp

/**
 * 대시보드 탭 루트 화면 — 통계 | 진행 | 서재.
 *
 * 상단 헤더/세그먼트 없이 플로팅 한 줄:
 * [통계|진행|서재 FloatingSubTabBar] (년월 캡슐) (통계 상세 원형 버튼).
 * 콘텐츠는 엣지-투-엣지, Crossfade로 섹션 전환(상태 유지).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTabScreen(
    onNavigateToCalendar: (year: Int, month: Int) -> Unit = { _, _ -> },
    onNavigateToStats: () -> Unit = {},
    onNavigateToBookSearch: (query: String) -> Unit = {},
    onNavigateToBookDetail: (UserBookDto) -> Unit = {},
    // 엣지-투-엣지 시 하단 내비게이션 바에 콘텐츠가 가리지 않도록 하는 오버레이 패딩
    bottomOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // 섹션별 상태 호이스팅 — Crossfade가 비활성 섹션을 dispose해도 유지
    val statsListState = rememberLazyListState()
    val progressListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    var progressSegment by rememberSaveable { mutableIntStateOf(1) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentTopPadding = statusBarTop + FloatingBarAreaHeight
    val contentBottomPadding = bottomOverlayPadding + 24.dp

    if (showMonthPicker) {
        YearMonthPickerSheet(
            initialYear = state.selectedYear,
            initialMonth = state.selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { year, month ->
                vm.changeMonth(year, month)
                showMonthPicker = false
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 섹션 콘텐츠 — 엣지-투-엣지 (플로팅 바 뒤까지)
        Crossfade(targetState = selectedTab, label = "dashboardSection") { tab ->
            when (tab) {
                0 -> DashboardStatsTab(
                    state = state,
                    listState = statsListState,
                    contentTopPadding = contentTopPadding,
                    contentBottomPadding = contentBottomPadding,
                    onUpdateGoal = vm::updateGoal,
                    onRefresh = vm::refresh,
                    onNavigateToCalendar = { onNavigateToCalendar(state.selectedYear, state.selectedMonth) },
                    onNavigateToStats = onNavigateToStats,
                    onRecommendationClick = { book -> onNavigateToBookSearch(book.title) },
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> DashboardProgressTab(
                    state = state,
                    listState = progressListState,
                    contentTopPadding = contentTopPadding,
                    contentBottomPadding = contentBottomPadding,
                    selectedSegment = progressSegment,
                    onSegmentChange = { progressSegment = it },
                    onRefresh = vm::refresh,
                    onBookClick = onNavigateToBookDetail,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> DashboardLibraryTab(
                    state = state,
                    listState = libraryListState,
                    contentTopPadding = contentTopPadding,
                    contentBottomPadding = contentBottomPadding,
                    onQueryChange = vm::setLibraryQuery,
                    onRefresh = vm::refresh,
                    onBookClick = onNavigateToBookDetail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 플로팅 한 줄 — [서브탭 바] (년월 캡슐) (통계 상세 원형)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingSubTabBar(
                tabs = listOf(0 to "통계", 1 to "진행", 2 to "서재"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
            Spacer(Modifier.weight(1f))
            FloatingCapsuleButton(
                text = "${state.selectedYear}년 ${state.selectedMonth}월",
                onClick = { showMonthPicker = true },
            )
            FilledTonalIconButton(onClick = onNavigateToStats) {
                Icon(Icons.Outlined.BarChart, contentDescription = "통계 상세")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 통계 섹션
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardStatsTab(
    state: DashboardUiState,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    onUpdateGoal: (Int) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToStats: () -> Unit,
    onRecommendationClick: (com.gowoobro.snippet.core.model.BookRecommendDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showGoalDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        GoalEditDialog(
            current = state.readingGoal.targetBooks,
            onDismiss = { showGoalDialog = false },
            onSave = { target ->
                onUpdateGoal(target)
                showGoalDialog = false
            },
        )
    }

    PullToRefreshBox(
        // 초기 로딩(중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
        isRefreshing = state.isLoading && state.monthlyBooks.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (state.isLoading && state.monthlyBooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PullToRefreshBox
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = contentTopPadding, bottom = contentBottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 독서 목표 카드
            item {
                ReadingGoalCard(
                    goal = state.readingGoal,
                    onEditClick = { showGoalDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 이번 달 통계 카드 2열
            item {
                val completedCount = state.monthlyBooks.count { it.status == BookStatus.COMPLETED }
                val totalPages = state.monthlyBooks
                    .filter { it.status == BookStatus.COMPLETED }
                    .sumOf { it.totalPage }

                SectionHeader(
                    title = "${state.selectedMonth}월 통계",
                    actionLabel = "상세 보기",
                    onActionClick = onNavigateToStats,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        icon = Icons.Outlined.AutoStories,
                        value = "${completedCount}권",
                        label = "완독한 책",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Outlined.Pages,
                        value = "${totalPages}쪽",
                        label = "총 페이지",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 스트릭 카드
            item {
                StreakCard(
                    currentStreak = state.streak.currentStreak,
                    maxStreak = state.streak.maxStreak,
                    lastReadDate = state.streak.lastReadDate,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 추천 도서
            if (state.recommendedBooks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "추천 도서",
                        actionLabel = "새로고침",
                        onActionClick = onRefresh,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    RecommendationRow(
                        books = state.recommendedBooks,
                        onBookClick = onRecommendationClick,
                    )
                }
            }

            // 월별 완독 차트 — 미니멀 (그리드/축선 없음, 값 라벨 직접 표기)
            item {
                SectionHeader(
                    title = "월별 완독",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    MinimalMonthlyBarChart(
                        data = state.monthlyStats,
                        chartHeight = 140.dp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // 카테고리 도넛 차트 (Canvas)
            if (state.categoryStats.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "카테고리 분포",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    CategoryDonutChart(
                        data = state.categoryStats,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            // 독서 캘린더 카드
            item {
                SectionHeader(
                    title = "독서 캘린더",
                    actionLabel = "전체 보기",
                    onActionClick = onNavigateToCalendar,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                MiniCalendarCard(
                    year = state.selectedYear,
                    month = state.selectedMonth,
                    completedBooks = state.monthlyBooks.filter { it.status == BookStatus.COMPLETED },
                    onCalendarClick = onNavigateToCalendar,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 최근 세션
            if (state.recentSessions.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "최근 독서 세션",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(state.recentSessions.size) { idx ->
                    val session = state.recentSessions[idx]
                    RecentSessionRow(
                        session = session,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 진행 섹션
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardProgressTab(
    state: DashboardUiState,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    selectedSegment: Int,
    onSegmentChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBookClick: (UserBookDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = listOf("대기중", "읽는중", "완독")

    val statusFilter = when (selectedSegment) {
        0 -> BookStatus.WAITING
        1 -> BookStatus.READING
        else -> BookStatus.COMPLETED
    }
    val filtered = state.progressBooks.filter { it.status == statusFilter }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = contentTopPadding, bottom = contentBottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    segments.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedSegment == index,
                            onClick = { onSegmentChange(index) },
                            shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                        ) { Text(label) }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "책이 없습니다",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(filtered.size) { idx ->
                    ProgressBookRow(
                        book = filtered[idx],
                        onClick = { onBookClick(filtered[idx]) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 서재 섹션
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardLibraryTab(
    state: DashboardUiState,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBookClick: (UserBookDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = state.filteredLibraryBooks

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = contentTopPadding, bottom = contentBottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("제목이나 저자로 검색...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Book,
                        title = if (state.libraryQuery.isNotBlank()) "검색 결과가 없습니다" else "아직 책이 없습니다",
                        description = if (state.libraryQuery.isBlank()) "첫 책을 추가해보세요!" else "다른 검색어를 시도해보세요",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(filtered.size) { idx ->
                    LibraryBookRow(
                        book = filtered[idx],
                        onClick = { onBookClick(filtered[idx]) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 서브 컴포넌트
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadingGoalCard(
    goal: com.gowoobro.snippet.core.model.ReadingGoalDto,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "올해 독서 목표",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditClick) {
                    Text(
                        text = if (goal.targetBooks <= 0) "목표 설정" else "수정",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (goal.targetBooks <= 0) {
                Text(
                    text = "독서 목표를 설정하면 진행상황을 확인할 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${goal.completedBooks}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = " / ${goal.targetBooks}권",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(goal.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (goal.progress >= 1f) StatusSuccess else MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { goal.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (goal.progress >= 1f) StatusSuccess else MaterialTheme.colorScheme.primary,
                )
                if (goal.progress >= 1f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "목표 달성! 축하합니다!",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusSuccess,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalEditDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("독서 목표 설정") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("목표 권수") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = text.toIntOrNull() ?: return@TextButton
                    if (v > 0) onSave(v)
                },
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun StreakCard(
    currentStreak: Int,
    maxStreak: Int,
    lastReadDate: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                // 스트릭 활성 시 주황 불꽃 (이모지 🔥 대체)
                tint = if (currentStreak > 0) StatusCaution else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "독서 스트릭",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "현재 ${currentStreak}일  최장 ${maxStreak}일",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!lastReadDate.isNullOrBlank()) {
                    Text(
                        text = "마지막 독서: $lastReadDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 추천 도서 가로 스크롤 — 표지 + 제목 + 저자. 탭하면 검색 화면으로. */
@Composable
private fun RecommendationRow(
    books: List<com.gowoobro.snippet.core.model.BookRecommendDto>,
    onBookClick: (com.gowoobro.snippet.core.model.BookRecommendDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books.size) { idx ->
            val book = books[idx]
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .clickable { onBookClick(book) },
            ) {
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 도넛 차트 — Canvas로 직접 그림 (외부 라이브러리 없음) */
@Composable
private fun CategoryDonutChart(
    data: List<CategoryStatsDto>,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val total = data.sumOf { it.totalCount }.coerceAtLeast(1)
            Canvas(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp),
            ) {
                var startAngle = -90f
                data.forEachIndexed { idx, cat ->
                    val sweep = 360f * cat.totalCount / total
                    drawArc(
                        color = ChartColors[idx % ChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 50.dp.toPx()),
                    )
                    startAngle += sweep
                }
                // 중앙 흰 원(도넛)
                drawCircle(
                    color = surfaceContainer,
                    radius = (size.minDimension / 2) - 50.dp.toPx(),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                data.take(6).forEachIndexed { idx, cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ChartColors[idx % ChartColors.size]),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cat.category.ifBlank { "기타" },
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${cat.totalCount}권",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCalendarCard(
    year: Int,
    month: Int,
    completedBooks: List<UserBookDto>,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCalendarClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        MiniCalendarGrid(
            year = year,
            month = month,
            completedBooks = completedBooks,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun MiniCalendarGrid(
    year: Int,
    month: Int,
    completedBooks: List<UserBookDto>,
    modifier: Modifier = Modifier,
) {
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    // 일요일=0 기준 시작 오프셋
    val startOffset = (firstDay.dayOfWeek.value % 7)
    val dayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")

    val completedDateMap = completedBooks.groupBy { it.endDate.take(10) }

    Column(modifier = modifier) {
        // 요일 헤더
        Row(Modifier.fillMaxWidth()) {
            dayHeaders.forEachIndexed { idx, day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (idx) {
                        0 -> MaterialTheme.colorScheme.error
                        6 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val cells = startOffset + daysInMonth
        val rows = (cells + 6) / 7
        val today = LocalDate.now()

        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIdx = row * 7 + col
                    val day = cellIdx - startOffset + 1
                    val isValid = day in 1..daysInMonth
                    val dateStr = if (isValid) "%04d-%02d-%02d".format(year, month, day) else ""
                    val booksOnDay = if (isValid) completedDateMap[dateStr] ?: emptyList() else emptyList()
                    val isToday = isValid && year == today.year && month == today.monthValue && day == today.dayOfMonth

                    CalendarDayCell(
                        day = if (isValid) day else null,
                        isToday = isToday,
                        books = booksOnDay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(
    session: com.gowoobro.snippet.core.model.ReadingSessionDto,
    modifier: Modifier = Modifier,
) {
    val hours = session.durationSeconds / 3600
    val minutes = (session.durationSeconds % 3600) / 60
    val durationText = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.bookTitle.ifBlank { "제목 없음" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${session.startPage}p → ${session.endPage}p · +${session.pagesRead}p",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressBookRow(
    book: UserBookDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 40.dp, height = 56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.status == BookStatus.READING && book.totalPage > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryBookRow(
    book: UserBookDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 36.dp, height = 50.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val statusLabel = when (book.status) {
                BookStatus.WAITING -> "대기중"
                BookStatus.READING -> "읽는중"
                BookStatus.COMPLETED -> "완독"
                BookStatus.DROPPED -> "중단"
                BookStatus.NONE -> ""
            }
            if (statusLabel.isNotBlank()) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (book.status) {
                        BookStatus.COMPLETED -> StatusSuccess
                        BookStatus.READING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .background(
                            color = when (book.status) {
                                BookStatus.COMPLETED -> StatusSuccess.copy(alpha = 0.1f)
                                BookStatus.READING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
