package com.gowoobro.snippet.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.BookSearchDto
import com.gowoobro.snippet.core.model.BookStatus
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.core.model.LibraryAddRequest
import com.gowoobro.snippet.core.model.PopularBookDto
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.core.model.UserBookUpdateRequest
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class LibraryUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // 서재 목록 (전체)
    val allBooks: List<UserBookDto> = emptyList(),
    // 페이지네이션
    val currentPage: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    // 탭별 검색어
    val haveQuery: String = "",
    val borrowQuery: String = "",
    val wishQuery: String = "",
    // 책 검색 화면
    val searchQuery: String = "",
    val searchResults: List<BookSearchDto> = emptyList(),
    val isSearching: Boolean = false,
    val searchPage: Int = 1,
    val hasMoreSearch: Boolean = true,
    val isLoadingMoreSearch: Boolean = false,
    // 인기 도서
    val popularBooks: List<PopularBookDto> = emptyList(),
    val isLoadingPopular: Boolean = false,
    val popularPage: Int = 1,
    val hasMorePopular: Boolean = true,
    // 책 상세 - 책별 기록/세션
    val detailRecords: List<RecordDto> = emptyList(),
    val detailSessions: List<ReadingSessionDto> = emptyList(),
    val isLoadingDetail: Boolean = false,
    // 스낵바 메시지
    val snackbarMessage: String? = null,
)

val LibraryUiState.haveBooks: List<UserBookDto>
    get() = allBooks.filter { it.type == BookType.HAVE }.let { books ->
        if (haveQuery.isBlank()) books
        else books.filter { b -> b.title.contains(haveQuery, ignoreCase = true) || b.author.contains(haveQuery, ignoreCase = true) }
    }

val LibraryUiState.borrowBooks: List<UserBookDto>
    get() = allBooks.filter { it.type == BookType.BORROW }.let { books ->
        if (borrowQuery.isBlank()) books
        else books.filter { b -> b.title.contains(borrowQuery, ignoreCase = true) || b.author.contains(borrowQuery, ignoreCase = true) }
    }

val LibraryUiState.wishBooks: List<UserBookDto>
    get() = allBooks.filter { it.type == BookType.WISH }.let { books ->
        if (wishQuery.isBlank()) books
        else books.filter { b -> b.title.contains(wishQuery, ignoreCase = true) || b.author.contains(wishQuery, ignoreCase = true) }
    }

/**
 * 서재 탭 ViewModel — 보유/대출/위시 목록, 책 검색(알라딘), 인기 도서,
 * 책 추가/상태변경/삭제, 책별 기록·세션 조회(상세용).
 */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadLibrary()
    }

    // ─── 서재 목록 ───────────────────────────────────────────────

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentPage = 0, hasMore = true) }
            val books = safeApiCall { container.userBookApi.getPaged(0, 20) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    allBooks = books,
                    currentPage = 0,
                    hasMore = books.size >= 20,
                )
            }
        }
    }

    fun loadMoreBooks() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1
            val books = safeApiCall { container.userBookApi.getPaged(nextPage, 20) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isLoadingMore = false,
                    allBooks = it.allBooks + books,
                    currentPage = nextPage,
                    hasMore = books.size >= 20,
                )
            }
        }
    }

    fun refresh() = loadLibrary()

    // ─── 탭 검색어 ────────────────────────────────────────────────

    fun setHaveQuery(q: String) = _uiState.update { it.copy(haveQuery = q) }
    fun setBorrowQuery(q: String) = _uiState.update { it.copy(borrowQuery = q) }
    fun setWishQuery(q: String) = _uiState.update { it.copy(wishQuery = q) }

    // ─── 책 추가 ──────────────────────────────────────────────────

    fun addBook(
        bookSearch: BookSearchDto,
        type: BookType,
        status: BookStatus,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val now = LocalDate.now().format(isoFormatter).let {
                LocalDate.now().atStartOfDay().format(isoFormatter)
            }
            val request = LibraryAddRequest(
                title = bookSearch.title,
                author = bookSearch.author,
                publisher = bookSearch.publisher,
                pubDate = bookSearch.pubDate,
                isbn = bookSearch.isbn,
                coverUrl = bookSearch.coverUrl,
                totalPage = bookSearch.totalPage ?: 0,
                type = type,
                status = status,
                readPage = 0,
                startDate = now,
                endDate = now,
                createDate = now,
            )
            when (val result = safeApiCall { container.userBookApi.add(request) }) {
                is AppResult.Success -> {
                    // POST 응답은 bare Long → 목록 재조회로 동기화
                    loadLibrary()
                    onSuccess()
                }
                is AppResult.Failure -> onError(result.error.message)
            }
        }
    }

    fun addPopularBook(
        book: PopularBookDto,
        type: BookType,
        status: BookStatus,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val now = LocalDate.now().atStartOfDay().format(isoFormatter)
            val request = LibraryAddRequest(
                title = book.title,
                author = book.author,
                publisher = book.publisher,
                pubDate = "",
                isbn = book.isbn13,
                coverUrl = book.coverUrl,
                totalPage = 0,
                type = type,
                status = status,
                readPage = 0,
                startDate = now,
                endDate = now,
                createDate = now,
            )
            when (val result = safeApiCall { container.userBookApi.add(request) }) {
                is AppResult.Success -> {
                    loadLibrary()
                    onSuccess()
                }
                is AppResult.Failure -> onError(result.error.message)
            }
        }
    }

    // ─── 책 상태 변경 ─────────────────────────────────────────────

    fun updateBookStatus(id: Long, status: BookStatus) {
        viewModelScope.launch {
            val result = safeApiCall {
                container.userBookApi.update(id, UserBookUpdateRequest(status = status))
            }
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(allBooks = state.allBooks.map { if (it.id == id) result.data else it })
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    fun updateBookType(id: Long, type: BookType) {
        viewModelScope.launch {
            val result = safeApiCall {
                container.userBookApi.update(id, UserBookUpdateRequest(type = type))
            }
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(allBooks = state.allBooks.map { if (it.id == id) result.data else it })
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    fun updateBookProgress(id: Long, readPage: Int) {
        viewModelScope.launch {
            val result = safeApiCall {
                container.userBookApi.update(id, UserBookUpdateRequest(readPage = readPage))
            }
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(allBooks = state.allBooks.map { if (it.id == id) result.data else it })
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    /** 반납 예정일 1주 연장. (반납일은 생성/전환 시점에 항상 채워지므로 미설정이면 아무 것도 안 함) */
    fun extendReturnDate(id: Long, currentReturnDate: String?) {
        val base = currentReturnDate
            ?.take(10)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        viewModelScope.launch {
            val result = safeApiCall {
                container.userBookApi.update(id, UserBookUpdateRequest(returnDate = base.plusDays(7).toString()))
            }
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        // 상세 화면이 allBooks에서 책을 찾으므로, 목록에 없으면 추가해 변경이 반영되게 한다
                        val updated = if (state.allBooks.any { it.id == id }) {
                            state.allBooks.map { if (it.id == id) result.data else it }
                        } else {
                            state.allBooks + result.data
                        }
                        state.copy(allBooks = updated)
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    fun updateBookRating(id: Long, rating: Int) {
        viewModelScope.launch {
            val result = safeApiCall {
                container.userBookApi.update(id, UserBookUpdateRequest(rating = rating))
            }
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(allBooks = state.allBooks.map { if (it.id == id) result.data else it })
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    fun deleteBook(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = safeApiCall { container.userBookApi.delete(id) }) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(allBooks = state.allBooks.filter { it.id != id })
                    }
                    onSuccess()
                }
                is AppResult.Failure -> _uiState.update { it.copy(snackbarMessage = result.error.message) }
            }
        }
    }

    // ─── 책 검색 ──────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchPage = 1, hasMoreSearch = true) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500) // 500ms 디바운스
            _uiState.update { it.copy(isSearching = true) }
            val results = safeApiCall { container.bookApi.search(query, 1) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    searchPage = 1,
                    hasMoreSearch = results.size >= 10,
                )
            }
        }
    }

    fun loadMoreSearchResults() {
        val state = _uiState.value
        if (state.isLoadingMoreSearch || !state.hasMoreSearch || state.searchQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreSearch = true) }
            val nextPage = state.searchPage + 1
            val results = safeApiCall { container.bookApi.search(state.searchQuery, nextPage) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isLoadingMoreSearch = false,
                    searchResults = it.searchResults + results,
                    searchPage = nextPage,
                    hasMoreSearch = results.size >= 10,
                )
            }
        }
    }

    // ─── 인기 도서 ────────────────────────────────────────────────

    fun loadPopularBooks(reset: Boolean = false) {
        val state = _uiState.value
        if (!reset && (state.isLoadingPopular || !state.hasMorePopular)) return
        viewModelScope.launch {
            val page = if (reset) 1 else state.popularPage
            _uiState.update {
                if (reset) it.copy(isLoadingPopular = true, popularBooks = emptyList(), popularPage = 1)
                else it.copy(isLoadingPopular = true)
            }
            val books = safeApiCall { container.bookApi.getPopular(pageNo = page, pageSize = 20) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isLoadingPopular = false,
                    popularBooks = if (reset) books else it.popularBooks + books,
                    popularPage = page + 1,
                    hasMorePopular = books.size >= 20,
                )
            }
        }
    }

    // ─── 책 상세 - 기록/세션 ─────────────────────────────────────

    fun loadBookDetail(bookId: Long, userBookId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            val recordsDeferred = viewModelScope.launch {
                val records = safeApiCall { container.recordApi.getByBook(bookId) }
                    .getOrDefault(emptyList())
                _uiState.update { it.copy(detailRecords = records) }
            }
            val sessionsDeferred = viewModelScope.launch {
                val sessions = safeApiCall { container.readingSessionApi.getByBook(userBookId) }
                    .getOrDefault(emptyList())
                _uiState.update { it.copy(detailSessions = sessions) }
            }
            recordsDeferred.join()
            sessionsDeferred.join()
            _uiState.update { it.copy(isLoadingDetail = false) }
        }
    }

    // ─── 스낵바 ───────────────────────────────────────────────────

    fun consumeSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    // ─── Factory ─────────────────────────────────────────────────

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(container) as T
            }
    }
}
