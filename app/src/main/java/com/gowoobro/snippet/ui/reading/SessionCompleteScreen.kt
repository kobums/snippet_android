package com.gowoobro.snippet.ui.reading

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.reading.TimerState
import com.gowoobro.snippet.ui.share.ShareCardRenderer
import com.gowoobro.snippet.ui.share.ShareSheet
import com.gowoobro.snippet.ui.theme.StatusSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 세션 완료 화면 — 2단계 구성.
 *
 * Phase 1 (Completing): 종료 페이지 입력 → "기록 저장" 버튼 → API 호출
 * Phase 2 (Done): 요약 통계 카드 (소요시간 / 읽은 페이지 / 페이스) + 완료 버튼
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCompleteScreen(
    onDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: ReadingTimerViewModel = viewModel(
        factory = ReadingTimerViewModel.factory(context.appContainer),
    )
    val timerState by vm.timerState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // 취소 확인 다이얼로그
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("기록 취소") },
            text = { Text("이 독서 세션의 기록을 취소하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        vm.reset()
                        onDone()
                    },
                ) { Text("취소", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("계속") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("독서 완료") },
                navigationIcon = {
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "닫기")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = timerState) {
                is TimerState.Completing -> {
                    CompletingPhase(
                        state = s,
                        isSaving = isSaving,
                        onSave = { endPage ->
                            isSaving = true
                            vm.saveSession(
                                endPage = endPage,
                                onSuccess = { isSaving = false },
                                onError = { msg ->
                                    isSaving = false
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                            )
                        },
                    )
                }
                is TimerState.Done -> {
                    DonePhase(
                        state = s,
                        onDone = {
                            vm.reset()
                            onDone()
                        },
                    )
                }
                else -> {
                    // 상태 불일치 방어 (이미 Idle 등)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // 저장 중 풀스크린 오버레이
            if (isSaving) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CompletingPhase(
    state: TimerState.Completing,
    isSaving: Boolean,
    onSave: (Int) -> Unit,
) {
    var endPageText by remember { mutableStateOf("") }
    var endPageError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.bookTitle,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        // 독서 시간 박스
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = formatElapsed(state.elapsedSeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.W200,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "어디까지 읽으셨나요?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "시작 페이지: ${state.startPage}p",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 종료 페이지 입력
        OutlinedTextField(
            value = endPageText,
            onValueChange = {
                endPageText = it
                endPageError = null
            },
            label = { Text("종료 페이지") },
            suffix = { Text("페이지") },
            singleLine = true,
            isError = endPageError != null,
            supportingText = endPageError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
            ),
        )

        // 실시간 프리뷰
        val endPage = endPageText.toIntOrNull()
        if (endPage != null && endPage > state.startPage) {
            val pagesRead = endPage - state.startPage
            val paceMin = if (state.elapsedSeconds > 0 && pagesRead > 0) {
                state.elapsedSeconds.toDouble() / 60.0 / pagesRead
            } else 0.0
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        "읽은 페이지 ${pagesRead}p",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (paceMin > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            "페이스 ${"%.1f".format(paceMin)} min/p",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val page = endPageText.toIntOrNull()
                when {
                    page == null || page < 0 -> endPageError = "올바른 페이지를 입력해주세요"
                    page < state.startPage -> endPageError = "시작 페이지(${state.startPage}p)보다 커야 합니다"
                    else -> onSave(page)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
        ) {
            Text("기록 저장")
        }
    }
}

@Composable
private fun DonePhase(
    state: TimerState.Done,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    val pagesRead = (state.endPage - state.startPage).coerceAtLeast(0)
    val paceMin = if (state.elapsedSeconds > 0 && pagesRead > 0) {
        state.elapsedSeconds.toDouble() / 60.0 / pagesRead
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // 완료 체크 아이콘 (이모지 🎉 대체)
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = StatusSuccess,
            modifier = Modifier.size(48.dp),
        )

        Text(
            text = "독서 완료!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = state.bookTitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // 통계 카드 3열
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "독서 시간",
                value = formatElapsedShort(state.elapsedSeconds),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "읽은 페이지",
                value = "${pagesRead}p",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "페이스",
                value = if (paceMin > 0) "${"%.1f".format(paceMin)}m/p" else "-",
                modifier = Modifier.weight(1f),
            )
        }

        // 페이지 진행 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("페이지 진행", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${state.startPage}p",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${state.endPage}p",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "+${pagesRead}p 읽음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // SNS 공유 버튼
        OutlinedButton(
            onClick = {
                if (isSharing) return@OutlinedButton
                isSharing = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        ShareCardRenderer.renderSessionCard(
                            context = context,
                            bookTitle = state.bookTitle,
                            elapsedSeconds = state.elapsedSeconds,
                            pagesRead = pagesRead,
                            paceMinPerPage = paceMin,
                        )
                    }
                    ShareSheet.shareImage(context, uri)
                    isSharing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSharing,
        ) {
            if (isSharing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text("공유하기")
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("완료")
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatElapsedShort(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}시간 ${m}분"
        else -> "${m}분"
    }
}
