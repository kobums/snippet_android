package com.gowoobro.snippet.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.CategoryStatsDto
import com.gowoobro.snippet.core.model.MonthlyStatsDto
import com.gowoobro.snippet.core.model.ReadingInsightsDto
import com.gowoobro.snippet.core.model.YearlyStatsDto
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.FloatingCapsuleButton
import com.gowoobro.snippet.ui.components.FloatingSubTabBar
import com.gowoobro.snippet.ui.components.MinimalMonthlyBarChart
import com.gowoobro.snippet.ui.components.SectionHeader
import com.gowoobro.snippet.ui.theme.ChartColors
import java.time.LocalDate

/** 플로팅 바 아래로 콘텐츠가 시작하도록 하는 보정값 */
private val FloatingBarAreaHeight = 60.dp

/**
 * 통계 상세 풀스크린 — 기간별 | 카테고리 | 인사이트.
 *
 * 플로팅 한 줄: (← 뒤로) [기간별|카테고리|인사이트 compact 바] (년도 드롭다운).
 * 기간별 = 월별 차트/목록 + 연도별 요약을 한 스크롤로 통합.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailScreen(
    onBack: () -> Unit = {},
    // 엣지-투-엣지 시 하단 내비게이션 바에 콘텐츠가 가리지 않도록 하는 오버레이 패딩
    bottomOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var yearMenuExpanded by remember { mutableStateOf(false) }

    val now = LocalDate.now()

    // 섹션별 스크롤 상태 유지
    val periodListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val insightsListState = rememberLazyListState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contentTopPadding = statusBarTop + FloatingBarAreaHeight
    val contentBottomPadding = bottomOverlayPadding + 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            // 초기 로딩(중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
            isRefreshing = state.isLoading && state.monthlyStats.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.isLoading && state.monthlyStats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@PullToRefreshBox
            }

            Crossfade(targetState = selectedTab, label = "statsSection") { tab ->
                when (tab) {
                    0 -> PeriodStatsTab(
                        year = state.statsYear,
                        monthlyData = state.monthlyStats,
                        yearlyData = state.yearlyStats,
                        listState = periodListState,
                        contentTopPadding = contentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> CategoryStatsTab(
                        data = state.categoryStats,
                        listState = categoryListState,
                        contentTopPadding = contentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> InsightsTab(
                        insights = state.insights,
                        listState = insightsListState,
                        contentTopPadding = contentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // 플로팅 한 줄 — (← 뒤로) [서브탭 compact] (년도 드롭다운)
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
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            FloatingSubTabBar(
                tabs = listOf(0 to "기간별", 1 to "카테고리", 2 to "인사이트"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                compact = true,
            )
            Spacer(Modifier.weight(1f))
            Box {
                FloatingCapsuleButton(
                    text = "${state.statsYear}년",
                    onClick = { yearMenuExpanded = true },
                    compact = true,
                )
                DropdownMenu(
                    expanded = yearMenuExpanded,
                    onDismissRequest = { yearMenuExpanded = false },
                ) {
                    // 최근 10년
                    (now.year downTo now.year - 9).forEach { year ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${year}년",
                                    fontWeight = if (year == state.statsYear) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            },
                            onClick = {
                                yearMenuExpanded = false
                                vm.changeStatsYear(year)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 기간별 탭 — 월별 + 연도별 통합
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodStatsTab(
    year: Int,
    monthlyData: List<MonthlyStatsDto>,
    yearlyData: List<YearlyStatsDto>,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = contentTopPadding, bottom = contentBottomPadding),
    ) {
        // ① 월별 차트
        item {
            SectionHeader(
                title = "${year}년 월별 완독",
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
                    data = monthlyData,
                    chartHeight = 150.dp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // 월별 목록 — 완독 > 0인 달만
        val activeMonths = monthlyData
            .filter { it.completedCount > 0 }
            .sortedBy { it.month }
        if (activeMonths.isEmpty()) {
            item {
                Text(
                    text = "이 해의 완독 기록이 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(activeMonths.size) { idx ->
                val stat = activeMonths[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stat.month}월",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(44.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${stat.completedCount}권",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(48.dp),
                        )
                        Text(
                            text = "${stat.totalPages}쪽",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ② 연도별 섹션
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader(
                title = "연도별",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (yearlyData.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.AutoStories,
                    title = "데이터가 없습니다",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // 총 완독/총 페이지 요약 카드
            item {
                val totalCompleted = yearlyData.sumOf { it.completedCount }
                val totalPages = yearlyData.sumOf { it.totalPages }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(modifier = Modifier.padding(20.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "총 완독",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "${totalCompleted}권",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(64.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "총 페이지",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "${totalPages}쪽",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // 연도별 행
            items(yearlyData.size) { idx ->
                val stat = yearlyData[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stat.year}년",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(64.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${stat.completedCount}권",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = " / ${stat.totalPages}쪽",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 카테고리 탭
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryStatsTab(
    data: List<CategoryStatsDto>,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = contentTopPadding, bottom = contentBottomPadding),
    ) {
        if (data.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Category,
                    title = "데이터가 없습니다",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                val total = data.sumOf { it.totalCount }.coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        var startAngle = -90f
                        data.forEachIndexed { idx, cat ->
                            val sweep = 360f * cat.totalCount / total
                            drawArc(
                                color = ChartColors[idx % ChartColors.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 50.dp.toPx()),
                            )
                            startAngle += sweep
                        }
                        drawCircle(
                            color = surfaceContainer,
                            radius = (size.minDimension / 2) - 50.dp.toPx(),
                        )
                    }
                }
            }

            items(data.size) { idx ->
                val cat = data[idx]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ChartColors[idx % ChartColors.size]),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = cat.category.ifBlank { "기타" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "완독 ${cat.completedCount} / 전체 ${cat.totalCount}권",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { cat.completionRate.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ChartColors[idx % ChartColors.size],
                        )
                        Text(
                            text = "완독률: ${"%.1f".format(cat.completionRate * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 인사이트 탭
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsTab(
    insights: ReadingInsightsDto?,
    listState: LazyListState,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (insights == null) {
        Box(modifier = modifier.padding(top = contentTopPadding)) {
            EmptyState(
                icon = Icons.Outlined.EmojiEvents,
                title = "데이터가 없습니다",
                description = "독서 데이터가 쌓이면 분석 결과를 확인할 수 있어요",
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopPadding,
            bottom = contentBottomPadding,
        ),
    ) {
        item {
            InsightRow(
                icon = Icons.Outlined.Schedule,
                tint = ChartColors[0],
                title = "평균 독서 기간",
                value = "%.1f일".format(insights.averageReadingDays),
                description = "책 한 권을 완독하는 평균 시간",
            )
        }
        item {
            InsightRow(
                icon = Icons.Outlined.Category,
                tint = ChartColors[1 % ChartColors.size],
                title = "선호 카테고리",
                value = insights.topCategory.ifBlank { "-" },
                description = "가장 많이 읽은 장르",
            )
        }
        item {
            InsightRow(
                icon = Icons.Outlined.EmojiEvents,
                tint = ChartColors[2 % ChartColors.size],
                title = "최장 독서 기록",
                value = "${insights.longestReadingDays}일",
                description = "한 책을 읽은 최장 기간",
            )
        }
        item {
            InsightRow(
                icon = Icons.Outlined.MenuBook,
                tint = ChartColors[3 % ChartColors.size],
                title = "가장 오래 읽은 책",
                value = insights.longestBook.ifBlank { "-" },
                description = "완독까지 가장 오래 걸린 책",
            )
        }
    }
}

/** 인사이트 행 카드 — 아이콘 + 제목/값/설명 */
@Composable
private fun InsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
