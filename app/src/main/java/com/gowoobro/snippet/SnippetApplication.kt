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
        // 자동 로그인 체크 (로컬 토큰 + 프로필 확인, 서버 호출 없음)
        container.applicationScope.launch {
            container.authManager.checkAuth()
        }
    }
}
