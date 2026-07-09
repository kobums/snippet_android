package com.gowoobro.snippet.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.core.model.PopularBookDto
import com.gowoobro.snippet.ui.components.BookCover
import com.gowoobro.snippet.ui.components.BookCoverSize
import com.gowoobro.snippet.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * 인기 도서 화면 — 국립중앙도서관 인기 대출 도서 목록.
 * 필터 칩(기간/KDC 장르/연령/성별) + 무한 스크롤.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopularBooksScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 필터 상태
    var selectedPeriod by remember { mutableStateOf<String?>(null) }
    var selectedKdc by remember { mutableStateOf<String?>(null) }
    var selectedAge by remember { mutableStateOf<String?>(null) }
    var selectedGender by remember { mutableStateOf<String?>(null) }

    val periodOptions = listOf("주간" to null, "1개월" to "1month", "3개월" to "3month", "6개월" to "6month")
    val kdcOptions = listOf("전체" to null, "문학" to "8", "사회과학" to "3", "자연과학" to "4", "철학" to "1", "역사" to "9")
    val ageOptions = listOf("전체" to null, "유아" to "0", "초등" to "1", "청소년" to "2", "성인" to "3")
    val genderOptions = listOf("전체" to null, "남성" to "M", "여성" to "F")

    // 초기 로드
    LaunchedEffect(Unit) {
        if (state.popularBooks.isEmpty()) {
            vm.loadPopularBooks(reset = true)
        }
    }

    // 무한 스크롤 (끝 - 200 기점)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to layout.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) {
                vm.loadPopularBooks(reset = false)
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    // 책 추가 바텀시트
    var selectedPopularBook by remember { mutableStateOf<PopularBookDto?>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    if (selectedPopularBook != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedPopularBook = null },
            sheetState = addSheetState,
        ) {
            PopularBookAddSheet(
                book = selectedPopularBook!!,
                onDismiss = { selectedPopularBook = null },
                onConfirm = { book, type, status ->
                    vm.addPopularBook(
                        book = book,
                        type = type,
                        status = status,
                        onSuccess = {
                            selectedPopularBook = null
                            scope.launch { snackbarHostState.showSnackbar("\"${book.title}\" 추가 완료!") }
                        },
                        onError = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("인기 도서") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadPopularBooks(reset = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "새로고침")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isLoadingPopular,
            onRefresh = { vm.loadPopularBooks(reset = true) },
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // 필터 칩 4줄
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 기간
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            periodOptions.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedPeriod == value,
                                    onClick = { selectedPeriod = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                        // KDC 장르
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            kdcOptions.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedKdc == value,
                                    onClick = { selectedKdc = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                        // 연령
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ageOptions.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedAge == value,
                                    onClick = { selectedAge = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                        // 성별
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            genderOptions.forEach { (label, value) ->
                                FilterChip(
                                    selected = selectedGender == value,
                                    onClick = { selectedGender = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }

                if (state.popularBooks.isEmpty() && !state.isLoadingPopular) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            title = "인기 도서 정보를 불러올 수 없습니다",
                            actionLabel = "다시 시도",
                            onActionClick = { vm.loadPopularBooks(reset = true) },
                        )
                    }
                } else {
                    items(state.popularBooks) { book ->
                        PopularBookItem(
                            book = book,
                            onAddClick = {
                                selectedPopularBook = book
                                scope.launch { addSheetState.show() }
                            },
                        )
                    }
                }

                if (state.isLoadingPopular) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularBookItem(
    book: PopularBookDto,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 순위 뱃지
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = if (book.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${book.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (book.rank <= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BookCover(
                coverUrl = book.coverUrl,
                size = BookCoverSize.Large,
                contentDescription = book.title,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.publisher.isNotBlank()) {
                    Text(
                        text = book.publisher,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (book.loanCount > 0) {
                    Text(
                        text = "대출 ${book.loanCount}회",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "서재에 추가",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopularBookAddSheet(
    book: PopularBookDto,
    onDismiss: () -> Unit,
    onConfirm: (PopularBookDto, BookType, BookStatus) -> Unit,
) {
    var selectedType by remember { mutableStateOf(BookType.HAVE) }
    var selectedStatus by remember { mutableStateOf(BookStatus.WAITING) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("책 추가", style = MaterialTheme.typography.titleLarge)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookCover(coverUrl = book.coverUrl, size = BookCoverSize.Large, contentDescription = book.title)
            Column {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 분류
        Text("분류", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BookType.WISH to "위시리스트", BookType.HAVE to "소장", BookType.BORROW to "대출").forEach { (type, label) ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(label) },
                )
            }
        }

        if (selectedType != BookType.WISH) {
            Text("상태", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BookStatus.WAITING to "읽을 예정", BookStatus.READING to "읽는 중", BookStatus.COMPLETED to "완독").forEach { (status, label) ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status },
                        label = { Text(label) },
                    )
                }
            }
        }

        Button(
            onClick = {
                val status = if (selectedType == BookType.WISH) BookStatus.NONE else selectedStatus
                onConfirm(book, selectedType, status)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("추가하기")
        }
        Spacer(Modifier.height(16.dp))
    }
}
