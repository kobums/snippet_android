package com.gowoobro.snippet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gowoobro.snippet.core.data.AuthState
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.AppThemeMode
import com.gowoobro.snippet.ui.SnippetApp
import com.gowoobro.snippet.ui.theme.SnippetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /** 푸시 탭 인텐트에 실리는 라우트 키 (값: snippet/dashboard/records/library/profile) */
        const val EXTRA_DEEP_LINK_ROUTE = "deep_link_route"

        /**
         * 푸시 탭으로 전달된 딥링크 라우트를 SnippetApp에 노출하는 홀더.
         * 인텐트 도착 시 emit, MainShell이 소비 후 [clearDeepLinkRoute]로 null 리셋.
         */
        private val _deepLinkRoute = MutableStateFlow<String?>(null)
        val deepLinkRoute: StateFlow<String?> = _deepLinkRoute.asStateFlow()

        fun clearDeepLinkRoute() {
            _deepLinkRoute.value = null
        }
    }

    // Android 13+ POST_NOTIFICATIONS 런타임 권한 요청
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // 권한 허용 후 현재 토큰을 서버에 등록
                appContainer.fcmTokenManager.registerCurrentToken()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 콜드 스타트로 푸시 탭이 열린 경우 라우트 추출
        handleDeepLinkIntent(intent)

        // 로그인 성공 시 FCM 토큰 등록 트리거
        lifecycleScope.launch {
            appContainer.authManager.authState
                .map { it is AuthState.LoggedIn }
                .distinctUntilChanged()
                .collect { isLoggedIn ->
                    if (isLoggedIn) {
                        requestNotificationPermissionIfNeeded()
                        appContainer.fcmTokenManager.registerCurrentToken()
                    }
                }
        }

        setContent {
            val themeMode by appContainer.settingsStore.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }
            SnippetTheme(darkTheme = darkTheme) {
                SnippetApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 앱 시작 + 백그라운드 복귀 시마다 버전 정책 확인 (게이트 내부에서 중복 요청 방지)
        appContainer.appVersionGate.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop이므로 앱이 떠 있는 상태에서 푸시 탭 시 여기로 들어온다.
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /** 인텐트에서 딥링크 라우트를 읽어 홀더에 emit. */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_DEEP_LINK_ROUTE)
        if (!route.isNullOrBlank()) {
            _deepLinkRoute.value = route
        }
    }

    /**
     * Android 13(API 33)+ 에서 POST_NOTIFICATIONS 런타임 권한 요청.
     * 이미 허용된 경우 즉시 리턴.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
