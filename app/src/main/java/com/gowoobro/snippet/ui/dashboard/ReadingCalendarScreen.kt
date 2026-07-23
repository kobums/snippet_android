package com.gowoobro.snippet.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.MonthNavigator
import com.gowoobro.snippet.ui.share.CalendarShareRenderer
import com.gowoobro.snippet.ui.share.ShareSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 독서 캘린더 풀스크린 (읽기 전용 + 4:5 이미지 공유).
 * DashboardTabScreen의 "전체 보기" 버튼으로 진입.
 * 상단 공유 버튼: 표지 다운로드 → 캘린더 Bitmap 렌더 → FileProvider 저장 → 공유 시트.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingCalendarScreen(
    initialYear: Int = LocalDate.now().year,
    initialMonth: Int = LocalDate.now().monthValue,
    onBack: () -> Unit = {},
    onNavigateToBookDetail: (UserBookDto) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    var isSharing by remember { mutableStateOf(false) }

    // 초기 진입 시 지정된 년/월로 설정
    LaunchedEffect(Unit) {
        if (state.selectedYear != initialYear || state.selectedMonth != initialMonth) {
            vm.changeMonth(initialYear, initialMonth)
        }
    }

    val now = LocalDate.now()
    val isNextDisabled = state.selectedYear == now.year && state.selectedMonth == now.monthValue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("독서 캘린더") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = {
                                isSharing = true
                                val year = state.selectedYear
                                val month = state.selectedMonth
                                val completed = state.monthlyBooks
                                    .filter { it.status == BookStatus.COMPLETED }
                                scope.launch {
                                    try {
                                        val uri = withContext(Dispatchers.IO) {
                                            CalendarShareRenderer.renderToUri(
                                                context = context,
                                                year = year,
                                                month = month,
                                                completedBooks = completed,
                                                isDark = isDark,
                                            )
                                        }
                                        ShareSheet.shareImage(
                                            context = context,
                                            imageUri = uri,
                                            shareText = "${year}년 ${month}월 독서 캘린더",
                                        )
                                    } catch (_: Exception) {
                                        // 공유 실패는 무시 (사용자 취소/네트워크 등)
                                    } finally {
                                        isSharing = false
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.IosShare, contentDescription = "공유")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            // 초기 로딩(중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
            isRefreshing = state.isLoading && state.monthlyBooks.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading && state.monthlyBooks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    MonthNavigator(
                        year = state.selectedYear,
                        month = state.selectedMonth,
                        onPrevious = vm::prevMonth,
                        onNext = vm::nextMonth,
                        nextEnabled = !isNextDisabled,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                    )
                }

                item {
                    val completedBooks = state.monthlyBooks.filter { it.status == BookStatus.COMPLETED }
                    CalendarGrid(
                        year = state.selectedYear,
                        month = state.selectedMonth,
                        completedBooks = completedBooks,
                        onBookClick = onNavigateToBookDetail,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // 이번 달 완독 책 목록
                val completedBooks = state.monthlyBooks.filter { it.status == BookStatus.COMPLETED }
                if (completedBooks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Book,
                            title = "${state.selectedMonth}월 완독 기록 없음",
                            description = "이 달에 완독한 책이 없습니다",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "완독한 책 (${completedBooks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(completedBooks) { book ->
                        CompletedBookRow(
                            book = book,
                            onClick = { onNavigateToBookDetail(book) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    completedBooks: List<UserBookDto>,
    onBookClick: (UserBookDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val startOffset = firstDay.dayOfWeek.value % 7
    val dayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")
    val completedDateMap = completedBooks.groupBy { it.endDate.take(10) }
    val today = LocalDate.now()

    var dialogBooks by remember { mutableStateOf<Pair<Int, List<UserBookDto>>?>(null) }

    dialogBooks?.let { (day, books) ->
        AlertDialog(
            onDismissRequest = { dialogBooks = null },
            title = { Text("${month}월 ${day}일 완독") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    books.forEach { book ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // 책 행 탭 → 다이얼로그 닫고 책 상세로 이동
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dialogBooks = null
                                    onBookClick(book)
                                },
                        ) {
                            if (book.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = book.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                            }
                            Column(modifier = Modifier.padding(start = 8.dp)) {
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
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogBooks = null }) { Text("닫기") }
            },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            val cells = startOffset + daysInMonth
            val rows = (cells + 6) / 7

            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIdx = row * 7 + col
                        val day = cellIdx - startOffset + 1
                        val isValid = day in 1..daysInMonth
                        val dateStr = if (isValid) "%04d-%02d-%02d".format(year, month, day) else ""
                        val booksOnDay = if (isValid) completedDateMap[dateStr] ?: emptyList() else emptyList()
                        val isToday = isValid && year == today.year && month == today.monthValue && day == today.dayOfMonth

                        // 규격화된 공용 셀 — 고정 비율, 표지 스택, 앵커 배지
                        CalendarDayCell(
                            day = if (isValid) day else null,
                            isToday = isToday,
                            books = booksOnDay,
                            onClick = if (booksOnDay.isNotEmpty()) {
                                { dialogBooks = Pair(day, booksOnDay) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedBookRow(
    book: UserBookDto,
    onClick: () -> Unit = {},
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
                Spacer(Modifier.size(12.dp))
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
                if (book.endDate.isNotBlank()) {
                    Text(
                        text = "완독일: ${book.endDate.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (book.totalPage > 0) {
                Text(
                    text = "${book.totalPage}p",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
