package com.gowoobro.snippet.ui.reading

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.reading.TimerState
import kotlinx.coroutines.delay

/**
 * 독서 세션 타이머 화면 (ActiveSessionScreen).
 *
 * - primary 배경 풀스크린
 * - 3→2→1 카운트다운 오버레이 후 서비스 시작
 * - 일시정지/재개, 독서 완료 버튼
 * - 뒤로가기 → 포기 확인 다이얼로그
 */
@Composable
fun ReadingTimerScreen(
    userBookId: Long,
    bookTitle: String,
    startPage: Int,
    onFinish: () -> Unit,       // Completing 상태 → SessionCompleteScreen으로
    onAbandon: () -> Unit = {},  // 포기 → 뒤로
    recover: Boolean = false,    // 복구 모드: 카운트다운 생략 + 영속 세션 재개
) {
    val context = LocalContext.current
    val vm: ReadingTimerViewModel = viewModel(
        factory = ReadingTimerViewModel.factory(context.appContainer),
    )
    val timerState by vm.timerState.collectAsStateWithLifecycle()

    // 알림 권한 요청 (Android 13+)
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 거부되어도 타이머는 동작 */ }

    // 카운트다운 상태 (3→2→1→0=완료). 복구 모드는 카운트다운 생략.
    var countdown by remember { mutableIntStateOf(if (recover) 0 else 3) }
    var countdownDone by remember { mutableStateOf(recover) }

    // 포기 확인 다이얼로그
    var showAbandonDialog by remember { mutableStateOf(false) }

    // 시작 / 복구
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (recover) {
            // 진행 중이던 세션을 그대로 재개 (서비스가 스토어 스냅샷에서 복원)
            vm.recoverSession(context)
            return@LaunchedEffect
        }
        delay(300)
        repeat(3) { i ->
            countdown = 3 - i
            delay(1000)
        }
        countdownDone = true
        countdown = 0
        vm.startSession(context, userBookId, bookTitle, startPage)
    }

    // Completing 상태로 전환되면 완료 화면으로
    LaunchedEffect(timerState) {
        if (timerState is TimerState.Completing) {
            onFinish()
        }
    }

    // 포기 확인 다이얼로그
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("독서 포기") },
            text = { Text("세션을 종료할까요? 기록이 저장되지 않습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbandonDialog = false
                        vm.abandonSession(context)
                        onAbandon()
                    },
                ) { Text("포기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonDialog = false }) { Text("계속 읽기") }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 메인 콘텐츠
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 상단 바: 책 제목 + 포기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { showAbandonDialog = true },
                    ) {
                        Text(
                            "포기",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 경과 시간 (대형)
                val elapsed = when (val s = timerState) {
                    is TimerState.Running -> s.elapsedSeconds
                    is TimerState.Paused -> s.elapsedSeconds
                    else -> 0L
                }
                val isPaused = timerState is TimerState.Paused
                val isRunning = timerState is TimerState.Running

                Text(
                    text = formatElapsed(elapsed),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.W200,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isPaused) 0.4f else 1f),
                    textAlign = TextAlign.Center,
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "${startPage}p 에서 시작",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(48.dp))

                // 일시정지 / 재개 원형 버튼
                if (countdownDone) {
                    IconButton(
                        onClick = {
                            if (isPaused) vm.resumeSession(context)
                            else if (isRunning) vm.pauseSession(context)
                        },
                        modifier = Modifier
                            .size(80.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(80.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (isPaused) "재개" else "일시정지",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 독서 완료 버튼
                    OutlinedButton(
                        onClick = { vm.prepareFinish(context) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        ),
                    ) {
                        Text("독서 완료")
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            // 카운트다운 오버레이
            if (!countdownDone && countdown > 0) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = countdown,
                            transitionSpec = {
                                (scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200)))
                                    .togetherWith(fadeOut(tween(150)))
                            },
                            label = "countdown",
                        ) { count ->
                            Text(
                                text = count.toString(),
                                fontSize = 120.sp,
                                fontWeight = FontWeight.W200,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
