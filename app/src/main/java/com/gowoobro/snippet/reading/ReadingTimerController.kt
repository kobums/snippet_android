package com.gowoobro.snippet.reading

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gowoobro.snippet.MainActivity
import com.gowoobro.snippet.core.datastore.ActiveSessionStore
import com.gowoobro.snippet.core.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 독서 세션 타이머 컨트롤러.
 *
 * 포그라운드 서비스를 쓰지 않는다. 경과 시간이 wall-clock epoch 차이로 계산되므로
 * (elapsed = baseElapsed + (now - startEpoch)) 시간을 세기 위해 살아있어야 하는 프로세스가
 * 없기 때문이다. 진행 상태는 [ActiveSessionStore] 스냅샷에만 의존하고, 알림의 초 단위 카운트업은
 * 시스템 크로노미터([NotificationCompat.Builder.setUsesChronometer])가 대신 굴린다.
 *
 * FGS를 쓰지 않으므로 `FOREGROUND_SERVICE_SPECIAL_USE` 선언과 Play Console 시연 영상 심사,
 * Android 15의 FGS 실행 시간 제한이 모두 해당되지 않는다.
 *
 * 상태는 [state]로 전역 노출한다 (UI 생명주기와 무관).
 */
object ReadingTimerController {

    /** 복구 시 인정하는 최대 공백(앱 장기 종료 방어). 24시간. */
    private const val MAX_RECOVER_GAP_SEC = 24L * 60 * 60

    private const val NOTIFICATION_ID = 300
    const val CHANNEL_ID = "reading_session"

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** 영속 I/O 전용 스코프 (세션 스냅샷 저장/삭제) */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateState(newState: TimerState) {
        _state.value = newState
    }

    // ─── 상태 전환 ───────────────────────────────────────────────

    fun start(context: Context, userBookId: Long, bookTitle: String, startPage: Int) {
        val app = context.applicationContext
        val nowEpoch = nowSec()

        _state.value = TimerState.Running(
            bookTitle = bookTitle,
            userBookId = userBookId,
            startPage = startPage,
            startEpoch = nowEpoch,
            baseElapsed = 0L,
        )
        persist(app, userBookId, bookTitle, startPage, startEpoch = nowEpoch, baseElapsed = 0L, paused = false)
        notify(app, bookTitle, elapsed = 0L, paused = false)
    }

    fun pause(context: Context) {
        val app = context.applicationContext
        val s = _state.value as? TimerState.Running ?: return
        val elapsed = s.elapsedNow()

        _state.value = TimerState.Paused(
            bookTitle = s.bookTitle,
            userBookId = s.userBookId,
            startPage = s.startPage,
            elapsedSeconds = elapsed,
        )
        // 일시정지 스냅샷: baseElapsed=elapsed, paused=true (startEpoch는 의미 없음)
        persist(app, s.userBookId, s.bookTitle, s.startPage, startEpoch = 0L, baseElapsed = elapsed, paused = true)
        notify(app, s.bookTitle, elapsed, paused = true)
    }

    fun resume(context: Context) {
        val app = context.applicationContext
        val s = _state.value as? TimerState.Paused ?: return
        val nowEpoch = nowSec()

        _state.value = TimerState.Running(
            bookTitle = s.bookTitle,
            userBookId = s.userBookId,
            startPage = s.startPage,
            startEpoch = nowEpoch,
            baseElapsed = s.elapsedSeconds,
        )
        persist(app, s.userBookId, s.bookTitle, s.startPage, startEpoch = nowEpoch, baseElapsed = s.elapsedSeconds, paused = false)
        notify(app, s.bookTitle, s.elapsedSeconds, paused = false)
    }

    /** 타이머 중단 → 종료 페이지 입력 대기(Completing). 알림은 내린다. */
    fun finish(context: Context) {
        val app = context.applicationContext
        val s = _state.value
        val elapsed = when (s) {
            is TimerState.Running -> s.elapsedNow()
            is TimerState.Paused -> s.elapsedSeconds
            else -> return
        }
        val (userBookId, bookTitle, startPage) = when (s) {
            is TimerState.Running -> Triple(s.userBookId, s.bookTitle, s.startPage)
            is TimerState.Paused -> Triple(s.userBookId, s.bookTitle, s.startPage)
            else -> return
        }

        _state.value = TimerState.Completing(
            bookTitle = bookTitle,
            userBookId = userBookId,
            startPage = startPage,
            elapsedSeconds = elapsed,
        )
        // 완료 입력 화면에서 앱이 종료돼도 복구할 수 있도록 일시정지 스냅샷 유지(저장 성공 시 VM이 clear).
        persist(app, userBookId, bookTitle, startPage, startEpoch = 0L, baseElapsed = elapsed, paused = true)
        cancelNotification(app)
    }

    fun abandon(context: Context) {
        val app = context.applicationContext
        _state.value = TimerState.Idle
        scope.launch { app.appContainer.activeSessionStore.clear() }
        cancelNotification(app)
    }

    /**
     * 영속된 세션 복구 — running이면 공백(앱 종료 시간)을 누적에 더해 이어서 카운트,
     * paused면 정지 상태로 복원.
     */
    fun recover(context: Context) {
        val app = context.applicationContext
        scope.launch { hydrateFromSnapshot(app) }
    }

    /**
     * 알림 액션 처리 진입점.
     *
     * 프로세스가 죽었다 브로드캐스트로 깨어난 경우 [state]는 Idle이라 곧바로 전이하면
     * 조작이 유실된다. 스냅샷을 먼저 복원한 뒤 액션을 적용한다.
     */
    fun onNotificationAction(context: Context, action: String, onComplete: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (_state.value is TimerState.Idle) hydrateFromSnapshot(app)
                when (action) {
                    ReadingTimerActionReceiver.ACTION_PAUSE -> pause(app)
                    ReadingTimerActionReceiver.ACTION_RESUME -> resume(app)
                    ReadingTimerActionReceiver.ACTION_FINISH -> finish(app)
                }
            } finally {
                onComplete()
            }
        }
    }

    private suspend fun hydrateFromSnapshot(app: Context) {
        val snap = app.appContainer.activeSessionStore.peek() ?: return
        if (snap.paused) {
            _state.value = TimerState.Paused(
                bookTitle = snap.bookTitle,
                userBookId = snap.userBookId,
                startPage = snap.startPage,
                elapsedSeconds = snap.baseElapsed,
            )
            notify(app, snap.bookTitle, snap.baseElapsed, paused = true)
        } else {
            val nowEpoch = nowSec()
            val gap = (nowEpoch - snap.startEpoch).coerceIn(0L, MAX_RECOVER_GAP_SEC)
            val elapsed = snap.baseElapsed + gap
            // 공백을 누적으로 고정하고 startEpoch을 현재로 재설정 → 이후 표시가 튀지 않음
            persist(app, snap.userBookId, snap.bookTitle, snap.startPage, startEpoch = nowEpoch, baseElapsed = elapsed, paused = false)
            _state.value = TimerState.Running(
                bookTitle = snap.bookTitle,
                userBookId = snap.userBookId,
                startPage = snap.startPage,
                startEpoch = nowEpoch,
                baseElapsed = elapsed,
            )
            notify(app, snap.bookTitle, elapsed, paused = false)
        }
    }

    // ─── 영속 ────────────────────────────────────────────────────

    private fun persist(
        context: Context,
        userBookId: Long,
        bookTitle: String,
        startPage: Int,
        startEpoch: Long,
        baseElapsed: Long,
        paused: Boolean,
    ) {
        scope.launch {
            context.appContainer.activeSessionStore.save(
                ActiveSessionStore.Snapshot(
                    userBookId = userBookId,
                    bookTitle = bookTitle,
                    startPage = startPage,
                    startEpoch = startEpoch,
                    baseElapsed = baseElapsed,
                    paused = paused,
                ),
            )
        }
    }

    // ─── 알림 ───────────────────────────────────────────────────

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "독서 세션",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "독서 중 타이머가 실행됩니다"
            setShowBadge(false)
        }
        notificationManager(context).createNotificationChannel(channel)
    }

    /**
     * 알림 게시/갱신.
     *
     * 알림 권한(Android 13+)이 거부돼 있으면 조용히 무시된다 — 경과 시간은 스냅샷에서
     * 계산되므로 알림 없이도 세션은 정상 동작한다.
     */
    private fun notify(context: Context, bookTitle: String, elapsed: Long, paused: Boolean) {
        ensureChannel(context)
        notificationManager(context).notify(NOTIFICATION_ID, buildNotification(context, bookTitle, elapsed, paused))
    }

    private fun cancelNotification(context: Context) {
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(
        context: Context,
        bookTitle: String,
        elapsed: Long,
        paused: Boolean,
    ): Notification {
        val tapPi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("$bookTitle 읽는 중")
            .setContentIntent(tapPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "재개" else "일시정지",
                actionPi(context, if (paused) ReadingTimerActionReceiver.ACTION_RESUME else ReadingTimerActionReceiver.ACTION_PAUSE, 1),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "완료",
                actionPi(context, ReadingTimerActionReceiver.ACTION_FINISH, 2),
            )

        if (paused) {
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(formatElapsed(elapsed) + " (일시정지)")
        } else {
            // 시스템이 초를 굴려주므로 앱이 1초마다 깨어날 필요가 없다.
            // 기준 시각을 "현재 - 경과"로 두면 크로노미터가 누적 경과부터 이어서 센다.
            builder.setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - elapsed * 1000L)
        }

        return builder.build()
    }

    private fun actionPi(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReadingTimerActionReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ─── 유틸 ────────────────────────────────────────────────────

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L
}

/** 초를 HH:MM:SS로 포맷 */
internal fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** 타이머 상태 머신 */
sealed class TimerState {
    object Idle : TimerState()

    /**
     * 진행 중.
     *
     * 경과 시간을 필드로 들고 있지 않다 — wall-clock에서 파생되는 값이라 필드로 두면
     * 곧바로 낡는다. 필요할 때 [elapsedNow]로 계산한다.
     */
    data class Running(
        val bookTitle: String,
        val userBookId: Long,
        val startPage: Int,
        /** 현재 구간 시작 epoch(초) */
        val startEpoch: Long,
        /** 이전 구간 누적 경과(초) */
        val baseElapsed: Long,
    ) : TimerState() {
        fun elapsedNow(): Long = baseElapsed + (System.currentTimeMillis() / 1000L - startEpoch)
    }

    data class Paused(
        val bookTitle: String,
        val userBookId: Long,
        val startPage: Int,
        val elapsedSeconds: Long,
    ) : TimerState()

    /** 타이머 정지 후 종료 페이지 입력 대기 상태 */
    data class Completing(
        val bookTitle: String,
        val userBookId: Long,
        val startPage: Int,
        val elapsedSeconds: Long,
    ) : TimerState()

    /** API 저장 완료 */
    data class Done(
        val bookTitle: String,
        val elapsedSeconds: Long,
        val startPage: Int,
        val endPage: Int,
    ) : TimerState()
}
