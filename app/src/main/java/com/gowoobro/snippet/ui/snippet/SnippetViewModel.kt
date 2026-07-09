package com.gowoobro.snippet.ui.snippet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.model.ArchiveAddRequest
import com.gowoobro.snippet.core.model.Snippet
import com.gowoobro.snippet.core.model.SnippetArchive
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.getOrNull
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- 스와이프 탭 상태 ----

data class SwipeUiState(
    val cards: List<Snippet> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** -1=무제한, 0=오늘 소진 */
    val remainingToday: Int = -1,
    /** 방금 좋아요(archive)된 스니펫 — Reveal 연출 트리거용 */
    val revealSnippet: SnippetArchive? = null,
)

// ---- 보관함 탭 상태 ----

data class ArchiveUiState(
    val items: List<SnippetArchive> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 스니펫 탭(스와이프 + 보관함) 공용 ViewModel.
 * Factory 패턴: [AuthViewModel]과 동일하게 [AppContainer]를 받아 수동 DI.
 */
class SnippetViewModel(private val container: AppContainer) : ViewModel() {

    // ---- Swipe ----

    private val _swipeState = MutableStateFlow(SwipeUiState())
    val swipeState: StateFlow<SwipeUiState> = _swipeState.asStateFlow()

    // ---- Archive ----

    private val _archiveState = MutableStateFlow(ArchiveUiState())
    val archiveState: StateFlow<ArchiveUiState> = _archiveState.asStateFlow()

    init {
        fetchCards()
    }

    // ---- 카드 로드 ----

    fun fetchCards() {
        viewModelScope.launch {
            _swipeState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = safeApiCall { container.snippetApi.getCards(count = 10) }
            when (result) {
                is AppResult.Success -> _swipeState.update {
                    it.copy(
                        isLoading = false,
                        cards = result.data.cards,
                        remainingToday = result.data.remainingToday,
                    )
                }
                is AppResult.Failure -> _swipeState.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    // ---- 좋아요(오른쪽 스와이프) ----

    fun like(snippet: Snippet) {
        // 낙관적 카드 제거
        removeTopCard(snippet.id)

        viewModelScope.launch {
            val result = safeApiCall {
                container.snippetApi.addArchive(ArchiveAddRequest(snippet.id))
            }
            if (result is AppResult.Success) {
                // archive 목록 무효화 — 다음 보관함 탭 진입 시 재조회됨
                _archiveState.update { ArchiveUiState() }

                // Reveal 연출: 책 정보 조회(보관함 목록에서 찾거나 최신 보관함으로 대체)
                loadReveal(snippet)
            } else if (result is AppResult.Failure) {
                // 실패 시 카드 맨 앞에 복원 (롤백)
                _swipeState.update { state ->
                    state.copy(cards = listOf(snippet) + state.cards)
                }
            }
            checkAndRefillCards()
        }
    }

    // ---- 패스(왼쪽 스와이프) ----

    fun pass(snippet: Snippet) {
        removeTopCard(snippet.id)
        viewModelScope.launch {
            // pass는 비로그인에도 허용 (서버 no-op). 실패해도 롤백하지 않음
            safeApiCall { container.snippetApi.skip(snippet.id) }
            checkAndRefillCards()
        }
    }

    // ---- Reveal 닫기 ----

    fun dismissReveal() {
        _swipeState.update { it.copy(revealSnippet = null) }
    }

    // ---- 보관함 조회 ----

    fun fetchArchive() {
        if (_archiveState.value.isLoading) return
        viewModelScope.launch {
            _archiveState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = safeApiCall { container.snippetApi.getArchive() }
            _archiveState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isLoading = false, items = result.data)
                    is AppResult.Failure -> it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    // ---- 보관함 삭제 (스와이프) ----

    fun removeFromArchive(item: SnippetArchive) {
        // 낙관적 제거 — 실패 시 목록 복원
        val previous = _archiveState.value.items
        _archiveState.update { state ->
            state.copy(items = state.items.filterNot { it.id == item.id })
        }
        viewModelScope.launch {
            val result = safeApiCall { container.snippetApi.removeArchive(item.id) }
            if (result is AppResult.Failure) {
                _archiveState.update { it.copy(items = previous) }
            }
        }
    }

    // ---- private helpers ----

    private fun removeTopCard(id: Long) {
        _swipeState.update { state ->
            state.copy(cards = state.cards.filterNot { it.id == id })
        }
    }

    private suspend fun loadReveal(snippet: Snippet) {
        // 보관함 목록에서 방금 추가된 항목을 찾아 Reveal 상태로 설정
        val archiveResult = safeApiCall { container.snippetApi.getArchive() }
        val archiveList = archiveResult.getOrDefault(emptyList())
        val matched = archiveList.firstOrNull { it.id == snippet.id }
            ?: SnippetArchive(
                id = snippet.id,
                text = snippet.text,
                tag = snippet.tag,
                bookTitle = snippet.bookTitle ?: "",
            )
        _swipeState.update { it.copy(revealSnippet = matched) }
        // 아카이브 상태도 갱신
        _archiveState.update { ArchiveUiState(items = archiveList) }
    }

    private fun checkAndRefillCards() {
        val state = _swipeState.value
        if (state.cards.size < 3 && state.remainingToday != 0) {
            fetchCards()
        }
    }

    // ---- Factory ----

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SnippetViewModel(container) as T
            }
    }
}
