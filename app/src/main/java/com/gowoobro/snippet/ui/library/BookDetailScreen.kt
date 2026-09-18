package com.gowoobro.snippet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.ui.components.BookHeader
import com.gowoobro.snippet.ui.components.EmptyState
import com.gowoobro.snippet.ui.components.RatingStars
import kotlinx.coroutines.launch

/**
 * 책 상세 화면.
 * 탭 구조: 정보 | 스니펫 | 독서일기 | 리뷰 | 독서세션
 * - 정보 탭: 책 메타, 상태변경, 진행률, 별점(완독 시)
 * - 나머지 탭: 해당 타입의 기록/세션 목록
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    userBook: UserBookDto,
    onBack: () -> Unit = {},
    onBookDeleted: () -> Unit = {},
    onNavigateToTimer: (userBookId: Long, bookTitle: String, startPage: Int) -> Unit = { _, _, _ -> },
    onNavigateToAddRecord: (type: RecordType, bookId: Long) -> Unit = { _, _ -> },
    // 기록 추가 화면에서 돌아왔을 때 책별 기록 새로고침 트리거
    refreshSignal: Int = 0,
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 현재 책: 서버 응답으로 갱신되는 detailBook 우선 (첫 페이지 목록에 없는 책도 변경이 반영된다)
    val book = state.detailBook?.takeIf { it.id == userBook.id } ?: userBook

    // 탭: 0=정보, 1=스니펫, 2=독서일기, 3=리뷰, 4=독서세션
    var selectedDetailTab by remember { mutableIntStateOf(0) }
    val detailTabs = listOf("정보", "스니펫", "독서일기", "리뷰", "독서세션")

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRatingSheet by remember { mutableStateOf(false) }
    val ratingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 초기 데이터 로드 + 기록 추가 후(refreshSignal 변경) 재조회
    LaunchedEffect(userBook.id, refreshSignal) {
        vm.loadBookDetail(userBook)
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    // 별점 바텀시트
    if (showRatingSheet) {
        var tempRating by remember { mutableIntStateOf(book.rating ?: 0) }
        ModalBottomSheet(
            onDismissRequest = { showRatingSheet = false },
            sheetState = ratingSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("독서 완료!", style = MaterialTheme.typography.titleLarge)
                Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("이 책은 어떠셨나요?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RatingStars(
                    rating = tempRating.toDouble(),
                    starSize = 40.dp,
                    onRatingChange = { star ->
                        tempRating = if (tempRating == star) 0 else star
                    },
                )
                val ratingLabels = listOf("", "별로였어요", "그저 그래요", "괜찮아요", "좋았어요!", "최고였어요!")
                if (tempRating > 0) {
                    Text(ratingLabels[tempRating], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showRatingSheet = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("건너뛰기") }
                    androidx.compose.material3.Button(
                        onClick = {
                            if (tempRating > 0) {
                                vm.updateBookRating(book.id, tempRating)
                            }
                            showRatingSheet = false
                        },
                        enabled = tempRating > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("저장") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("책 삭제") },
            text = { Text("\"${book.title}\"을(를) 서재에서 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteBook(book.id) { onBookDeleted() }
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        floatingActionButton = {
            // 기록 탭(스니펫/독서일기/리뷰)에서만 "기록 추가" FAB 표시 (정보/독서세션 제외)
            val recordType: RecordType? = when (selectedDetailTab) {
                1 -> RecordType.SNIPPET
                2 -> RecordType.DIARY
                3 -> RecordType.REVIEW
                else -> null
            }
            if (recordType != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToAddRecord(recordType, book.bookId) },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "기록 추가",
                        )
                    },
                    text = { Text("기록 추가") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 가로 스크롤 탭 칩
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detailTabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedDetailTab == index,
                        onClick = { selectedDetailTab = index },
                        label = { Text(title) },
                    )
                }
            }

            HorizontalDivider()

            when (selectedDetailTab) {
                0 -> BookInfoTab(
                    book = book,
                    onStatusChange = { newStatus ->
                        // 별점 시트는 완독 저장이 성공한 뒤에만 띄운다
                        vm.updateBookStatus(book.id, newStatus) {
                            if (newStatus == BookStatus.COMPLETED) {
                                showRatingSheet = true
                            }
                        }
                    },
                    onTypeChange = { newType -> vm.updateBookType(book.id, newType) },
                    onExtendReturn = { vm.extendReturnDate(book.id, book.returnDate) },
                    onRatingClick = { showRatingSheet = true },
                    onStartReading = {
                        onNavigateToTimer(book.id, book.title, book.readPage)
                    },
                )
                1 -> BookRecordsTab(
                    records = state.detailRecords.filter { it.type == RecordType.SNIPPET },
                    isLoading = state.isLoadingDetail,
                    emptyTitle = "아직 스니펫이 없습니다",
                )
                2 -> BookRecordsTab(
                    records = state.detailRecords.filter { it.type == RecordType.DIARY },
                    isLoading = state.isLoadingDetail,
                    emptyTitle = "아직 독서일기가 없습니다",
                )
                3 -> BookRecordsTab(
                    records = state.detailRecords.filter { it.type == RecordType.REVIEW },
                    isLoading = state.isLoadingDetail,
                    emptyTitle = "아직 리뷰가 없습니다",
                )
                4 -> BookSessionsTab(
                    sessions = state.detailSessions,
                    isLoading = state.isLoadingDetail,
                )
            }
        }
    }
}

@Composable
private fun BookInfoTab(
    book: UserBookDto,
    onStatusChange: (BookStatus) -> Unit,
    onTypeChange: (BookType) -> Unit,
    onExtendReturn: () -> Unit = {},
    onRatingClick: () -> Unit,
    onStartReading: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 책 헤더
        item {
            BookHeader(
                title = book.title,
                author = book.author,
                coverUrl = book.coverUrl,
                badge = statusLabel(book.status).takeIf { it.isNotBlank() },
            )
        }

        // 분류 & 상태 변경
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("분류 변경", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(BookType.WISH to "위시", BookType.HAVE to "소장", BookType.BORROW to "대출").forEach { (type, label) ->
                            val selected = book.type == type
                            FilterChip(
                                selected = selected,
                                onClick = { if (!selected) onTypeChange(type) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (book.type != BookType.WISH) {
                        HorizontalDivider()
                        Text("상태 변경", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                BookStatus.WAITING to "대기중",
                                BookStatus.READING to "읽는중",
                                BookStatus.COMPLETED to "완독",
                                BookStatus.DROPPED to "중단",
                            ).forEach { (status, label) ->
                                val selected = book.status == status
                                FilterChip(
                                    selected = selected,
                                    onClick = { if (!selected) onStatusChange(status) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 반납 카드 (대출일 때) — 반납 예정일 + D-day + 1주 연기
        if (book.type == BookType.BORROW) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("반납 예정일", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = book.returnDate?.take(10) ?: "미설정",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                book.returnDate?.let { returnDate ->
                                    val dday = computeDday(returnDate)
                                    Text(
                                        text = ddayLabel(dday),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(ddayColor(dday), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = onExtendReturn,
                            enabled = book.returnDate != null,
                        ) {
                            Text("1주 연기")
                        }
                    }
                }
            }
        }

        // 진행률 카드 (위시 아닐 때)
        if (book.type != BookType.WISH && book.totalPage > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("진행률", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${book.readPage} / ${book.totalPage} 페이지",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { book.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Text(
                            "${(book.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // 독서 시작 버튼 (읽는 중일 때)
                        if (book.status == BookStatus.READING) {
                            androidx.compose.material3.Button(
                                onClick = onStartReading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("독서 시작")
                            }
                        }
                    }
                }
            }
        }

        // 별점 카드 (완독일 때)
        if (book.status == BookStatus.COMPLETED) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("별점", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            RatingStars(rating = (book.rating ?: 0).toDouble())
                        }
                        TextButton(onClick = onRatingClick) {
                            Text(if (book.rating != null) "수정" else "평가하기")
                        }
                    }
                }
            }
        }

        // 독서 날짜 카드
        if (book.startDate.isNotBlank() || book.endDate.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("독서 기간", style = MaterialTheme.typography.titleSmall)
                        if (book.startDate.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("시작", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(book.startDate.take(10), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (book.status == BookStatus.COMPLETED && book.endDate.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("완료", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(book.endDate.take(10), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRecordsTab(
    records: List<RecordDto>,
    isLoading: Boolean,
    emptyTitle: String,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        records.isEmpty() -> EmptyState(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = emptyTitle,
            modifier = modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(records) { record ->
                RecordCard(record = record)
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: RecordDto,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!record.tag.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "#${record.tag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.relatedPage != null) {
                        Text(
                            "p.${record.relatedPage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        record.createDate.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookSessionsTab(
    sessions: List<ReadingSessionDto>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        sessions.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Timer,
            title = "아직 독서 세션이 없습니다",
            modifier = modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions) { session ->
                SessionCard(session = session)
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ReadingSessionDto,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(session.sessionDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val hours = session.durationSeconds / 3600
                val minutes = (session.durationSeconds % 3600) / 60
                Text(
                    text = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${session.startPage}p → ${session.endPage}p (+${session.pagesRead}p)",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (session.secondsPerPage > 0) {
                Text(
                    text = "페이스: ${"%.1f".format(session.secondsPerPage / 60)} min/p",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
