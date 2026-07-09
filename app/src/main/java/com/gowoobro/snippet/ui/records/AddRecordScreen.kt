package com.gowoobro.snippet.ui.records

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.RecordType
import com.gowoobro.snippet.core.model.UserBookDto
import com.gowoobro.snippet.ui.components.BookHeader
import com.gowoobro.snippet.ui.components.RatingStars
import com.gowoobro.snippet.ui.ocr.OcrResultScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * 기록 추가 화면.
 *
 * @param books 선택 가능한 책 목록 (없으면 빈 리스트 → 에러 안내)
 * @param initialType 기본 선택 타입
 * @param initialText OCR 등에서 프리필된 텍스트
 * @param onBack 저장 완료 또는 취소 시 뒤로가기 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    books: List<UserBookDto> = emptyList(),
    initialType: RecordType = RecordType.SNIPPET,
    initialText: String = "",
    onBack: (saved: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: RecordsViewModel = viewModel(
        factory = RecordsViewModel.factory(context.appContainer),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 폼 상태
    var selectedType by remember { mutableStateOf(initialType) }
    var selectedBook by remember { mutableStateOf(books.firstOrNull()) }
    var text by remember { mutableStateOf(initialText) }
    var tag by remember { mutableStateOf("") }
    var pageStr by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var bookDropdownExpanded by remember { mutableStateOf(false) }

    // OCR 플로우 상태
    // null = AddRecord 폼 표시, non-null = OcrResultScreen 표시
    var ocrImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // 카메라 촬영용 임시 파일 Uri (TakePicture 결과 저장 위치)
    var cameraImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // 카메라/갤러리 선택 다이얼로그 표시 여부
    var showCameraSourceDialog by remember { mutableStateOf(false) }

    // Photo Picker 런처 (갤러리 선택, READ_MEDIA 권한 불필요)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            ocrImageUri = uri
        }
    }

    // 카메라 촬영 런처 (TakePicture → FileProvider Uri)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            ocrImageUri = cameraImageUri
        }
    }

    // 카메라 권한 요청 런처
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            scope.launch { snackbarHostState.showSnackbar("카메라 권한이 필요합니다") }
        }
    }

    val typeLabelOf: (RecordType) -> String = { type ->
        when (type) {
            RecordType.SNIPPET -> "스니펫"
            RecordType.DIARY -> "독서일기"
            RecordType.REVIEW -> "리뷰"
        }
    }

    // OCR 결과 화면이 활성화된 경우: OcrResultScreen을 표시
    val currentOcrUri = ocrImageUri
    if (currentOcrUri != null) {
        OcrResultScreen(
            imageUri = currentOcrUri,
            onBack = {
                // 재촬영 → OCR 화면 닫고 폼으로 복귀
                ocrImageUri = null
            },
            onTextConfirmed = { recognizedText ->
                // 인식된 텍스트를 본문 필드에 삽입 후 OCR 화면 닫기
                text = recognizedText
                ocrImageUri = null
            },
        )
        return
    }

    // 카메라/갤러리 선택 다이얼로그
    if (showCameraSourceDialog) {
        AlertDialog(
            onDismissRequest = { showCameraSourceDialog = false },
            title = { Text("사진 가져오기") },
            text = { Text("카메라로 촬영하거나 갤러리에서 선택하세요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraSourceDialog = false
                        // 카메라 권한 요청 후 촬영
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Text(" 카메라 촬영")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraSourceDialog = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Photo, contentDescription = null)
                    Text(" 갤러리 선택")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${typeLabelOf(selectedType)} 추가") },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    } else {
                        IconButton(
                            onClick = {
                                val book = selectedBook
                                if (book == null) {
                                    scope.launch { snackbarHostState.showSnackbar("책을 먼저 선택해주세요") }
                                    return@IconButton
                                }
                                if (text.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("내용을 입력해주세요") }
                                    return@IconButton
                                }
                                vm.addRecord(
                                    bookId = book.bookId,
                                    type = selectedType,
                                    text = text.trim(),
                                    tag = tag.trim().takeIf { it.isNotBlank() },
                                    relatedPage = pageStr.toIntOrNull(),
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("기록이 저장되었습니다")
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
            // 책 헤더 (선택된 책)
            selectedBook?.let { book ->
                BookHeader(
                    title = book.title,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    badge = typeLabelOf(selectedType),
                )
            } ?: run {
                if (books.isNotEmpty()) {
                    Text(
                        text = "책을 선택해주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "기록할 책이 없습니다. 먼저 서재에 책을 추가해주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 책 변경 드롭다운 (책이 여러 권일 때)
            if (books.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = bookDropdownExpanded,
                    onExpandedChange = { bookDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedBook?.title ?: "책 선택",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("책 변경") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = bookDropdownExpanded,
                        onDismissRequest = { bookDropdownExpanded = false },
                    ) {
                        books.forEach { book ->
                            DropdownMenuItem(
                                text = { Text(book.title) },
                                onClick = {
                                    selectedBook = book
                                    bookDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }

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

            // OCR/카메라 버튼 — 카메라 촬영 또는 갤러리 선택 다이얼로그 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        // 카메라 직접 촬영
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("카메라 촬영")
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Photo,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("갤러리 선택")
                }
            }

            // 내용 입력
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("내용") },
                placeholder = { Text("기록할 내용을 입력하세요") },
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 카메라 촬영 결과를 저장할 임시 파일 Uri를 생성한다.
 * FileProvider를 통해 카메라 앱에 안전하게 공유 가능한 Uri 반환.
 */
private fun createCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr_images").also { it.mkdirs() }
    val file = File.createTempFile("ocr_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
