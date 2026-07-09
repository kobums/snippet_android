package com.gowoobro.snippet.ui.snippet

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.ui.components.FloatingSubTabBar
import com.gowoobro.snippet.ui.widget.SnippetWidgetBridge

/** 플로팅 서브탭 바가 차지하는 대략 높이 (콘텐츠 상단 여백 계산용) */
private val FloatingBarHeight = 64.dp

/**
 * 스니펫 탭 루트 화면 — 스와이프 | 보관함 서브탭.
 * SnippetApp.kt MainShell의 SnippetTab.Snippet 라우트에 연결.
 *
 * iOS 확정 디자인:
 * - 상단 헤더/세그먼트 없이 콘텐츠가 화면 전체(상태바 뒤까지) 사용
 * - 상단에 캡슐 플로팅 [스와이프 | 보관함] 서브탭 바 오버레이
 * - 서브탭 전환은 Crossfade 직접 표시 (상태 유지)
 *
 * @param bottomOverlayPadding 하단 탭바(NavigationBar)가 콘텐츠를 가리는 높이 —
 *        리스트/컨트롤의 contentPadding으로 흘려보내 가림을 방지한다.
 */
@Composable
fun SnippetTabScreen(
    bottomOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val vm: SnippetViewModel = viewModel(
        factory = SnippetViewModel.factory(context.appContainer),
    )

    val swipeState by vm.swipeState.collectAsStateWithLifecycle()
    val archiveState by vm.archiveState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // 보관함 탭 최초 진입 시 데이터 로드
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && archiveState.items.isEmpty() && !archiveState.isLoading) {
            vm.fetchArchive()
        }
    }

    // 최상단 카드를 홈 위젯에 반영
    LaunchedEffect(swipeState.cards.firstOrNull()?.id) {
        swipeState.cards.firstOrNull()?.let { top ->
            SnippetWidgetBridge.update(context, top.text, top.tag)
        }
    }

    // Reveal 다이얼로그
    swipeState.revealSnippet?.let { archive ->
        SnippetRevealDialog(
            archive = archive,
            onDismiss = vm::dismissReveal,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 콘텐츠 시작 위치: 상태바 높이 + 플로팅 바 높이
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val contentTopPadding = statusBarPadding + FloatingBarHeight

        // 서브탭 전환: 선택 섹션 직접 표시 + Crossfade (iOS와 동일)
        Crossfade(
            targetState = selectedTab,
            label = "snippetSubTab",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            when (tab) {
                0 -> SnippetSwipeScreen(
                    state = swipeState,
                    onLike = vm::like,
                    onPass = vm::pass,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = contentTopPadding,
                        bottom = bottomOverlayPadding,
                    ),
                )
                else -> ArchiveScreen(
                    state = archiveState,
                    onRetry = vm::fetchArchive,
                    onRemove = vm::removeFromArchive,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = contentTopPadding + 8.dp,
                        bottom = bottomOverlayPadding + 16.dp,
                    ),
                )
            }
        }

        // 플로팅 서브탭 바 (상태바 아래 4dp)
        FloatingSubTabBar(
            tabs = listOf(0 to "스와이프", 1 to "보관함"),
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp),
        )
    }
}
