package com.gowoobro.snippet.ui.reading

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.ReadingSessionAddRequest
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.safeApiCall
import com.gowoobro.snippet.reading.ReadingTimerService
import com.gowoobro.snippet.reading.TimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 독서 타이머 ViewModel.
 * Service 시작/제어를 담당하고, TimerState를 UI에 노출한다.
 * 완료 시 ReadingSessionApi로 세션 저장(POST /readingsessions).
 */
class ReadingTimerViewModel(
    private val container: AppContainer,
) : ViewModel() {

    /** 서비스의 전역 상태를 그대로 노출 */
    val timerState: StateFlow<TimerState> = ReadingTimerService.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingTimerService.state.value)

    // ─── Service 제어 ────────────────────────────────────────────

    fun startSession(context: Context, userBookId: Long, bookTitle: String, startPage: Int) {
        val intent = Intent(context, ReadingTimerService::class.java).apply {
            action = ReadingTimerService.ACTION_START
            putExtra(ReadingTimerService.EXTRA_USER_BOOK_ID, userBookId)
            putExtra(ReadingTimerService.EXTRA_BOOK_TITLE, bookTitle)
            putExtra(ReadingTimerService.EXTRA_START_PAGE, startPage)
        }
        context.startForegroundService(intent)
    }

    /** 프로세스 종료 후 영속된 세션 복구 — 서비스가 스토어에서 스냅샷을 읽어 재개한다. */
    fun recoverSession(context: Context) {
        val intent = Intent(context, ReadingTimerService::class.java).apply {
            action = ReadingTimerService.ACTION_RECOVER
        }
        context.startForegroundService(intent)
    }

    fun pauseSession(context: Context) {
        sendAction(context, ReadingTimerService.ACTION_PAUSE)
    }

    fun resumeSession(context: Context) {
        sendAction(context, ReadingTimerService.ACTION_RESUME)
    }

    /** 타이머 중단 — Service에 FINISH 전송 → Completing 상태로 전환 */
    fun prepareFinish(context: Context) {
        sendAction(context, ReadingTimerService.ACTION_FINISH)
    }

    fun abandonSession(context: Context) {
        sendAction(context, ReadingTimerService.ACTION_ABANDON)
    }

    private fun sendAction(context: Context, action: String) {
        val intent = Intent(context, ReadingTimerService::class.java).apply {
            this.action = action
        }
        context.startService(intent)
    }

    // ─── 세션 저장 ────────────────────────────────────────────────

    /**
     * 완료 시 API 저장.
     * @param endPage 종료 페이지 입력값
     * @param onSuccess 저장 성공 콜백
     * @param onError 저장 실패 콜백(에러 메시지)
     */
    fun saveSession(
        endPage: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val s = timerState.value as? TimerState.Completing ?: return
        viewModelScope.launch {
            val request = ReadingSessionAddRequest(
                userBookId = s.userBookId,
                durationSeconds = s.elapsedSeconds.toInt(),
                startPage = s.startPage,
                endPage = endPage,
                sessionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            )
            // POST 응답은 bare Long(id) → 별도 재조회 불필요 (완료 화면에서 summary만 표시)
            when (val result = safeApiCall { container.readingSessionApi.add(request) }) {
                is AppResult.Success -> {
                    // 저장 성공 → 복구용 스냅샷 제거
                    container.activeSessionStore.clear()
                    ReadingTimerService.updateState(
                        TimerState.Done(
                            bookTitle = s.bookTitle,
                            elapsedSeconds = s.elapsedSeconds,
                            startPage = s.startPage,
                            endPage = endPage,
                        ),
                    )
                    onSuccess()
                }
                is AppResult.Failure -> onError(result.error.message)
            }
        }
    }

    /** Done 상태 이후 완전 초기화 */
    fun reset() {
        ReadingTimerService.updateState(TimerState.Idle)
    }

    // ─── Factory ─────────────────────────────────────────────────

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReadingTimerViewModel(container) as T
            }
    }
}
