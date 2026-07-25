package com.gowoobro.snippet.ui.update

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.gowoobro.snippet.core.model.AppVersionDto

/** 권장 업데이트 안내 — 강제가 아니므로 "나중에"로 닫을 수 있다. */
@Composable
fun SoftUpdateDialog(
    policy: AppVersionDto,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 버전이 있습니다") },
        text = {
            Text(
                policy.message
                    ?: "최신 버전으로 업데이트하면 새로운 기능을 사용할 수 있어요.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, policy.resolvedStoreUrl.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                onDismiss()
            }) {
                Text("업데이트")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        },
    )
}
