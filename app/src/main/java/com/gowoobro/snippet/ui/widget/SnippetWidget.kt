package com.gowoobro.snippet.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gowoobro.snippet.MainActivity

/**
 * 홈 화면 위젯 — 오늘의 스니펫 한 문장 미리보기.
 *
 * 데이터는 앱에서 [SnippetWidgetBridge.update] 로 Glance 상태(Preferences)에 기록하고,
 * 위젯은 [currentState] 로 읽어 렌더링한다. 탭하면 앱을 연다.
 */
class SnippetWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val text = prefs[KEY_TEXT]?.takeIf { it.isNotBlank() }
            ?: "오늘의 스니펫을 만나보세요"
        val tag = prefs[KEY_TAG].orEmpty()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 4,
            )
            if (tag.isNotBlank()) {
                Text(
                    text = "#$tag",
                    style = TextStyle(
                        color = GlanceTheme.colors.secondary,
                        fontSize = 12.sp,
                    ),
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
            }
        }
    }

    companion object {
        val KEY_TEXT = stringPreferencesKey("snippet_text")
        val KEY_TAG = stringPreferencesKey("snippet_tag")
    }
}

/** 위젯 등록 리시버 (AndroidManifest 에 선언) */
class SnippetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SnippetWidget()
}

/**
 * 앱에서 위젯에 표시할 최신 스니펫을 갱신한다.
 * 설치된 위젯이 없으면 아무 일도 하지 않는다.
 */
object SnippetWidgetBridge {
    suspend fun update(context: Context, text: String, tag: String?) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(SnippetWidget::class.java)
        if (ids.isEmpty()) return
        val widget = SnippetWidget()
        ids.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[SnippetWidget.KEY_TEXT] = text
                    this[SnippetWidget.KEY_TAG] = tag.orEmpty()
                }
            }
            widget.update(context, id)
        }
    }
}
