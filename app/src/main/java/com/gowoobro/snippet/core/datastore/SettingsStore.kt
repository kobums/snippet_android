package com.gowoobro.snippet.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gowoobro.snippet.core.model.AppThemeMode
import com.gowoobro.snippet.core.model.OcrEnginePreference
import com.gowoobro.snippet.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 사용자 프로필 + 테마 설정 저장소 (02-data-api.md §6.1).
 *
 * ⚠️ Flutter와 달리 `current_user` JSON에 토큰을 포함하지 않는다 —
 * 토큰은 TokenStore에만 저장 (§5.4 개선 권장 반영).
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    // ----- 사용자 프로필 -----

    val userProfileFlow: Flow<UserProfile?> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_USER]?.let { decodeUser(it) }
    }

    suspend fun currentUser(): UserProfile? =
        dataStore.data.first()[KEY_CURRENT_USER]?.let { decodeUser(it) }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { it[KEY_CURRENT_USER] = json.encodeToString(UserProfile.serializer(), profile) }
    }

    suspend fun clearUserProfile() {
        dataStore.edit { it.remove(KEY_CURRENT_USER) }
    }

    private fun decodeUser(raw: String): UserProfile? =
        runCatching { json.decodeFromString(UserProfile.serializer(), raw) }.getOrNull()

    // ----- 테마 설정 -----

    val themeModeFlow: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        AppThemeMode.fromValue(prefs[KEY_THEME_MODE])
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.value }
    }

    // ----- OCR 엔진 설정 -----

    val ocrEngineFlow: Flow<OcrEnginePreference> = dataStore.data.map { prefs ->
        OcrEnginePreference.fromValue(prefs[KEY_OCR_ENGINE])
    }

    suspend fun ocrEngine(): OcrEnginePreference =
        OcrEnginePreference.fromValue(dataStore.data.first()[KEY_OCR_ENGINE])

    suspend fun setOcrEngine(pref: OcrEnginePreference) {
        dataStore.edit { it[KEY_OCR_ENGINE] = pref.value }
    }

    // ----- 권장 업데이트 안내 스킵 -----

    /** "나중에"를 누른 최신 버전. 같은 버전에 대해서는 다시 안내하지 않는다. */
    suspend fun skippedUpdateVersion(): String? =
        dataStore.data.first()[KEY_SKIPPED_UPDATE_VERSION]

    suspend fun setSkippedUpdateVersion(version: String) {
        dataStore.edit { it[KEY_SKIPPED_UPDATE_VERSION] = version }
    }

    companion object {
        /** Flutter SharedPreferences와 동일 키 */
        private val KEY_CURRENT_USER = stringPreferencesKey("current_user")
        private val KEY_THEME_MODE = stringPreferencesKey("themeMode")
        private val KEY_OCR_ENGINE = stringPreferencesKey("ocrEngine")
        private val KEY_SKIPPED_UPDATE_VERSION = stringPreferencesKey("skippedUpdateVersion")
    }
}
