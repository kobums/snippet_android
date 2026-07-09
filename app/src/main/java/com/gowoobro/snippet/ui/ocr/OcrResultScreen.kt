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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    var phase by remember { mutableStateOf<OcrPhase>(OcrPhase.Selecting) }
    var editableText by remember { mutableStateOf("") }

    // 업라이트 Bitmap 로드 (EXIF 회전 반영)
    LaunchedEffect(imageUri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { TextRecognizer.loadUprightBitmap(context, imageUri) }.getOrNull()
        }
        if (loaded == null) {
            phase = OcrPhase.Error("이미지를 불러올 수 없습니다")
        } else {
            bitmap = loaded
            phase = OcrPhase.Selecting
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

/** 화면(디스플레이) 좌표계의 수평 밑줄 데이터. */
private data class Underline(
    val id: Long,
    val startX: Float,
    val endX: Float,
    val centerY: Float,
    val height: Float,
    val selected: Boolean = false,
) {
    /** 밑줄을 감싸는 사각형(디스플레이 좌표). */
    val rect: Rect
        get() = Rect(
            left = min(startX, endX),
            top = centerY - height / 2f,
            right = max(startX, endX),
            bottom = centerY + height / 2f,
        )

    fun contains(p: Offset): Boolean = rect.contains(p)
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

    val underlines = remember { mutableStateListOf<Underline>() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // 드래그 진행 상태
    var dragMinX by remember { mutableStateOf(0f) }
    var dragMaxX by remember { mutableStateOf(0f) }
    var dragMinY by remember { mutableStateOf(0f) }
    var dragMaxY by remember { mutableStateOf(0f) }
    var dragSumY by remember { mutableStateOf(0f) }
    var dragCount by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    fun previewUnderline(): Underline? {
        if (!dragging || dragCount < 2) return null
        val avgY = dragSumY / dragCount
        val h = ((dragMaxY - dragMinY) * 3f).coerceIn(40f, 80f)
        return Underline(
            id = -1L,
            startX = dragMinX,
            endX = dragMaxX,
            centerY = avgY,
            height = h,
        )
    }

    val selectedId = underlines.firstOrNull { it.selected }?.id

    Column(modifier = Modifier.fillMaxSize()) {
        // 안내 메시지
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val hit = underlines.indexOfLast { it.contains(tap) }
                            for (i in underlines.indices) {
                                underlines[i] = underlines[i].copy(selected = i == hit && hit >= 0)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragMinX = offset.x
                                dragMaxX = offset.x
                                dragMinY = offset.y
                                dragMaxY = offset.y
                                dragSumY = offset.y
                                dragCount = 1
                            },
                            onDrag = { change, _ ->
                                val p = change.position
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
                canvasSize = size
                val all = underlines + listOfNotNull(previewUnderline())
                all.forEach { u ->
                    val r = u.rect
                    // 1. 반투명 노란 배경
                    drawRect(
                        color = highlightColor.copy(alpha = 0.2f),
                        topLeft = Offset(r.left, r.top),
                        size = Size(r.width, r.height),
                    )
                    // 2. 중앙 굵은 노란 수평선
                    drawLine(
                        color = highlightColor,
                        start = Offset(min(u.startX, u.endX), u.centerY),
                        end = Offset(max(u.startX, u.endX), u.centerY),
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

        Column(
            modifier = Modifier.padding(16.dp),
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
                        val cs = canvasSize
                        if (cs.width > 0 && cs.height > 0) {
                            val regions = underlines.mapNotNull { it.toImageRect(cs, bitmap) }
                            if (regions.isNotEmpty()) onRecognizeRegions(regions)
                        }
                    },
                    enabled = underlines.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("OCR 실행 (${underlines.size}개)")
                }
            }
        }
    }
}

/**
 * 디스플레이 좌표 밑줄을 이미지 픽셀 좌표 Rect로 변환한다.
 *
 * 이미지는 ContentScale.Fit(letterbox)로 그려지므로, 캔버스 크기 [canvasSize]와
 * 비트맵 원본 크기로부터 표시 scale·offset을 계산해 역변환한다.
 * 결과는 이미지 경계로 clamp된다. 너무 작거나 이미지 밖이면 null.
 */
private fun Underline.toImageRect(canvasSize: Size, bitmap: Bitmap): AndroidRect? {
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    if (imgW <= 0f || imgH <= 0f) return null

    // ContentScale.Fit: 가로/세로 중 작은 배율로 맞춤 → 레터박스 오프셋 발생
    val scale = min(canvasSize.width / imgW, canvasSize.height / imgH)
    if (scale <= 0f) return null
    val displayedW = imgW * scale
    val displayedH = imgH * scale
    val offsetX = (canvasSize.width - displayedW) / 2f
    val offsetY = (canvasSize.height - displayedH) / 2f

    val r = rect
    val left = ((r.left - offsetX) / scale).coerceIn(0f, imgW)
    val right = ((r.right - offsetX) / scale).coerceIn(0f, imgW)
    val top = ((r.top - offsetY) / scale).coerceIn(0f, imgH)
    val bottom = ((r.bottom - offsetY) / scale).coerceIn(0f, imgH)

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
