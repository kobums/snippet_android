package com.gowoobro.snippet.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.RecordDto
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.ui.components.BookHeader
import com.gowoobro.snippet.ui.components.RatingStars
import com.gowoobro.snippet.ui.share.NotesShareCardRenderer
import com.gowoobro.snippet.ui.share.ShareSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 기록 수정 화면.
 * AddRecordScreen 폼 재사용 — 책 헤더는 읽기 전용, AppBar 액션에 삭제 추가.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordScreen(
    record: RecordDto,
    onBack: (saved: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: RecordsViewModel = viewModel(
        factory = RecordsViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 폼 상태 (기존 레코드 값으로 초기화)
    var selectedType by remember { mutableStateOf(record.type) }
    var text by remember { mutableStateOf(record.text) }
    var tag by remember { mutableStateOf(record.tag ?: "") }
    var pageStr by remember { mutableStateOf(record.relatedPage?.toString() ?: "") }
    var rating by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val typeLabelOf: (RecordType) -> String = { type ->
        when (type) {
            RecordType.SNIPPET -> "스니펫"
            RecordType.DIARY -> "독서일기"
            RecordType.REVIEW -> "리뷰"
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("기록 삭제") },
            text = { Text("이 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteRecord(
                            id = record.id,
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("기록이 삭제되었습니다")
                                    onBack(true)
                                }
                            },
                            onError = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                        )
                    },
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${typeLabelOf(selectedType)} 수정") },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    } else {
                        // 삭제 버튼
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "삭제",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        // 저장 버튼
                        IconButton(
                            onClick = {
                                if (text.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("내용을 입력해주세요") }
                                    return@IconButton
                                }
                                vm.updateRecord(
                                    id = record.id,
                                    type = selectedType,
                                    text = text.trim(),
                                    tag = tag.trim().takeIf { it.isNotBlank() },
                                    relatedPage = pageStr.toIntOrNull(),
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("기록이 수정되었습니다")
                                            onBack(true)
                                        }
                                    },
                                    onError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    },
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "저장")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 책 헤더 — 읽기 전용
            BookHeader(
                title = record.bookTitle,
                author = record.bookAuthor.takeIf { it.isNotBlank() },
                coverUrl = record.bookCoverUrl.takeIf { it.isNotBlank() },
                badge = typeLabelOf(record.type),
            )

            // 기록 타입 선택
            Text("기록 유형", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(typeLabelOf(type)) },
                    )
                }
            }

            // 내용 입력
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("내용") },
                minLines = 8,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )

            // 태그 / 페이지 2열
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("태그") },
                    placeholder = { Text("선택 입력") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = pageStr,
                    onValueChange = { pageStr = it.filter { c -> c.isDigit() } },
                    label = { Text("페이지") },
                    placeholder = { Text("쪽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            // 리뷰 타입이면 별점 입력
            if (selectedType == RecordType.REVIEW) {
                Text("별점", style = MaterialTheme.typography.labelLarge)
                RatingStars(
                    rating = rating.toDouble(),
                    starSize = 32.dp,
                    onRatingChange = { newRating ->
                        rating = if (rating == newRating) 0 else newRating
                    },
                )
            }

            // ── 메모 이미지 내보내기 ─────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("메모 이미지 내보내기", style = MaterialTheme.typography.labelLarge)
            Text(
                "기록을 4:5 노트 카드 이미지로 만들어 공유합니다. 내용이 길면 여러 장으로 나뉩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
                onClick = {
                    if (isExporting) return@FilledTonalButton
                    if (text.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("내보낼 내용이 없습니다") }
                        return@FilledTonalButton
                    }
                    isExporting = true
                    scope.launch {
                        try {
                            // 화면에 보이는(편집 중) 본문/타입을 반영해 내보낸다.
                            val target = record.copy(text = text, type = selectedType)
                            val uris = withContext(Dispatchers.IO) {
                                NotesShareCardRenderer.renderPages(context, target, isDark)
                            }
                            ShareSheet.shareImages(context, uris)
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("내보내기 실패: ${e.message ?: "알 수 없는 오류"}")
                        } finally {
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting,
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Icon(Icons.Filled.IosShare, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  이미지로 내보내기")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
