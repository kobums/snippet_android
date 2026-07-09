package com.gowoobro.snippet.core.di

import android.content.Context
import android.os.Build
import com.gowoobro.snippet.BuildConfig
import com.gowoobro.snippet.SnippetApplication
import com.gowoobro.snippet.core.data.AuthManager
import com.gowoobro.snippet.core.datastore.ActiveSessionStore
import com.gowoobro.snippet.core.datastore.SettingsStore
import com.gowoobro.snippet.core.datastore.TokenStore
import com.gowoobro.snippet.core.datastore.snippetDataStore
import com.gowoobro.snippet.core.network.AuthInterceptor
import com.gowoobro.snippet.core.network.TokenAuthenticator
import com.gowoobro.snippet.core.network.api.AuthApi
import com.gowoobro.snippet.core.network.api.BookApi
import com.gowoobro.snippet.core.network.api.OcrApi
import com.gowoobro.snippet.core.network.api.ReadingGoalApi
import com.gowoobro.snippet.core.network.api.ReadingSessionApi
import com.gowoobro.snippet.core.network.api.RecordApi
import com.gowoobro.snippet.core.network.api.SnippetApi
import com.gowoobro.snippet.core.network.api.SuggestionApi
import com.gowoobro.snippet.core.network.api.UserApi
import com.gowoobro.snippet.core.network.api.UserBookApi
import com.gowoobro.snippet.core.network.api.UserBookStatsApi
import com.gowoobro.snippet.fcm.FcmTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * 수동 DI 컨테이너 (Hilt 미사용). [SnippetApplication]이 보유한다.
 *
 * 네트워크 구성 (02-data-api.md §1.1, §2):
 * - baseUrl: BuildConfig.BASE_URL (debug: http://10.0.1.29:8008/api/, release: https://snippetapi.gowoobro.com/api/)
 * - connectTimeout 5초
 * - refresh 호출은 인증 인터셉터/Authenticator가 없는 별도 Retrofit으로 (무한루프 방지)
 */
class AppContainer(context: Context) {

    /**
     * 실제 API base URL.
     * 에뮬레이터의 디버그 빌드에서는 개발 머신 LAN IP 대신 호스트 별칭(10.0.2.2)으로 치환한다
     * (에뮬레이터 NAT에서는 LAN IP로 호스트에 접근할 수 없음). 실기기 디버그는 LAN IP 유지.
     */
    private val apiBaseUrl: String = run {
        val isEmulator = Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish")
        if (BuildConfig.DEBUG && isEmulator) {
            BuildConfig.BASE_URL.replace(Regex("//[0-9.]+:"), "//10.0.2.2:")
        } else {
            BuildConfig.BASE_URL
        }
    }

    /** 앱 수명 코루틴 스코프 */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * JSON 규약:
     * - ignoreUnknownKeys: 서버 필드 추가에 안전
     * - coerceInputValues: null/미지 enum 값 → 기본값 폴백 ("" 폴백, RecordType.SNIPPET 폴백)
     * - explicitNulls=false + encodeDefaults=false: PATCH 부분 업데이트에서 null 필드 생략
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    // ----- DataStore -----

    private val dataStore = context.applicationContext.snippetDataStore
    val tokenStore = TokenStore(dataStore)
    val settingsStore = SettingsStore(dataStore, json)
    val activeSessionStore = ActiveSessionStore(dataStore)

    // ----- 세션 만료 이벤트 (refresh 실패 → 강제 로그아웃 브로드캐스트, §9.3-6) -----

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    // ----- 네트워킹 -----

    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    private val baseOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
            }
        }
        .build()

    /**
     * refresh 전용 OkHttpClient — 인증 클라이언트와 Dispatcher/커넥션 풀을 공유하면 안 된다.
     * Authenticator 안에서 같은 Dispatcher로 서브 요청(refresh)을 보내면, 401을 받고 대기 중인
     * 원요청들이 host당 동시 요청 한도(maxRequestsPerHost=5)를 이미 점유하고 있어
     * refresh가 큐에서 영원히 대기하는 데드락이 발생한다 (동시 요청 5개 이상일 때 100% 재현).
     */
    private val refreshOkHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .dispatcher(Dispatcher())
        .connectionPool(ConnectionPool())
        .build()

    /** 인증 인터셉터 없는 Retrofit — /auth/refresh 전용 */
    private val plainRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(apiBaseUrl)
        .client(refreshOkHttpClient)
        .addConverterFactory(converterFactory)
        .build()

    private val refreshAuthApi: AuthApi = plainRetrofit.create()

    private val authenticatedOkHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(
            TokenAuthenticator(
                tokenStore = tokenStore,
                refreshApi = { refreshAuthApi },
                onSessionExpired = { _sessionExpiredEvents.tryEmit(Unit) },
            ),
        )
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(apiBaseUrl)
        .client(authenticatedOkHttpClient)
        .addConverterFactory(converterFactory)
        .build()

    // ----- API -----

    val authApi: AuthApi = retrofit.create()
    val snippetApi: SnippetApi = retrofit.create()
    val userBookApi: UserBookApi = retrofit.create()
    val userBookStatsApi: UserBookStatsApi = retrofit.create()
    val recordApi: RecordApi = retrofit.create()
    val bookApi: BookApi = retrofit.create()
    val readingSessionApi: ReadingSessionApi = retrofit.create()
    val readingGoalApi: ReadingGoalApi = retrofit.create()
    val suggestionApi: SuggestionApi = retrofit.create()
    val ocrApi: OcrApi = retrofit.create()
    val userApi: UserApi = retrofit.create()

    // ----- 세션 -----

    val authManager = AuthManager(
        authApi = authApi,
        tokenStore = tokenStore,
        settingsStore = settingsStore,
        externalScope = applicationScope,
        sessionExpiredEvents = sessionExpiredEvents,
    )

    /** FCM 토큰 관리 — 로그인 성공 후 [FcmTokenManager.registerCurrentToken] 호출 */
    val fcmTokenManager = FcmTokenManager(
        userApi = userApi,
        scope = applicationScope,
    )
}

/** 화면/ViewModel에서 컨테이너 접근용 헬퍼 */
val Context.appContainer: AppContainer
    get() = (applicationContext as SnippetApplication).container
