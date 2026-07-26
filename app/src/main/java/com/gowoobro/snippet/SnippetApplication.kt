package com.gowoobro.snippet

import android.app.Application
import com.gowoobro.snippet.core.di.AppContainer
import kotlinx.coroutines.launch

class SnippetApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            // 레거시 Flutter 앱(1.0.17)에서 업데이트된 사용자의 세션 이관 (1회)
            container.flutterTokenMigrator.migrateIfNeeded()
            // 자동 로그인 체크 (로컬 토큰 + 프로필 확인, 서버 호출 없음)
            container.authManager.checkAuth()
        }
    }
}
