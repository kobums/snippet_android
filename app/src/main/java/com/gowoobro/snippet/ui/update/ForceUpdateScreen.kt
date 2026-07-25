package com.gowoobro.snippet.ui.update

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gowoobro.snippet.BuildConfig
import com.gowoobro.snippet.core.model.AppVersionDto

/**
 * 강제 업데이트 차단 화면.
 *
 * 뒤로가기를 막고 닫기 수단을 두지 않는다 — 업데이트 외에는 진행할 방법이 없어야 한다.
 */
@Composable
fun ForceUpdateScreen(policy: AppVersionDto) {
    val context = LocalContext.current

    // 뒤로가기로 빠져나갈 수 없게 소비만 하고 아무것도 하지 않는다
    BackHandler(enabled = true) {}

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = "업데이트가 필요합니다",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = policy.message
                        ?: "원활한 사용을 위해 최신 버전으로 업데이트해주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                policy.latestVersion?.let { latest ->
                    Text(
                        text = "현재 ${BuildConfig.VERSION_NAME} → 최신 $latest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, policy.resolvedStoreUrl.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text("Play 스토어에서 업데이트")
                }
            }
        }
    }
}
