package com.gowoobro.snippet.reading

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.gowoobro.snippet.MainActivity
import com.gowoobro.snippet.core.datastore.ActiveSessionStore
import com.gowoobro.snippet.core.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 독서 세션 타이머 포그라운드 서비스.
 *
 * 시간 계산 방식:
 * - wall-clock epoch 기반 — elapsed = baseElapsed + (now - startEpoch)
 * - 일시정지 시 baseElapsed에 현재 경과 저장, startEpoch 초기화
 * - 앱 킬/백그라운드 후 재시작해도 epoch 차이로 정확한 시간 복원
 *
 * 상태 노출: [TimerState] sealed class → companion object [state] (StateFlow)
 */
class ReadingTimerService : Service() {

    companion object {
        // Intent actions
        const val ACTION_START = "com.gowoobro.snippet.reading.START"
        const val ACTION_PAUSE = "com.gowoobro.snippet.reading.PAUSE"
        const val ACTION_RESUME = "com.gowoobro.snippet.reading.RESUME"
        const val ACTION_FINISH = "com.gowoobro.snippet.reading.FINISH"
        const val ACTION_ABANDON = "com.gowoobro.snippet.reading.ABANDON"

        /** 프로세스 종료 후 영속된 세션을 복구해 재개 (extras 불필요 — 스토어에서 읽음) */
        const val ACTION_RECOVER = "com.gowoobro.snippet.reading.RECOVER"

        /** 복구 시 인정하는 최대 공백(앱 장기 종료 방어). 24시간. */
        private const val MAX_RECOVER_GAP_SEC = 24L * 60 * 60

        // Intent extras
        const val EXTRA_BOOK_TITLE = "bookTitle"
        const val EXTRA_USER_BOOK_ID = "userBookId"
        const val EXTRA_START_PAGE = "startPage"

        private const val NOTIFICATION_ID = 300
        const val CHANNEL_ID = "reading_session"

        // 전역 상태 — UI가 Service 생명주기와 무관하게 구독
        private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun updateState(newState: TimerState) {
            _state.value = newState
        }
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /** 영속 I/O 전용 스코프 (세션 스냅샷 저장/삭제) */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSessionStore: ActiveSessionStore by lazy { applicationContext.appContainer.activeSessionStore }

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val s = _state.value
            if (s is TimerState.Running) {
                val elapsed = s.baseElapsed + (System.currentTimeMillis() / 1000L - s.startEpoch)
                _state.value = s.copy(elapsedSeconds = elapsed)
                updateNotification(s.bookTitle, elapsed)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_FINISH -> handleFinish()
            ACTION_ABANDON -> handleAbandon()
            ACTION_RECOVER -> handleRecover()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── 상태 전환 ───────────────────────────────────────────────

    private fun handleStart(intent: Intent) {
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: "독서 중"
        val userBookId = intent.getLongExtra(EXTRA_USER_BOOK_ID, 0L)
        val startPage = intent.getIntExtra(EXTRA_START_PAGE, 0)
        val nowEpoch = System.currentTimeMillis() / 1000L

        val newState = TimerState.Running(
            bookTitle = bookTitle,
            userBookId = userBookId,
            startPage = startPage,
            startEpoch = nowEpoch,
            baseElapsed = 0L,
            elapsedSeconds = 0L,
        )
        _state.value = newState
        persist(userBookId, bookTitle, startPage, startEpoch = nowEpoch, baseElapsed = 0L, paused = false)

        startForeground(NOTIFICATION_ID, buildNotification(bookTitle, 0L))
        handler.post(tickRunnable)
    }

    private fun handlePause() {
        val s = _state.value as? TimerState.Running ?: return
        handler.removeCallbacks(tickRunnable)
        val elapsed = s.baseElapsed + (System.currentTimeMillis() / 1000L - s.startEpoch)
        _state.value = TimerState.Paused(
            bookTitle = s.bookTitle,
            userBookId = s.userBookId,
            startPage = s.startPage,
            elapsedSeconds = elapsed,
        )
        // 일시정지 스냅샷: baseElapsed=elapsed, paused=true (startEpoch는 의미 없음)
        persist(s.userBookId, s.bookTitle, s.startPage, startEpoch = 0L, baseElapsed = elapsed, paused = true)
        updateNotification(s.bookTitle, elapsed, paused = true)
    }

    private fun handleResume() {
        val s = _state.value as? TimerState.Paused ?: return
        val nowEpoch = System.currentTimeMillis() / 1000L
        val newState = TimerState.Running(
            bookTitle = s.bookTitle,
            userBookId = s.userBookId,
            startPage = s.startPage,
            startEpoch = nowEpoch,
            baseElapsed = s.elapsedSeconds,
            elapsedSeconds = s.elapsedSeconds,
        )
        _state.value = newState
        persist(s.userBookId, s.bookTitle, s.startPage, startEpoch = nowEpoch, baseElapsed = s.elapsedSeconds, paused = false)
        updateNotification(s.bookTitle, s.elapsedSeconds)
        handler.post(tickRunnable)
    }

    private fun handleFinish() {
        handler.removeCallbacks(tickRunnable)
        // 상태를 Completing으로 변경 (UI가 SessionComplete 화면으로 이동)
        val s = _state.value
        val elapsed = when (s) {
            is TimerState.Running -> s.baseElapsed + (System.currentTimeMillis() / 1000L - s.startEpoch)
            is TimerState.Paused -> s.elapsedSeconds
            else -> 0L
        }
        val userBookId = when (s) {
            is TimerState.Running -> s.userBookId
            is TimerState.Paused -> s.userBookId
            else -> 0L
        }
        val bookTitle = when (s) {
            is TimerState.Running -> s.bookTitle
            is TimerState.Paused -> s.bookTitle
            else -> ""
        }
        val startPage = when (s) {
            is TimerState.Running -> s.startPage
            is TimerState.Paused -> s.startPage
            else -> 0
        }
        _state.value = TimerState.Completing(
            bookTitle = bookTitle,
            userBookId = userBookId,
            startPage = startPage,
            elapsedSeconds = elapsed,
        )
        // 완료 입력 화면에서 앱이 종료돼도 복구할 수 있도록 일시정지 스냅샷 유지(저장 성공 시 VM이 clear).
        persist(userBookId, bookTitle, startPage, startEpoch = 0L, baseElapsed = elapsed, paused = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleAbandon() {
        handler.removeCallbacks(tickRunnable)
        _state.value = TimerState.Idle
        clearPersistence()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 영속된 세션 복구 — running이면 공백(앱 종료 시간)을 누적에 더해 이어서 카운트, paused면 정지 상태로 복원. */
    private fun handleRecover() {
        serviceScope.launch {
            val snap = activeSessionStore.peek() ?: return@launch
            val nowEpoch = System.currentTimeMillis() / 1000L
            if (snap.paused) {
                val elapsed = snap.baseElapsed
                _state.value = TimerState.Paused(
                    bookTitle = snap.bookTitle,
                    userBookId = snap.userBookId,
                    startPage = snap.startPage,
                    elapsedSeconds = elapsed,
                )
                handler.post {
                    startForeground(NOTIFICATION_ID, buildNotification(snap.bookTitle, elapsed, paused = true))
                }
            } else {
                val gap = (nowEpoch - snap.startEpoch).coerceIn(0L, MAX_RECOVER_GAP_SEC)
                val elapsed = snap.baseElapsed + gap
                // 공백을 누적으로 고정하고 startEpoch을 현재로 재설정 → 이후 tick이 튀지 않음
                persist(snap.userBookId, snap.bookTitle, snap.startPage, startEpoch = nowEpoch, baseElapsed = elapsed, paused = false)
                _state.value = TimerState.Running(
                    bookTitle = snap.bookTitle,
                    userBookId = snap.userBookId,
                    startPage = snap.startPage,
                    startEpoch = nowEpoch,
                    baseElapsed = elapsed,
                    elapsedSeconds = elapsed,
                )
                handler.post {
                    startForeground(NOTIFICATION_ID, buildNotification(snap.bookTitle, elapsed))
                    handler.post(tickRunnable)
                }
            }
        }
    }

    // ─── 영속 ────────────────────────────────────────────────────

    private fun persist(
        userBookId: Long,
        bookTitle: String,
        startPage: Int,
        startEpoch: Long,
        baseElapsed: Long,
        paused: Boolean,
    ) {
        serviceScope.launch {
            activeSessionStore.save(
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

    private fun clearPersistence() {
        serviceScope.launch { activeSessionStore.clear() }
    }

    // ─── 알림 ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "독서 세션",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "독서 중 타이머가 실행됩니다"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(bookTitle: String, elapsed: Long, paused: Boolean = false): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseOrResumeIntent = Intent(this, ReadingTimerService::class.java).apply {
            action = if (paused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseOrResumePi = PendingIntent.getService(
            this, 1, pauseOrResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val finishIntent = Intent(this, ReadingTimerService::class.java).apply {
            action = ACTION_FINISH
        }
        val finishPi = PendingIntent.getService(
            this, 2, finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("$bookTitle 읽는 중")
            .setContentText(formatElapsed(elapsed) + if (paused) " (일시정지)" else "")
            .setContentIntent(tapPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "재개" else "일시정지",
                pauseOrResumePi,
            )
            .addAction(android.R.drawable.ic_media_next, "완료", finishPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(bookTitle: String, elapsed: Long, paused: Boolean = false) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(bookTitle, elapsed, paused))
    }

    // ─── 유틸 ────────────────────────────────────────────────────

    private fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}

/** 타이머 상태 머신 */
sealed class TimerState {
    object Idle : TimerState()

    data class Running(
        val bookTitle: String,
        val userBookId: Long,
        val startPage: Int,
        /** 현재 구간 시작 epoch(초) */
        val startEpoch: Long,
        /** 이전 구간 누적 경과(초) */
        val baseElapsed: Long,
        /** UI 표시용 현재 총 경과(초) */
        val elapsedSeconds: Long,
    ) : TimerState()

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
