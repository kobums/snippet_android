package com.gowoobro.snippet.fcm

import android.util.Log
import com.gowoobro.snippet.core.model.FcmTokenRequest
import com.gowoobro.snippet.core.network.safeApiCall
import com.gowoobro.snippet.core.network.api.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "FcmTokenManager"

/**
 * FCM 토큰 조회 및 서버 등록 담당.
 *
 * google-services.json이 없는 경우 FirebaseApp이 초기화되지 않아
 * [com.google.firebase.FirebaseApp] 관련 예외가 발생하므로 전 작업을 try-catch로 감쌉니다.
 * 파일을 app/ 폴더에 넣으면 자동으로 토큰 조회·등록이 활성화됩니다.
 *
 * 호출 시점:
 * - 로그인 성공 직후 ([registerCurrentToken] 호출)
 * - [SnippetMessagingService.onNewToken] 콜백 (토큰 갱신 시 자동)
 */
class FcmTokenManager(
    private val userApi: UserApi,
    private val scope: CoroutineScope,
) {

    /**
     * 현재 FCM 토큰을 조회해 서버에 등록합니다.
     * google-services.json 미존재 시 조용히 무시합니다.
     */
    fun registerCurrentToken() {
        scope.launch {
            try {
                val token = fetchToken()
                sendTokenToServer(token)
            } catch (e: Exception) {
                // FirebaseApp 미초기화(google-services.json 없음) 또는 네트워크 오류 — 무시
                Log.d(TAG, "FCM 토큰 조회 건너뜀: ${e.message}")
            }
        }
    }

    /**
     * 서버에 FCM 토큰을 전송합니다.
     * [SnippetMessagingService.onNewToken] 에서도 직접 호출됩니다.
     */
    suspend fun sendTokenToServer(token: String) {
        val result = safeApiCall { userApi.registerFcmToken(FcmTokenRequest(fcmToken = token)) }
        Log.d(TAG, "FCM 토큰 서버 등록 결과: $result")
    }

    private suspend fun fetchToken(): String = suspendCancellableCoroutine { cont ->
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> cont.resume(token) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
