package com.gowoobro.snippet.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.ReadingSessionDto
import com.gowoobro.snippet.core.model.RecordAddRequest
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.core.model.RecordUpdateRequest
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 독서기록 탭 UI 상태 */
data class RecordsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // 선택 월
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    // 기록 목록 (selectedYear/selectedMonth 기준)
    val records: List<RecordDto> = emptyList(),
    // 현재 records가 어떤 타입으로 로드됐는지 — 탭 전환 시 이전 타입 목록이 남지 않게 한다
    val loadedType: RecordType? = null,
    // 전체 독서 세션 (책 제목별 그룹핑용)
    val sessions: List<ReadingSessionDto> = emptyList(),
    val isSessionsLoading: Boolean = false,
    // 저장/삭제 진행 중
    val isSaving: Boolean = false,
    val saveError: String? = null,
)

/** 기록 탭 타입 필터 */
enum class RecordFilter(val label: String, val type: RecordType?) {
    ALL("전체", null),
    SNIPPET("스니펫", RecordType.SNIPPET),
    DIARY("독서일기", RecordType.DIARY),
    REVIEW("리뷰", RecordType.REVIEW),
}

/** 선택 필터를 적용한 기록 목록 */
val RecordsUiState.filteredRecords: List<RecordDto>
    get() = records  // 필터링은 VM에서 type 파라미터로 처리

/** 책 제목별 그룹핑 */
fun List<RecordDto>.groupByBook(): Map<String, List<RecordDto>> =
    groupBy { it.bookTitle.ifBlank { "제목 없음" } }

fun List<ReadingSessionDto>.groupSessionsByBook(): Map<String, List<ReadingSessionDto>> =
    groupBy { it.bookTitle.ifBlank { "제목 없음" } }

/**
 * 독서기록 탭 ViewModel.
 * Factory 패턴: AppContainer 수동 DI.
 */
class RecordsViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    init {
        loadMonthlyRecords()
        loadSessions()
    }

    // --------------- 월별 기록 ---------------

    fun selectMonth(year: Int, month: Int, type: RecordType? = null) {
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadMonthlyRecords(year, month, type)
    }

    fun prevMonth() {
        val state = _uiState.value
        val date = LocalDate.of(state.selectedYear, state.selectedMonth, 1).minusMonths(1)
        selectMonth(date.year, date.monthValue)
    }

    fun nextMonth() {
        val state = _uiState.value
        val date = LocalDate.of(state.selectedYear, state.selectedMonth, 1).plusMonths(1)
        val now = LocalDate.now()
        if (!date.isAfter(now.withDayOfMonth(1))) {
            selectMonth(date.year, date.monthValue)
        }
    }

    // 월 이동 연타 시 늦게 도착한 이전 달 응답이 현재 달 화면을 덮어쓰지 않도록
    // 직전 Job을 취소한다 (safeApiCall이 CancellationException을 재던져야 유효).
    private var recordsJob: Job? = null

    fun loadMonthlyRecords(
        year: Int = _uiState.value.selectedYear,
        month: Int = _uiState.value.selectedMonth,
        type: RecordType? = null,
    ) {
        recordsJob?.cancel()
        recordsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    // 타입이 바뀌면 이전 타입 목록을 비워 중앙 스피너가 일관되게 보이도록 한다
                    records = if (type != it.loadedType) emptyList() else it.records,
                )
            }
            val result = safeApiCall {
                container.recordApi.getMonthly(year, month, type?.wire)
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    loadedType = type,
                    records = result.getOrDefault(state.records),
                    errorMessage = if (result is com.gowoobro.snippet.core.network.AppResult.Failure)
                        result.error.message else null,
                )
            }
        }
    }

    // --------------- 세션 목록 ---------------

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSessionsLoading = true) }
            val result = safeApiCall { container.readingSessionApi.getAll() }
            _uiState.update { state ->
                state.copy(
                    isSessionsLoading = false,
                    sessions = result.getOrDefault(state.sessions),
                )
            }
        }
    }

    // --------------- 기록 추가 ---------------

    fun addRecord(
        bookId: Long,
        type: RecordType,
        text: String,
        tag: String?,
        relatedPage: Int?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val addResult = safeApiCall {
                container.recordApi.add(
                    RecordAddRequest(
                        bookId = bookId,
                        type = type,
                        text = text,
                        tag = tag?.takeIf { it.isNotBlank() },
                        relatedPage = relatedPage,
                    ),
                )
            }
            when (addResult) {
                is com.gowoobro.snippet.core.network.AppResult.Success -> {
                    // POST는 bare Long → 재조회
                    loadMonthlyRecords()
                    _uiState.update { it.copy(isSaving = false) }
                    onSuccess()
                }
                is com.gowoobro.snippet.core.network.AppResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, saveError = addResult.error.message) }
                    onError(addResult.error.message)
                }
            }
        }
    }

    // --------------- 기록 수정 ---------------

    fun updateRecord(
        id: Long,
        type: RecordType?,
        text: String?,
        tag: String?,
        relatedPage: Int?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val result = safeApiCall {
                container.recordApi.update(
                    id,
                    RecordUpdateRequest(
                        type = type,
                        text = text,
                        tag = tag?.takeIf { it.isNotBlank() },
                        relatedPage = relatedPage,
                    ),
                )
            }
            when (result) {
                is com.gowoobro.snippet.core.network.AppResult.Success -> {
                    loadMonthlyRecords()
                    _uiState.update { it.copy(isSaving = false) }
                    onSuccess()
                }
                is com.gowoobro.snippet.core.network.AppResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, saveError = result.error.message) }
                    onError(result.error.message)
                }
            }
        }
    }

    // --------------- 기록 삭제 ---------------

    fun deleteRecord(
        id: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val result = safeApiCall { container.recordApi.delete(id) }
            when (result) {
                is com.gowoobro.snippet.core.network.AppResult.Success -> {
                    loadMonthlyRecords()
                    onSuccess()
                }
                is com.gowoobro.snippet.core.network.AppResult.Failure -> {
                    onError(result.error.message)
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null, saveError = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecordsViewModel(container) as T
            }
    }
}
