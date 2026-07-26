package com.gowoobro.snippet.reading

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 독서 세션 알림의 액션 버튼(일시정지/재개/완료) 수신기.
 *
 * 포그라운드 서비스를 없앤 뒤 알림 액션을 받을 진입점 역할을 한다.
 * 앱 프로세스가 죽어 있어도 브로드캐스트로 깨어나 상태를 전이하고 스냅샷을 갱신하므로,
 * 사용자가 알림에서 누른 조작이 유실되지 않는다.
 */
class ReadingTimerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAUSE = "com.gowoobro.snippet.reading.PAUSE"
        const val ACTION_RESUME = "com.gowoobro.snippet.reading.RESUME"
        const val ACTION_FINISH = "com.gowoobro.snippet.reading.FINISH"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_PAUSE && action != ACTION_RESUME && action != ACTION_FINISH) return

        // 스냅샷 복원이 DataStore I/O라 onReceive 반환 이후까지 이어진다 → goAsync로 수명 연장
        val pending = goAsync()
        ReadingTimerController.onNotificationAction(context, action) { pending.finish() }
    }
}
