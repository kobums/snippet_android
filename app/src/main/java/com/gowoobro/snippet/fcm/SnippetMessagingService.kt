package com.gowoobro.snippet.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gowoobro.snippet.MainActivity
import com.gowoobro.snippet.R
import com.gowoobro.snippet.core.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "SnippetMsgService"
private const val CHANNEL_ID = "snippet_push"
private const val CHANNEL_NAME = "Snippet 알림"
private const val CHANNEL_DESC = "Snippet 앱의 푸시 알림을 표시합니다"
private const val NOTIFICATION_ID = 1001

/**
 * FCM 메시지 수신 서비스.
 *
 * - [onNewToken]: 토큰 갱신 시 서버에 재등록
 * - [onMessageReceived]: 포그라운드 상태에서 알림 표시 (백그라운드는 OS가 자동 표시)
 *
 * google-services.json이 없으면 이 서비스는 시작되지 않으므로
 * 파일 없는 빌드에서도 부작용이 없습니다.
 */
class SnippetMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM 토큰 갱신: $token")
        // 갱신된 토큰을 서버에 등록 (로그인 상태가 아니면 safeApiCall 내부에서 401 처리됨)
        val tokenManager = FcmTokenManager(
            userApi = appContainer.userApi,
            scope = CoroutineScope(Dispatchers.IO),
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tokenManager.sendTokenToServer(token)
            } catch (e: Exception) {
                Log.w(TAG, "토큰 서버 등록 실패 (무시): ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM 메시지 수신: ${message.notification?.title}")
        val title = message.notification?.title ?: message.data["title"] ?: "Snippet"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        // 딥링크 라우팅: data["route"] (alias "tab") → 탭 전환 (iOS와 공유하는 계약)
        val route = message.data["route"] ?: message.data["tab"]
        showNotification(title, body, route)
    }

    // ----- 알림 표시 -----

    private fun showNotification(title: String, body: String, route: String?) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!route.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, route)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESC
        }
        notificationManager.createNotificationChannel(channel)
    }
}
