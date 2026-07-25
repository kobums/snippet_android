package com.gowoobro.snippet.core.data

import com.gowoobro.snippet.BuildConfig
import com.gowoobro.snippet.core.datastore.SettingsStore
import com.gowoobro.snippet.core.model.AppVersionDto
import com.gowoobro.snippet.core.network.api.AppVersionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 앱 버전 정책 게이트 — 강제 업데이트 차단 여부를 앱 전역에 노출한다.
 *
 * 조회 실패(네트워크 끊김·서버 장애·응답 형식 변경)는 전부 "차단 안 함"으로 처리한다.
 * 서버가 죽었다는 이유로 앱 전체가 잠기면 안 되기 때문 (fail-open).
 */
class AppVersionGate(
    private val api: AppVersionApi,
    private val settingsStore: SettingsStore,
    private val externalScope: CoroutineScope,
) {

    private val _policy = MutableStateFlow(AppVersionDto.notRequired)

    /** 마지막으로 받아온 정책. 조회 전/실패 시 notRequired. */
    val policy: StateFlow<AppVersionDto> = _policy.asStateFlow()

    private val _showSoftPrompt = MutableStateFlow(false)

    /** 권장 업데이트 안내 노출 여부 (강제 아님, 사용자가 닫을 수 있음). */
    val showSoftPrompt: StateFlow<Boolean> = _showSoftPrompt.asStateFlow()

    /** 중복 요청 방지 — 포그라운드 복귀가 연속으로 들어와도 1개만 수행. */
    private var inFlight: Job? = null

    /** 정책을 조회해 상태를 갱신한다. 실패해도 예외를 던지지 않는다. */
    fun refresh() {
        if (inFlight?.isActive == true) return
        inFlight = externalScope.launch {
            val result = runCatching { api.check(version = BuildConfig.VERSION_NAME) }.getOrNull()
            if (result == null) {
                // fail-open: 이미 차단 중이면 그대로 두고(해제는 성공 응답으로만),
                // 아니면 차단하지 않는다.
                if (!_policy.value.updateRequired) {
                    _policy.value = AppVersionDto.notRequired
                }
                return@launch
            }
            _policy.value = result

            val latest = result.latestVersion
            _showSoftPrompt.value = !result.updateRequired &&
                result.updateAvailable &&
                latest != null &&
                settingsStore.skippedUpdateVersion() != latest
        }
    }

    /** 권장 업데이트 안내를 이 버전에 한해 다시 띄우지 않는다. */
    fun skipSoftPrompt() {
        _showSoftPrompt.value = false
        val latest = _policy.value.latestVersion ?: return
        externalScope.launch { settingsStore.setSkippedUpdateVersion(latest) }
    }
}
