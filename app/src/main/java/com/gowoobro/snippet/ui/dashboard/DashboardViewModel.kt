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
                safeApiCall { container.userBookApi.getPaged(0, 20) }
                    .getOrDefault(emptyList())
            }
            val recommendDeferred = async {
                safeApiCall { container.bookApi.getRecommend() }
                    .getOrDefault(emptyList())
            }

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
                    libraryBooks = libraryDeferred.await(),
                    recommendedBooks = recommendDeferred.await(),
                )
            }
        }
    }

    /** 선택 월 변경 → 해당 월 책 재조회 */
    fun changeMonth(year: Int, month: Int) {
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        viewModelScope.launch {
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
        viewModelScope.launch {
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
    }

    fun refresh() = loadAll()

    // ---- Factory ----

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(container) as T
            }
    }
}
