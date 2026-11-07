package com.gowoobro.snippet.ui.ocr

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.gowoobro.snippet.core.di.AppContainer
import com.gowoobro.snippet.core.di.appContainer
import com.gowoobro.snippet.core.model.OcrEngine
import com.gowoobro.snippet.core.model.OcrRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * OCR 화면 (다중 밑줄 영역 선택 → 인식 → 결과 편집).
 *
 * 플로우:
 * - 영역 선택: 이미지 위를 드래그해 수평 밑줄을 그어 인식할 문장 영역들을 지정, 또는 "전체 인식"
 *   - 드래그 한 번 = 노란 수평 밑줄 1개 = OCR 영역 1개 (여러 개 가능)
 *   - 밑줄을 탭하면 선택(파란 테두리), 선택 삭제 / 전체 지우기 가능
 * - 인식: 각 영역을 ML Kit 한국어 OCR 후 위→아래로 이어 붙임 (또는 전체)
 * - 결과: 편집 가능 텍스트 + "영역 다시 선택" / "이 텍스트 사용"
 *
 * @param imageUri 인식할 이미지 Uri
 * @param onBack 뒤로가기 (재촬영)
 * @param onTextConfirmed 확정 텍스트를 호출자(AddRecordScreen)에 전달
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onTextConfirmed: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = context.appContainer

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 폴더블 접기·펴기/회전으로 Activity가 다시 만들어져도 단계·인식 결과를 유지한다 (비트맵은 Uri에서 다시 읽음)
    var phase by rememberSaveable(stateSaver = OcrPhaseSaver) { mutableStateOf<OcrPhase>(OcrPhase.Selecting) }
    var editableText by rememberSaveable { mutableStateOf("") }

    // 업라이트 Bitmap 로드 (EXIF 회전 반영)
    LaunchedEffect(imageUri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { TextRecognizer.loadUprightBitmap(context, imageUri) }.getOrNull()
        }
        if (loaded == null) {
            phase = OcrPhase.Error("이미지를 불러올 수 없습니다")
        } else {
            // 복원된 단계(인식 결과 편집 중 등)를 덮어쓰지 않는다 — 처음 진입 시 기본값이 이미 Selecting
            bitmap = loaded
        }
    }

    /** 전체 이미지 OCR (엔진 설정에 따라 온디바이스 또는 백엔드) */
    fun runOcrAll() {
        val src = bitmap ?: return
        phase = OcrPhase.Processing
        scope.launch {
            try {
                val backend = container.settingsStore.ocrEngine().backendEngine
                val text = if (backend == null) {
                    TextRecognizer.recognizeBitmap(src)
                } else {
                    recognizeOnServer(container, src, emptyList(), backend)
                }
                if (text.isBlank()) {
                    phase = OcrPhase.Error("선택한 영역에서 텍스트를 인식하지 못했습니다")
                } else {
                    editableText = text
                    phase = OcrPhase.Success
                }
            } catch (e: Exception) {
                phase = OcrPhase.Error(e.message ?: "텍스트를 인식할 수 없습니다")
            }
        }
    }

    /** 다중 영역(밑줄) OCR — 각 영역을 인식해 위→아래로 이어 붙임 */
    fun runOcrRegions(regions: List<AndroidRect>) {
        val src = bitmap ?: return
        if (regions.isEmpty()) return
        phase = OcrPhase.Processing
        scope.launch {
            try {
                val backend = container.settingsStore.ocrEngine().backendEngine
                val text = if (backend == null) {
                    TextRecognizer.recognizeRegions(src, regions)
                } else {
                    recognizeOnServer(container, src, regions, backend)
                }
                if (text.isBlank()) {
                    phase = OcrPhase.Error("선택한 영역에서 텍스트를 인식하지 못했습니다")
                } else {
                    editableText = text
                    phase = OcrPhase.Success
                }
            } catch (e: Exception) {
                phase = OcrPhase.Error(e.message ?: "텍스트를 인식할 수 없습니다")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (phase is OcrPhase.Selecting) "인식할 영역 선택" else "텍스트 인식") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = phase) {
                OcrPhase.Selecting -> {
                    val bmp = bitmap
                    if (bmp == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        UnderlineHighlighter(
                            bitmap = bmp,
                            onRecognizeRegions = { regions -> runOcrRegions(regions) },
                            onRecognizeAll = { runOcrAll() },
                        )
                    }
                }

                OcrPhase.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "텍스트를 인식하는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is OcrPhase.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onBack) { Text("재촬영") }
                            if (bitmap != null) {
                                Button(onClick = { phase = OcrPhase.Selecting }) { Text("영역 다시 선택") }
                            }
                        }
                    }
                }

                OcrPhase.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = "인식된 텍스트를 확인하고 필요하면 수정하세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }

                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { editableText = it },
                            label = { Text("추출된 텍스트") },
                            placeholder = { Text("인식된 텍스트가 여기에 표시됩니다") },
                            minLines = 6,
                            maxLines = 20,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { phase = OcrPhase.Selecting },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("영역 다시 선택")
                            }
                            Button(
                                onClick = { onTextConfirmed(editableText) },
                                enabled = editableText.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("이 텍스트 사용")
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 이미지 표시 영역 기준 정규화(0~1, 좌상단 원점) 좌표의 수평 밑줄.
 * 화면 크기가 바뀌어도(폴더블 접기·펴기, 회전, 창 크기 변경) 이미지 위 같은 줄에 붙어 있다.
 */
private data class Underline(
    val id: Long,
    val startX: Float,
    val endX: Float,
    val centerY: Float,
    val height: Float,
    val selected: Boolean = false,
) {
    /** 정규화 영역 사각형 (OCR 영역). */
    val normalizedRect: Rect
        get() = Rect(
            left = min(startX, endX),
            top = centerY - height / 2f,
            right = max(startX, endX),
            bottom = centerY + height / 2f,
        )

    /** 현재 이미지 표시 영역 [frame] 기준 화면 좌표 사각형 (렌더링·탭 판정). */
    fun rectIn(frame: Rect): Rect {
        val r = normalizedRect
        return Rect(
            left = frame.left + r.left * frame.width,
            top = frame.top + r.top * frame.height,
            right = frame.left + r.right * frame.width,
            bottom = frame.top + r.bottom * frame.height,
        )
    }

    companion object {
        /** 화면 좌표 드래그 결과(minX..maxX, 평균 Y, 높이)를 [frame] 기준 정규화 밑줄로 만든다. */
        fun fromDisplay(id: Long, minX: Float, maxX: Float, centerY: Float, height: Float, frame: Rect): Underline {
            val w = max(frame.width, 1f)
            val h = max(frame.height, 1f)
            return Underline(
                id = id,
                startX = (minX - frame.left) / w,
                endX = (maxX - frame.left) / w,
                centerY = (centerY - frame.top) / h,
                height = height / h,
            )
        }
    }
}

/** 정규화 밑줄 목록 저장 — 선택 상태는 버리고 좌표만 4개씩 납작하게 담는다. */
private val UnderlinesSaver = listSaver<SnapshotStateList<Underline>, Float>(
    save = { list -> list.flatMap { listOf(it.startX, it.endX, it.centerY, it.height) } },
    restore = { flat ->
        flat.chunked(4).mapIndexed { i, (sx, ex, cy, h) ->
            Underline(id = i + 1L, startX = sx, endX = ex, centerY = cy, height = h)
        }.toMutableStateList()
    },
)

/** ContentScale.Fit(레터박스)로 그려진 이미지의 실제 표시 영역. */
private fun imageFrame(canvasSize: Size, bitmap: Bitmap): Rect {
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    if (imgW <= 0f || imgH <= 0f || canvasSize.width <= 0f || canvasSize.height <= 0f) {
        return Rect(Offset.Zero, canvasSize)
    }
    val scale = min(canvasSize.width / imgW, canvasSize.height / imgH)
    val w = imgW * scale
    val h = imgH * scale
    val left = (canvasSize.width - w) / 2f
    val top = (canvasSize.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/**
 * 이미지 위 드래그로 수평 밑줄(=OCR 영역)을 여러 개 긋는 컴포저블.
 *
 * - 이미지는 비율 유지(letterbox)로 표시하고, 표시 영역(offset+scale)을 계산해
 *   디스플레이 좌표 ↔ 이미지 픽셀 좌표를 변환한다.
 * - 드래그: 경로 점들의 minX..maxX, 평균 Y, 높이 = clamp((maxY-minY)*3, 40, 80) (디스플레이 px).
 *   진행 중에는 미리보기(노란 사각형+굵은 선), 종료 시 밑줄 확정.
 * - 탭: 기존 밑줄 사각형 안을 탭하면 선택(파란 테두리). 밖을 탭하면 선택 해제.
 */
@Composable
private fun UnderlineHighlighter(
    bitmap: Bitmap,
    onRecognizeRegions: (List<AndroidRect>) -> Unit,
    onRecognizeAll: () -> Unit,
) {
    val highlightColor = Color(0xFFFFEB3B) // 노란색
    val selectColor = Color(0xFF2196F3) // 파란색

    // 정규화 좌표라 화면 크기가 바뀌어도 그대로 쓰고, Activity 재생성에도 살아남는다
    val underlines = rememberSaveable(saver = UnderlinesSaver) { mutableStateListOf<Underline>() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val frame = imageFrame(canvasSize, bitmap)

    // 드래그 진행 상태
    var dragMinX by remember { mutableStateOf(0f) }
    var dragMaxX by remember { mutableStateOf(0f) }
    var dragMinY by remember { mutableStateOf(0f) }
    var dragMaxY by remember { mutableStateOf(0f) }
    var dragSumY by remember { mutableStateOf(0f) }
    var dragCount by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(canvasSize) {
        // 드래그 중에 화면 크기가 바뀌면 이전 좌표계의 점들이라 진행 중 밑줄은 버린다
        dragging = false
        dragCount = 0
    }

    /** 진행 중 드래그의 미리보기 밑줄 (정규화 좌표). */
    fun previewUnderline(): Underline? {
        if (!dragging || dragCount < 2) return null
        val avgY = dragSumY / dragCount
        val h = ((dragMaxY - dragMinY) * 3f).coerceIn(40f, 80f)
        return Underline.fromDisplay(id = -1L, minX = dragMinX, maxX = dragMaxX, centerY = avgY, height = h, frame = frame)
    }

    /** 드래그 점을 이미지 표시 영역 안으로 자른다 (레터박스 여백에서 시작해도 이미지 가장자리에 붙도록). */
    fun clampToImage(p: Offset): Offset = Offset(
        x = p.x.coerceIn(frame.left, frame.right),
        y = p.y.coerceIn(frame.top, frame.bottom),
    )

    val selectedId = underlines.firstOrNull { it.selected }?.id

    // 안내 메시지
    val guidance: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "손가락으로 텍스트에 밑줄을 그어주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "• 자동으로 수평선으로 변환됩니다\n• 밑줄을 탭하면 선택해 삭제할 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    // 이미지 + 밑줄 캔버스
    val imageArea: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "인식 대상 이미지",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it.toSize() }
                    .pointerInput(frame) {
                        detectTapGestures { tap ->
                            val hit = underlines.indexOfLast { it.rectIn(frame).contains(tap) }
                            for (i in underlines.indices) {
                                underlines[i] = underlines[i].copy(selected = i == hit && hit >= 0)
                            }
                        }
                    }
                    .pointerInput(frame) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val offset = clampToImage(start)
                                dragging = true
                                dragMinX = offset.x
                                dragMaxX = offset.x
                                dragMinY = offset.y
                                dragMaxY = offset.y
                                dragSumY = offset.y
                                dragCount = 1
                            },
                            onDrag = { change, _ ->
                                val p = clampToImage(change.position)
                                dragMinX = min(dragMinX, p.x)
                                dragMaxX = max(dragMaxX, p.x)
                                dragMinY = min(dragMinY, p.y)
                                dragMaxY = max(dragMaxY, p.y)
                                dragSumY += p.y
                                dragCount += 1
                                change.consume()
                            },
                            onDragEnd = {
                                previewUnderline()?.let {
                                    underlines.add(it.copy(id = System.currentTimeMillis()))
                                }
                                dragging = false
                                dragCount = 0
                            },
                            onDragCancel = {
                                dragging = false
                                dragCount = 0
                            },
                        )
                    },
            ) {
                val all = underlines + listOfNotNull(previewUnderline())
                all.forEach { u ->
                    val r = u.rectIn(frame)
                    // 1. 반투명 노란 배경
                    drawRect(
                        color = highlightColor.copy(alpha = 0.2f),
                        topLeft = Offset(r.left, r.top),
                        size = Size(r.width, r.height),
                    )
                    // 2. 중앙 굵은 노란 수평선
                    val lineY = frame.top + u.centerY * frame.height
                    drawLine(
                        color = highlightColor,
                        start = Offset(r.left, lineY),
                        end = Offset(r.right, lineY),
                        strokeWidth = 4.dp.toPx(),
                    )
                    // 3. 선택 테두리(파란색)
                    if (u.selected) {
                        drawRect(
                            color = selectColor,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }

    // 선택 개수 · 삭제/지우기 · 인식 버튼
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (underlines.isEmpty()) {
                    "인식할 문장에 밑줄을 그어 선택하세요"
                } else {
                    "${underlines.size}개 영역 선택됨 — 탭해서 선택 후 삭제할 수 있어요"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 선택 삭제
                IconButton(
                    onClick = {
                        if (selectedId != null) {
                            underlines.removeAll { it.id == selectedId }
                        }
                    },
                    enabled = selectedId != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "선택 밑줄 삭제",
                        tint = if (selectedId != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                // 전체 지우기
                IconButton(
                    onClick = { underlines.clear() },
                    enabled = underlines.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "전체 지우기",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRecognizeAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("전체 인식")
                }
                Button(
                    onClick = {
                        val regions = underlines.mapNotNull { it.toImageRect(bitmap) }
                        if (regions.isNotEmpty()) onRecognizeRegions(regions)
                    },
                    enabled = underlines.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("OCR 실행 (${underlines.size}개)")
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // 가로로 넓은 화면(폰·태블릿 가로)에서 세로로 쌓으면 이미지 영역이 0으로 눌린다 → 이미지 옆에 버튼·안내.
            // 높이가 낮으니 인식 버튼을 위에 둬서 스크롤 없이 누를 수 있게 한다.
            Row(modifier = Modifier.fillMaxSize()) {
                imageArea(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    controls(Modifier.fillMaxWidth())
                    guidance(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                guidance(Modifier.fillMaxWidth())
                imageArea(Modifier.fillMaxWidth().weight(1f))
                controls(Modifier)
            }
        }
    }
}

/**
 * 정규화 밑줄을 이미지 픽셀 좌표 Rect로 변환한다 (이미지 경계로 clamp).
 * 너무 작거나 이미지 밖이면 null.
 */
private fun Underline.toImageRect(bitmap: Bitmap): AndroidRect? {
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    if (imgW <= 0f || imgH <= 0f) return null

    val r = normalizedRect
    val left = (r.left * imgW).coerceIn(0f, imgW)
    val right = (r.right * imgW).coerceIn(0f, imgW)
    val top = (r.top * imgH).coerceIn(0f, imgH)
    val bottom = (r.bottom * imgH).coerceIn(0f, imgH)

    val px = left.toInt()
    val py = top.toInt()
    val pw = (right - left).toInt()
    val ph = (bottom - top).toInt()
    if (pw < 1 || ph < 1) return null
    return AndroidRect(px, py, px + pw, py + ph)
}

/**
 * 백엔드 OCR(`POST /ocr/extract`). 비트맵을 JPEG로 압축해 전송하고,
 * 영역(이미지 픽셀 좌표 [AndroidRect])이 있으면 `regions` JSON으로 함께 보낸다.
 */
private suspend fun recognizeOnServer(
    container: AppContainer,
    bitmap: Bitmap,
    regions: List<AndroidRect>,
    engine: OcrEngine,
): String = withContext(Dispatchers.IO) {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
    val imagePart = MultipartBody.Part.createFormData(
        "image",
        "image.jpg",
        baos.toByteArray().toRequestBody("image/jpeg".toMediaType()),
    )
    val enginePart = engine.value.toRequestBody("text/plain".toMediaType())
    val regionsPart = if (regions.isEmpty()) {
        null
    } else {
        val list = regions.map {
            OcrRegion(
                left = it.left.toDouble(),
                top = it.top.toDouble(),
                right = it.right.toDouble(),
                bottom = it.bottom.toDouble(),
            )
        }
        container.json.encodeToString(ListSerializer(OcrRegion.serializer()), list)
            .toRequestBody("text/plain".toMediaType())
    }
    container.ocrApi.extract(imagePart, enginePart, regionsPart).extractedText
}

/** OCR 처리 단계 */
private sealed interface OcrPhase {
    data object Selecting : OcrPhase
    data object Processing : OcrPhase
    data object Success : OcrPhase
    data class Error(val message: String) : OcrPhase
}

/** 단계 저장 — 처리 중이던 인식은 Activity 재생성과 함께 취소되므로 영역 선택으로 되돌린다. */
private val OcrPhaseSaver = Saver<OcrPhase, String>(
    save = { phase ->
        when (phase) {
            OcrPhase.Selecting, OcrPhase.Processing -> "selecting"
            OcrPhase.Success -> "success"
            is OcrPhase.Error -> "error:" + phase.message
        }
    },
    restore = { saved ->
        when {
            saved == "success" -> OcrPhase.Success
            saved.startsWith("error:") -> OcrPhase.Error(saved.removePrefix("error:"))
            else -> OcrPhase.Selecting
        }
    },
)
