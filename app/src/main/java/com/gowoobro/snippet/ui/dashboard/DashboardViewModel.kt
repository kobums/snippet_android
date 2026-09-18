package com.gowoobro.snippet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.BookRecommendDto
import com.gowoobro.snippet.core.model.CategoryStatsDto
import com.gowoobro.snippet.core.model.MonthlyStatsDto
import com.gowoobro.snippet.core.model.ReadingGoalDto
import com.gowoobro.snippet.core.model.ReadingGoalUpdateRequest
import com.gowoobro.snippet.core.model.ReadingInsightsDto
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.core.model.StreakDto
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.core.model.YearlyStatsDto
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.getOrNull
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // 선택 월
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    // 해당 월 책 목록 (통계 탭)
    val monthlyBooks: List<UserBookDto> = emptyList(),
    // 통계 상세 — 선택 년도 (월별/카테고리/인사이트 조회 기준)
    val statsYear: Int = LocalDate.now().year,
    // 독서 목표
    val readingGoal: ReadingGoalDto = ReadingGoalDto.empty(LocalDate.now().year),
    // 스트릭
    val streak: StreakDto = StreakDto(),
    // 최근 세션
    val recentSessions: List<ReadingSessionDto> = emptyList(),
    // 통계 상세 — 월별(현재 년)
    val monthlyStats: List<MonthlyStatsDto> = emptyList(),
    // 통계 상세 — 연도별
    val yearlyStats: List<YearlyStatsDto> = emptyList(),
    // 통계 상세 — 카테고리
    val categoryStats: List<CategoryStatsDto> = emptyList(),
    // 통계 상세 — 인사이트
    val insights: ReadingInsightsDto? = null,
    // 진행 탭 책
    val progressBooks: List<UserBookDto> = emptyList(),
    // 서재 탭 책
    val libraryBooks: List<UserBookDto> = emptyList(),
    // 서재 검색어
    val libraryQuery: String = "",
    // 추천 도서
    val recommendedBooks: List<BookRecommendDto> = emptyList(),
)

val DashboardUiState.filteredLibraryBooks: List<UserBookDto>
    get() = if (libraryQuery.isBlank()) libraryBooks
    else libraryBooks.filter { book ->
        book.title.contains(libraryQuery, ignoreCase = true) ||
            book.author.contains(libraryQuery, ignoreCase = true)
    }

/**
 * 대시보드 탭(통계/진행/서재) 공용 ViewModel.
 * Factory 패턴: AppContainer 수동 DI.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadAll()
        // 상세/서재 탭에서 책 상태·진도가 바뀌면 대시보드 목록·통계도 조용히 갱신
        viewModelScope.launch {
            container.userBookChangedEvents.collect { onUserBookChanged(it) }
        }
    }

    private var bookChangeRefreshJob: Job? = null

    /**
     * 다른 화면에서 책이 바뀌었을 때: 서재 목록은 서버 응답으로 즉시 교체하고,
     * 진행/월별 목록과 완독 통계는 멤버십이 바뀔 수 있어 다시 조회한다.
     * isLoading을 켜지 않아 복귀 시 스켈레톤이 번쩍이지 않는다.
     * (상태 변경 → 별점 저장처럼 연달아 오면 직전 조회를 취소하고 마지막 것만 반영)
     */
    private fun onUserBookChanged(book: UserBookDto) {
        _uiState.update { state ->
            state.copy(libraryBooks = state.libraryBooks.map { if (it.id == book.id) book else it })
        }
        bookChangeRefreshJob?.cancel()
        bookChangeRefreshJob = viewModelScope.launch {
            val state = _uiState.value
            val now = LocalDate.now()
            val monthlyBooksDeferred = async {
                safeApiCall { container.userBookApi.getMonthly(state.selectedYear, state.selectedMonth) }.getOrNull()
            }
            val progressDeferred = async {
                safeApiCall { container.userBookApi.getProgress() }.getOrNull()
            }
            val goalDeferred = async {
                safeApiCall { container.readingGoalApi.get(now.year) }.getOrNull()
            }
            val monthlyStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getMonthly(state.statsYear) }.getOrNull()
            }
            val yearlyStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getYearly() }.getOrNull()
            }
            val categoryStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getCategory(state.statsYear) }.getOrNull()
            }
            val insightsDeferred = async {
                safeApiCall { container.userBookStatsApi.getInsights(state.statsYear) }.getOrNull()
            }
            // 실패한 항목은 기존 값을 유지한다 (빈 목록으로 덮어쓰지 않음)
            val monthlyBooks = monthlyBooksDeferred.await()
            val progress = progressDeferred.await()
            val goal = goalDeferred.await()
            val monthlyStats = monthlyStatsDeferred.await()
            val yearlyStats = yearlyStatsDeferred.await()
            val categoryStats = categoryStatsDeferred.await()
            val insights = insightsDeferred.await()
            _uiState.update {
                it.copy(
                    monthlyBooks = monthlyBooks ?: it.monthlyBooks,
                    progressBooks = progress ?: it.progressBooks,
                    readingGoal = goal ?: it.readingGoal,
                    monthlyStats = monthlyStats ?: it.monthlyStats,
                    yearlyStats = yearlyStats ?: it.yearlyStats,
                    categoryStats = categoryStats ?: it.categoryStats,
                    insights = insights ?: it.insights,
                )
            }
        }
    }

    /** 전체 데이터 병렬 로드 */
    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val state = _uiState.value
            val now = LocalDate.now()

            val monthlyBooksDeferred = async {
                safeApiCall {
                    container.userBookApi.getMonthly(state.selectedYear, state.selectedMonth)
                }.getOrDefault(emptyList())
            }
            val goalDeferred = async {
                safeApiCall { container.readingGoalApi.get(now.year) }
                    .getOrDefault(ReadingGoalDto.empty(now.year))
            }
            val streakDeferred = async {
                safeApiCall { container.readingSessionApi.getStreak() }
                    .getOrDefault(StreakDto())
            }
            val recentSessionsDeferred = async {
                safeApiCall { container.readingSessionApi.getAll() }
                    .getOrDefault(emptyList<ReadingSessionDto>())
                    .take(5)
            }
            val monthlyStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getMonthly(state.statsYear) }
                    .getOrDefault(emptyList())
            }
            val yearlyStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getYearly() }
                    .getOrDefault(emptyList())
            }
            val categoryStatsDeferred = async {
                safeApiCall { container.userBookStatsApi.getCategory(state.statsYear) }
                    .getOrDefault(emptyList())
            }
            val insightsDeferred = async {
                safeApiCall { container.userBookStatsApi.getInsights(state.statsYear) }
                    .getOrNull()
            }
            val progressDeferred = async {
                safeApiCall { container.userBookApi.getProgress() }
                    .getOrDefault(emptyList())
            }
            val libraryDeferred = async {
                safeApiCall { container.userBookApi.getPaged(0, LIBRARY_PAGE_SIZE) }
                    .getOrDefault(emptyList())
            }
            val recommendDeferred = async {
                safeApiCall { container.bookApi.getRecommend() }
                    .getOrDefault(emptyList())
            }

            val libraryBooks = libraryDeferred.await()
            libraryFullyLoaded = libraryBooks.size < LIBRARY_PAGE_SIZE

            _uiState.update {
                it.copy(
                    isLoading = false,
                    monthlyBooks = monthlyBooksDeferred.await(),
                    readingGoal = goalDeferred.await(),
                    streak = streakDeferred.await(),
                    recentSessions = recentSessionsDeferred.await(),
                    monthlyStats = monthlyStatsDeferred.await(),
                    yearlyStats = yearlyStatsDeferred.await(),
                    categoryStats = categoryStatsDeferred.await(),
                    insights = insightsDeferred.await(),
                    progressBooks = progressDeferred.await(),
                    libraryBooks = libraryBooks,
                    recommendedBooks = recommendDeferred.await(),
                )
            }
        }
    }

    // 월/연도 이동 연타 시 늦게 도착한 이전 요청 응답이 현재 화면을 덮어쓰지 않도록
    // 직전 Job을 취소한다 (safeApiCall이 CancellationException을 재던져야 유효).
    private var monthJob: Job? = null
    private var statsYearJob: Job? = null

    /** 선택 월 변경 → 해당 월 책 재조회 */
    fun changeMonth(year: Int, month: Int) {
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        // 연도가 바뀌면 연도 스코프 통계(월별 차트/카테고리/인사이트)도 함께 갱신해야 한다.
        // (그대로 두면 대시보드의 월별 완독 차트·카테고리 도넛이 이전 연도 데이터로 남는다)
        if (year != _uiState.value.statsYear) changeStatsYear(year)
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            val result = safeApiCall { container.userBookApi.getMonthly(year, month) }
            _uiState.update { it.copy(monthlyBooks = result.getOrDefault(emptyList())) }
        }
    }

    fun prevMonth() {
        val state = _uiState.value
        val date = LocalDate.of(state.selectedYear, state.selectedMonth, 1).minusMonths(1)
        changeMonth(date.year, date.monthValue)
    }

    fun nextMonth() {
        val state = _uiState.value
        val now = LocalDate.now()
        val date = LocalDate.of(state.selectedYear, state.selectedMonth, 1).plusMonths(1)
        if (!date.isAfter(LocalDate.of(now.year, now.monthValue, 1))) {
            changeMonth(date.year, date.monthValue)
        }
    }

    /** 통계 상세 년도 변경 → 해당 년도 월별/카테고리/인사이트 재조회 */
    fun changeStatsYear(year: Int) {
        if (year == _uiState.value.statsYear) return
        _uiState.update { it.copy(statsYear = year) }
        statsYearJob?.cancel()
        statsYearJob = viewModelScope.launch {
            val monthlyDeferred = async {
                safeApiCall { container.userBookStatsApi.getMonthly(year) }
                    .getOrDefault(emptyList())
            }
            val categoryDeferred = async {
                safeApiCall { container.userBookStatsApi.getCategory(year) }
                    .getOrDefault(emptyList())
            }
            val insightsDeferred = async {
                safeApiCall { container.userBookStatsApi.getInsights(year) }
                    .getOrNull()
            }
            _uiState.update {
                it.copy(
                    monthlyStats = monthlyDeferred.await(),
                    categoryStats = categoryDeferred.await(),
                    insights = insightsDeferred.await(),
                )
            }
        }
    }

    /** 독서 목표 업데이트 */
    fun updateGoal(targetBooks: Int) {
        viewModelScope.launch {
            val result = safeApiCall {
                container.readingGoalApi.update(
                    ReadingGoalUpdateRequest(
                        year = LocalDate.now().year,
                        targetBooks = targetBooks,
                    ),
                )
            }
            if (result is AppResult.Success) {
                _uiState.update { it.copy(readingGoal = result.data) }
            }
        }
    }

    fun setLibraryQuery(query: String) {
        _uiState.update { it.copy(libraryQuery = query) }
        ensureLibraryFullyLoadedForSearch(query)
    }

    // ---- 서재 탭 검색용 전량 로드 ----

    /** 서재 탭이 전량 로드됐는지 — 검색 필터는 전량 위에서만 정확하다. */
    private var libraryFullyLoaded = false
    private var libraryLoadAllJob: Job? = null

    /**
     * 검색어 입력 시 남은 페이지를 전부 로드한다.
     * 첫 페이지에만 필터가 걸리면 뒤 페이지의 책이 "검색 결과 없음"으로 나오기 때문.
     */
    private fun ensureLibraryFullyLoadedForSearch(query: String) {
        if (query.isBlank() || libraryFullyLoaded) return
        if (libraryLoadAllJob?.isActive == true) return
        libraryLoadAllJob = viewModelScope.launch {
            var page = 1
            while (true) {
                // 에러 시 중단 — 다음 검색어 입력에서 재시도된다.
                val books = safeApiCall { container.userBookApi.getPaged(page, LIBRARY_PAGE_SIZE) }
                    .getOrNull() ?: return@launch
                _uiState.update { state ->
                    // 새로고침과 겹쳐도 중복 행이 생기지 않도록 id 기준 dedupe
                    val known = state.libraryBooks.map { it.id }.toSet()
                    state.copy(libraryBooks = state.libraryBooks + books.filter { it.id !in known })
                }
                if (books.size < LIBRARY_PAGE_SIZE) {
                    libraryFullyLoaded = true
                    return@launch
                }
                page++
            }
        }
    }

    fun refresh() = loadAll()

    // ---- Factory ----

    companion object {
        private const val LIBRARY_PAGE_SIZE = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(container) as T
            }
    }
}
