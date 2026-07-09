package com.gowoobro.snippet.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookSearchDto
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.ui.components.BookCover
import com.gowoobro.snippet.ui.components.BookCoverSize
import com.gowoobro.snippet.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * 책 검색 화면 — 알라딘 검색(500ms 디바운스), 결과 탭 → AddBookBottomSheet.
 * 우측 상단 바코드 버튼 → GMS Code Scanner로 ISBN 인식 후 검색어 주입.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    initialBookType: BookType = BookType.HAVE,
    initialQuery: String = "",
    onBack: () -> Unit = {},
    onBookAdded: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 바코드 스캔으로 전달받은 ISBN을 최초 1회 검색어로 주입
    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank() && state.searchQuery.isBlank()) {
            vm.setSearchQuery(initialQuery)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 바텀시트 상태
    var selectedBook by remember { mutableStateOf<BookSearchDto?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // 스낵바 처리
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    // 무한 스크롤 (90% 지점)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to layout.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= (total * 0.9).toInt()) {
                vm.loadMoreSearchResults()
            }
        }
    }

    if (selectedBook != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedBook = null },
            sheetState = bottomSheetState,
        ) {
            AddBookBottomSheet(
                book = selectedBook!!,
                initialBookType = initialBookType,
                onDismiss = { selectedBook = null },
                onConfirm = { bookSearch, type, status ->
                    vm.addBook(
                        bookSearch = bookSearch,
                        type = type,
                        status = status,
                        onSuccess = {
                            selectedBook = null
                            scope.launch { snackbarHostState.showSnackbar("\"${bookSearch.title}\" 추가 완료!") }
                            onBookAdded()
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
                title = { Text("책 검색") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // 바코드 스캔 → ISBN 인식 후 검색어로 주입 (GMS Code Scanner)
                    IconButton(onClick = {
                        launchIsbnScan(
                            context = context,
                            onIsbn = { isbn -> vm.setSearchQuery(isbn) },
                            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                    }) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "바코드 스캔")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 검색바
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("책 제목, 저자, ISBN으로 검색...") },
                leadingIcon = {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.SearchOff, contentDescription = null)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            when {
                state.searchQuery.isBlank() -> {
                    // 초기 상태
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "책을 검색해보세요",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.searchResults.isEmpty() && !state.isSearching -> {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "검색 결과가 없습니다",
                        description = "다른 검색어를 시도해보세요",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.searchResults) { book ->
                            SearchResultItem(
                                book = book,
                                onClick = {
                                    selectedBook = book
                                    scope.launch { bottomSheetState.show() }
                                },
                            )
                        }
                        if (state.isLoadingMoreSearch) {
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
    }
}

@Composable
private fun SearchResultItem(
    book: BookSearchDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        text = "${book.publisher}${if (book.pubDate.isNotBlank()) " · ${book.pubDate.take(10)}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 책 추가 바텀시트 — 분류(소장/대출/위시) + 상태(읽을예정/읽는중/완독) 선택 후 추가.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookBottomSheet(
    book: BookSearchDto,
    initialBookType: BookType,
    onDismiss: () -> Unit,
    onConfirm: (BookSearchDto, BookType, BookStatus) -> Unit,
) {
    var selectedType by remember { mutableStateOf(initialBookType.takeIf { it != BookType.RETURN } ?: BookType.HAVE) }
    var selectedStatus by remember { mutableStateOf(BookStatus.WAITING) }

    val typeOptions = listOf(
        BookType.WISH to "위시리스트",
        BookType.HAVE to "소장",
        BookType.BORROW to "대출",
    )
    val statusOptions = listOf(
        BookStatus.WAITING to "읽을 예정",
        BookStatus.READING to "읽는 중",
        BookStatus.COMPLETED to "완독",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 핸들
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .align(Alignment.CenterHorizontally)
                .padding(top = 0.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
        }

        Text("책 추가", style = MaterialTheme.typography.titleLarge)

        // 책 정보 미리보기
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookCover(
                coverUrl = book.coverUrl,
                size = BookCoverSize.Large,
                contentDescription = book.title,
            )
            Column {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()

        // 분류 선택
        Text("분류", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            typeOptions.forEach { (type, label) ->
                val selected = selectedType == type
                if (selected) {
                    Button(
                        onClick = { selectedType = type },
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(label) }
                } else {
                    TextButton(
                        onClick = { selectedType = type },
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(label) }
                }
            }
        }

        // 상태 선택 (위시 선택 시 숨김)
        if (selectedType != BookType.WISH) {
            Text("상태", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statusOptions.forEach { (status, label) ->
                    val selected = selectedStatus == status
                    if (selected) {
                        Button(
                            onClick = { selectedStatus = status },
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(label) }
                    } else {
                        TextButton(
                            onClick = { selectedStatus = status },
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(label) }
                    }
                }
            }
        }

        // 추가 버튼
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
    }
}
