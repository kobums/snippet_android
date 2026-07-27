package com.gowoobro.snippet.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.ui.components.BookCover
import com.gowoobro.snippet.ui.components.BookCoverSize
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.FloatingSubTabBar
import com.gowoobro.snippet.ui.theme.BadgeNeutral
import com.gowoobro.snippet.ui.theme.StatusCaution
import com.gowoobro.snippet.ui.theme.StatusError
import com.gowoobro.snippet.ui.theme.StatusInfo
import com.gowoobro.snippet.ui.theme.StatusSuccess
import com.gowoobro.snippet.ui.theme.StatusWarning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 플로팅 서브탭 바가 차지하는 대략 높이 (콘텐츠 상단 여백 계산용) */
private val FloatingBarHeight = 64.dp

/**
 * 서재 탭 루트 화면 — 소장 | 대출 | 위시 서브탭.
 *
 * iOS 확정 디자인:
 * - 상단 헤더/세그먼트 없이 콘텐츠가 화면 전체(상태바 뒤까지) 사용
 * - 플로팅 한 줄: [소장|대출|위시 캡슐 서브탭 바] ... (인기도서 원형) (+ 원형)
 * - 서브탭 전환은 Crossfade 직접 표시, 검색 바 없음(책 추가 모달의 검색은 유지)
 *
 * @param bottomOverlayPadding 하단 탭바(NavigationBar)가 콘텐츠를 가리는 높이 —
 *        그리드 contentPadding으로 흘려보내 가림을 방지한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabScreen(
    onNavigateToBookSearch: (BookType, String) -> Unit = { _, _ -> },
    onNavigateToPopularBooks: () -> Unit = {},
    onNavigateToBookDetail: (UserBookDto) -> Unit = {},
    bottomOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    // 스낵바 처리
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    val currentBookType = when (selectedTab) {
        0 -> BookType.HAVE
        1 -> BookType.BORROW
        else -> BookType.WISH
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 콘텐츠 시작 위치: 상태바 높이 + 플로팅 바 높이
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val contentTopPadding = statusBarPadding + FloatingBarHeight
        val gridPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopPadding + 4.dp,
            bottom = bottomOverlayPadding + 16.dp,
        )

        val ptrState = rememberPullToRefreshState()
        // 초기 로딩(그리드 중앙 스피너 담당)에는 새로고침 인디케이터를 겹쳐 돌리지 않는다
        val currentTabBooks = when (selectedTab) {
            0 -> state.haveBooks
            1 -> state.borrowBooks
            else -> state.wishBooks
        }
        val refreshing = state.isLoading && currentTabBooks.isNotEmpty()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                // 플로팅 바에 가리지 않도록 콘텐츠 시작선 아래에서 표시
                PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentTopPadding),
                )
            },
        ) {
            // 서브탭 전환: 선택 섹션 직접 표시 + Crossfade
            Crossfade(
                targetState = selectedTab,
                label = "librarySubTab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    0 -> LibraryBookGrid(
                        books = state.haveBooks,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = { vm.loadMoreBooks(BookType.HAVE) },
                        emptyTitle = "소장한 책이 없습니다",
                        emptyDescription = "첫 책을 추가해보세요!",
                        onBookClick = onNavigateToBookDetail,
                        onStatusChange = { book, status -> vm.updateBookStatus(book.id, status) },
                        contentPadding = gridPadding,
                    )
                    1 -> LibraryBookGrid(
                        books = state.borrowBooks,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = { vm.loadMoreBooks(BookType.BORROW) },
                        emptyTitle = "빌린 책이 없습니다",
                        emptyDescription = "대출한 책을 추가해보세요!",
                        onBookClick = onNavigateToBookDetail,
                        onStatusChange = { book, status -> vm.updateBookStatus(book.id, status) },
                        contentPadding = gridPadding,
                    )
                    else -> LibraryBookGrid(
                        books = state.wishBooks,
                        isInitialLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = { vm.loadMoreBooks(BookType.WISH) },
                        emptyTitle = "위시리스트가 비어있습니다",
                        emptyDescription = "읽고 싶은 책을 추가해보세요!",
                        onBookClick = onNavigateToBookDetail,
                        onStatusChange = { book, status -> vm.updateBookStatus(book.id, status) },
                        contentPadding = gridPadding,
                    )
                }
            }
        }

        // 플로팅 한 줄: [소장|대출|위시] ... (인기도서) (+)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingSubTabBar(
                tabs = listOf(0 to "소장", 1 to "대출", 2 to "위시"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
            Spacer(modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = onNavigateToPopularBooks) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = "인기 도서",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { onNavigateToBookSearch(currentBookType, "") }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "책 추가",
                    modifier = Modifier.size(24.dp),
                )
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

@Composable
private fun LibraryBookGrid(
    books: List<UserBookDto>,
    isInitialLoading: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    emptyTitle: String,
    emptyDescription: String,
    onBookClick: (UserBookDto) -> Unit,
    onStatusChange: (UserBookDto, BookStatus) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // 무한 스크롤: 끝에서 6개 남은 지점에서 로드
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem to totalItems
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 6) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (isInitialLoading) {
                    // 초기 로딩: 빈 상태 문구 대신 중앙 스피너만
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = emptyTitle,
                        description = emptyDescription,
                    )
                }
            }
        } else {
            items(books) { book ->
                BookGridCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onStatusChange = { status -> onStatusChange(book, status) },
                )
            }
        }

        if (isLoadingMore && books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * 서재 책 그리드 카드 — 모든 카드 높이 동일 규격.
 * - 표지: 가로:세로 0.72 비율 고정 (넘침 없음)
 * - 상태 배지 우상단 / 대출 D-day 배지 우하단 오버레이
 * - 제목 2줄 고정, 저자 1줄 고정
 * - 하단 부가 영역 고정 52dp: (읽는중) 진행률+% 한 줄 + 상태 버튼(읽기 시작 / 완독·중단)
 */
@Composable
fun BookGridCard(
    book: UserBookDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onStatusChange: (BookStatus) -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // 표지 + 상태/D-day 뱃지
            Box {
                BookCover(
                    coverUrl = book.coverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f),
                    size = BookCoverSize.Large,
                )
                // 상태 뱃지 (우상단)
                if (book.type != BookType.WISH) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = statusBadgeColor(book.status),
                    ) {
                        Text(
                            text = statusLabel(book.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                // 대출 반납 D-day 뱃지 (우하단)
                if (book.type == BookType.BORROW && book.returnDate != null) {
                    val dday = computeDday(book.returnDate)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = ddayColor(dday),
                    ) {
                        Text(
                            text = ddayLabel(dday),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                // 제목 — 항상 2줄 높이
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 저자 — 1줄 고정 (없어도 높이 유지)
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                // 하단 부가 영역 — 고정 52dp (상단 정렬)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    when (book.status) {
                        BookStatus.READING -> {
                            if (book.totalPage > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    LinearProgressIndicator(
                                        progress = { book.progress },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp),
                                    )
                                    Text(
                                        text = "${(book.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniStatusButton(
                                    label = "완독",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStatusChange(BookStatus.COMPLETED) },
                                )
                                MiniStatusButton(
                                    label = "중단",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onStatusChange(BookStatus.DROPPED) },
                                )
                            }
                        }
                        BookStatus.WAITING -> {
                            MiniStatusButton(
                                label = "읽기 시작",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onStatusChange(BookStatus.READING) },
                            )
                        }
                        else -> {
                            // 완독/중단/위시 — 빈 공간 (카드 높이 통일)
                        }
                    }
                }
            }
        }
    }
}

/** 카드 하단의 컴팩트 상태 변경 버튼 — 은은한 tonal 캡슐 */
@Composable
private fun MiniStatusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

fun statusBadgeColor(status: BookStatus): Color = when (status) {
    BookStatus.WAITING -> BadgeNeutral    // iOS systemGray
    BookStatus.READING -> StatusInfo      // iOS systemBlue (브랜드 정합)
    BookStatus.COMPLETED -> StatusSuccess // 브랜드 그린 #34C759
    BookStatus.DROPPED -> StatusCaution   // iOS systemOrange
    BookStatus.NONE -> BadgeNeutral
}

fun statusLabel(status: BookStatus): String = when (status) {
    BookStatus.WAITING -> "대기중"
    BookStatus.READING -> "읽는중"
    BookStatus.COMPLETED -> "완독"
    BookStatus.DROPPED -> "중단"
    BookStatus.NONE -> ""
}

fun computeDday(returnDate: String): Long {
    return try {
        val target = LocalDate.parse(returnDate.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        ChronoUnit.DAYS.between(LocalDate.now(), target)
    } catch (e: Exception) {
        999L
    }
}

fun ddayLabel(dday: Long): String = when {
    dday < 0 -> "연체 ${-dday}일"
    dday == 0L -> "D-Day"
    else -> "D-$dday"
}

fun ddayColor(dday: Long): Color = when {
    dday < 0 -> StatusError
    dday <= 3 -> StatusWarning
    else -> StatusSuccess
}
