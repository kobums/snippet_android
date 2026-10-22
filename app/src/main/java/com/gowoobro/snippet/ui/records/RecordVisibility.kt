package com.gowoobro.snippet.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * 밑줄을 다른 사용자의 스와이프 카드에 공개할지 — 기본은 비공개.
 * 예전엔 모든 밑줄이 작성자 동의 없이 카드에 노출됐다. 밑줄 유형일 때만 보여준다
 * (iOS RecordVisibilitySection 과 같은 문구·동작).
 */
@Composable
internal fun RecordVisibilityRow(isPublic: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isPublic, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("스와이프 카드에 공개", style = MaterialTheme.typography.bodyLarge)
            Text(
                "켜면 이 문장이 다른 사용자의 스와이프 카드에 나옵니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 행 전체가 토글이라 스위치 자체 클릭은 받지 않는다 (접근성 중복 방지)
        Switch(checked = isPublic, onCheckedChange = null)
    }
}
