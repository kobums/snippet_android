package com.gowoobro.snippet.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.gowoobro.snippet.MainActivity
import com.gowoobro.snippet.core.data.AuthState
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.BookType
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.core.network.getOrDefault
import com.gowoobro.snippet.core.network.safeApiCall
import com.gowoobro.snippet.ui.auth.LoginScreen
import com.gowoobro.snippet.ui.auth.RegisterScreen
import com.gowoobro.snippet.ui.auth.SplashScreen
import com.gowoobro.snippet.ui.navigation.SnippetTab
import com.gowoobro.snippet.ui.dashboard.DashboardTabScreen
import com.gowoobro.snippet.ui.dashboard.ReadingCalendarScreen
import com.gowoobro.snippet.ui.dashboard.StatsDetailScreen
import com.gowoobro.snippet.ui.library.BookDetailScreen
import com.gowoobro.snippet.ui.library.BookSearchScreen
import com.gowoobro.snippet.ui.library.LibraryTabScreen
import com.gowoobro.snippet.ui.library.PopularBooksScreen
import com.gowoobro.snippet.ui.profile.ProfileScreen
import com.gowoobro.snippet.ui.profile.SuggestionScreen
import com.gowoobro.snippet.reading.ReadingTimerService
import com.gowoobro.snippet.reading.TimerState
import com.gowoobro.snippet.ui.reading.ReadingTimerScreen
import java.time.LocalDate
import com.gowoobro.snippet.ui.reading.SessionCompleteScreen
import com.gowoobro.snippet.ui.records.AddRecordScreen
import com.gowoobro.snippet.ui.records.RecordsTabScreen
import com.gowoobro.snippet.ui.snippet.SnippetTabScreen

/**
 * 화면 간 UserBookDto 전달을 위한 간단한 in-memory 홀더.
 * NavController가 복잡한 객체를 route 파라미터로 직렬화하지 않아도 되도록 사용.
 */
object NavHolder {
    var pendingBook: UserBookDto? = null
}

/**
 * 앱 루트 — authState에 따라 스플래시 / 인증 / 메인 탭으로 분기.
 * (MainActivity에서 호출)
 */
@Composable
fun SnippetApp() {
    val context = LocalContext.current
    val authManager = context.appContainer.authManager
    val authState by authManager.authState.collectAsStateWithLifecycle()

    when (authState) {
        is AuthState.Unknown -> SplashScreen()
        is AuthState.LoggedOut -> AuthFlow()
        is AuthState.LoggedIn -> MainShell()
    }
}

/** 로그인 ↔ 회원가입 내비게이션 (로그아웃 상태) */
@Composable
private fun AuthFlow() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onNavigateToRegister = { navController.navigate("register") })
        }
        composable("register") {
            RegisterScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** 5탭 메인 셸 (로그인 상태) */
@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 진행 중이던 독서 세션 복구 제안 — 앱 재실행 시 영속 스냅샷이 있고 타이머가 Idle이면 "이어 읽기" 안내
    LaunchedEffect(Unit) {
        if (ReadingTimerService.state.value !is TimerState.Idle) return@LaunchedEffect
        val snap = context.appContainer.activeSessionStore.peek() ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "이전 독서 세션이 있습니다. 이어서 읽을까요?",
            actionLabel = "이어 읽기",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            val encodedTitle = Uri.encode(snap.bookTitle.ifBlank { "독서 중" })
            navController.navigate(
                "readingTimer/${snap.userBookId}/$encodedTitle/${snap.startPage}?recover=true",
            )
        }
    }

    // 푸시 딥링크 라우트 소비 → 해당 탭으로 전환 후 홀더 초기화
    // (LoggedIn 상태에서만 MainShell이 컴포즈되므로 여기서 안전하게 처리)
    val deepLinkRoute by MainActivity.deepLinkRoute.collectAsStateWithLifecycle()
    LaunchedEffect(deepLinkRoute) {
        val tab = SnippetTab.fromRoute(deepLinkRoute)
        if (tab != null) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        // 알 수 없는 라우트도 포함해, 소비 후 재발화 방지를 위해 초기화
        if (deepLinkRoute != null) {
            MainActivity.clearDeepLinkRoute()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                SnippetTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        // 스니펫/서재 탭은 엣지-투-엣지(상태바/탭바 뒤까지 콘텐츠) 디자인이라
        // NavHost 전체가 아닌 각 라우트에서 innerPadding을 개별 적용한다.
        NavHost(
            navController = navController,
            startDestination = SnippetTab.Snippet.route,
        ) {
            SnippetTab.entries.forEach { tab ->
                composable(tab.route) { tabEntry ->
                    val isEdgeToEdge =
                        tab == SnippetTab.Snippet || tab == SnippetTab.Library ||
                            tab == SnippetTab.Dashboard || tab == SnippetTab.Records
                    Box(
                        modifier = if (isEdgeToEdge) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxSize().padding(innerPadding)
                        },
                    ) {
                        when (tab) {
                            SnippetTab.Snippet -> SnippetTabScreen(
                                bottomOverlayPadding = innerPadding.calculateBottomPadding(),
                            )
                            SnippetTab.Dashboard -> DashboardTabScreen(
                                onNavigateToCalendar = { year, month ->
                                    navController.navigate("readingCalendar/$year/$month")
                                },
                                onNavigateToStats = { navController.navigate("statsDetail") },
                                onNavigateToBookSearch = { query ->
                                    navController.navigate("bookSearch/${BookType.WISH.name}?query=$query")
                                },
                                onNavigateToBookDetail = { book ->
                                    NavHolder.pendingBook = book
                                    navController.navigate("bookDetail")
                                },
                                bottomOverlayPadding = innerPadding.calculateBottomPadding(),
                            )
                            SnippetTab.Records -> {
                                // 기록 추가 화면 복귀 시 목록 새로고침: savedStateHandle 결과를 카운터로 사용
                                val recordsRefresh by tabEntry.savedStateHandle
                                    .getStateFlow("recordAdded", 0)
                                    .collectAsStateWithLifecycle()
                                RecordsTabScreen(
                                    onNavigateToAddRecord = { type ->
                                        navController.navigate("addRecord/${type.name}")
                                    },
                                    refreshSignal = recordsRefresh,
                                    bottomOverlayPadding = innerPadding.calculateBottomPadding(),
                                )
                            }
                            SnippetTab.Library -> LibraryTabScreen(
                                onNavigateToBookDetail = { book ->
                                    NavHolder.pendingBook = book
                                    navController.navigate("bookDetail")
                                },
                                onNavigateToBookSearch = { type, query ->
                                    navController.navigate("bookSearch/${type.name}?query=$query")
                                },
                                onNavigateToPopularBooks = {
                                    navController.navigate("popularBooks")
                                },
                                bottomOverlayPadding = innerPadding.calculateBottomPadding(),
                            )
                            SnippetTab.Profile -> ProfileScreen(
                                onNavigateToSuggestion = { navController.navigate("suggestion") },
                            )
                        }
                    }
                }
            }
            composable("suggestion") {
                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    SuggestionScreen(
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = "bookSearch/{bookType}?query={query}",
                arguments = listOf(
                    navArgument("bookType") { defaultValue = BookType.HAVE.name },
                    navArgument("query") { defaultValue = "" },
                ),
            ) { entry ->
                val bookType = entry.arguments?.getString("bookType")
                    ?.let { runCatching { BookType.valueOf(it) }.getOrNull() }
                    ?: BookType.HAVE
                val query = entry.arguments?.getString("query").orEmpty()
                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    BookSearchScreen(
                        initialBookType = bookType,
                        initialQuery = query,
                        onBack = { navController.popBackStack() },
                        onBookAdded = { navController.popBackStack() },
                    )
                }
            }
            composable("popularBooks") {
                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    PopularBooksScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = "readingCalendar/{year}/{month}",
                arguments = listOf(
                    navArgument("year") { type = NavType.IntType },
                    navArgument("month") { type = NavType.IntType },
                ),
            ) { entry ->
                val year = entry.arguments?.getInt("year") ?: LocalDate.now().year
                val month = entry.arguments?.getInt("month") ?: LocalDate.now().monthValue
                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    ReadingCalendarScreen(
                        initialYear = year,
                        initialMonth = month,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("statsDetail") {
                // 통계 상세도 엣지-투-엣지 (플로팅 바가 상태바 인셋을 직접 처리)
                Box(Modifier.fillMaxSize()) {
                    StatsDetailScreen(
                        onBack = { navController.popBackStack() },
                        bottomOverlayPadding = innerPadding.calculateBottomPadding(),
                    )
                }
            }
            composable("bookDetail") { detailEntry ->
                val book = NavHolder.pendingBook
                if (book != null) {
                    // 기록 추가 화면 복귀 시 책별 기록 새로고침: savedStateHandle 결과를 카운터로 사용
                    val detailRefresh by detailEntry.savedStateHandle
                        .getStateFlow("recordAdded", 0)
                        .collectAsStateWithLifecycle()
                    // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                        BookDetailScreen(
                            userBook = book,
                            onBack = { navController.popBackStack() },
                            onBookDeleted = { navController.popBackStack() },
                            onNavigateToTimer = { userBookId, bookTitle, startPage ->
                                navController.navigate("readingTimer/$userBookId/$bookTitle/$startPage")
                            },
                            onNavigateToAddRecord = { type, bookId ->
                                navController.navigate("addRecord/${type.name}?bookId=$bookId")
                            },
                            refreshSignal = detailRefresh,
                        )
                    }
                }
            }
            composable(
                route = "addRecord/{type}?bookId={bookId}",
                arguments = listOf(
                    navArgument("type") { defaultValue = RecordType.SNIPPET.name },
                    navArgument("bookId") { defaultValue = "" },
                ),
            ) { entry ->
                val type = entry.arguments?.getString("type")
                    ?.let { runCatching { RecordType.valueOf(it) }.getOrNull() }
                    ?: RecordType.SNIPPET
                val bookId = entry.arguments?.getString("bookId")?.toLongOrNull()
                val container = LocalContext.current.appContainer

                // 서재 책 목록 로드 (bookId가 있으면 해당 책 1권으로 제한 → 드롭다운 없이 고정)
                val books by produceState(initialValue = emptyList<UserBookDto>(), bookId) {
                    val all = safeApiCall { container.userBookApi.getAll() }
                        .getOrDefault(emptyList())
                    value = if (bookId != null) all.filter { it.bookId == bookId } else all
                }

                // 저장 완료 후 이전 화면(기록 탭/책 상세)에 새로고침 신호 전달
                val notifySaved: () -> Unit = {
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle["recordAdded"] = (handle.get<Int>("recordAdded") ?: 0) + 1
                    }
                }

                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    AddRecordScreen(
                        books = books,
                        initialType = type,
                        onBack = { saved ->
                            if (saved) notifySaved()
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(
                route = "readingTimer/{userBookId}/{bookTitle}/{startPage}?recover={recover}",
                arguments = listOf(
                    navArgument("recover") { defaultValue = "false" },
                ),
            ) { backStackEntry ->
                val userBookId = backStackEntry.arguments?.getString("userBookId")?.toLongOrNull() ?: 0L
                val bookTitle = backStackEntry.arguments?.getString("bookTitle") ?: ""
                val startPage = backStackEntry.arguments?.getString("startPage")?.toIntOrNull() ?: 0
                val recover = backStackEntry.arguments?.getString("recover") == "true"
                // 타이머 화면은 자체 TopAppBar가 없어 전체 innerPadding 유지
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    ReadingTimerScreen(
                        userBookId = userBookId,
                        bookTitle = bookTitle,
                        startPage = startPage,
                        recover = recover,
                        onFinish = {
                            navController.navigate("sessionComplete") {
                                popUpTo("readingTimer/$userBookId/$bookTitle/$startPage?recover=$recover") {
                                    inclusive = true
                                }
                            }
                        },
                        onAbandon = { navController.popBackStack() },
                    )
                }
            }
            composable("sessionComplete") {
                // 화면 자체 Scaffold(TopAppBar)가 상태바 인셋을 처리하므로 상단 이중 여백 방지 —
                // 하단(NavigationBar 높이)만 보정한다
                Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                    SessionCompleteScreen(
                        onDone = {
                            navController.popBackStack("bookDetail", inclusive = false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(label)
    }
}
