package com.gowoobro.snippet.core.migration

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gowoobro.snippet.core.datastore.SettingsStore
import com.gowoobro.snippet.core.datastore.TokenStore
import com.gowoobro.snippet.core.model.UserProfile
import com.gowoobro.snippet.core.network.AppError
import com.gowoobro.snippet.core.network.AppResult
import com.gowoobro.snippet.core.network.api.AuthApi
import com.gowoobro.snippet.core.network.safeApiCall
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

private const val TAG = "FlutterTokenMigrator"

/**
 * 레거시 Flutter 앱(Play 1.0.17)의 로그인 세션을 네이티브로 이관한다.
 *
 * Flutter 앱은 flutter_secure_storage 9.2.4(기본 옵션)로 토큰을 저장했다:
 * - 값: SharedPreferences `FlutterSecureStorage`,
 *   키 = base64프리픽스 + "_" + 이름, 값 = Base64(IV 16바이트 + AES/CBC/PKCS7 암호문)
 * - AES 키: SharedPreferences `FlutterSecureKeyStorage`에 RSA로 감싸 저장,
 *   RSA 키쌍은 AndroidKeyStore `<packageName>.FlutterSecureStoragePluginKey`
 *
 * 같은 패키지로 업데이트되면 SharedPreferences 파일과 Keystore 키가 모두 살아남으므로,
 * 여기서 복호화해 [TokenStore]에 시딩하면 기존 사용자가 재로그인 없이 이어진다.
 *
 * 실패는 전부 "로그인 화면으로 폴백"일 뿐 앱 동작을 막지 않는다 (fail-open).
 * 어떤 결과든 1회 시도 후 레거시 저장소를 삭제하고 다시 시도하지 않는다.
 */
class FlutterTokenMigrator(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val authApi: AuthApi,
) {

    /** 앱 시작 시 checkAuth 이전에 1회 호출한다. */
    suspend fun migrateIfNeeded() {
        try {
            seedTokensFromFlutterOnce()
            // 토큰은 있는데 프로필이 없으면(시딩 직후, 또는 이전 실행에서 me() 실패)
            // 프로필을 채워야 checkAuth가 LoggedIn으로 판정한다.
            ensureProfileIfMissing()
        } catch (e: Exception) {
            // 마이그레이션은 어떤 경우에도 앱 시작을 막으면 안 된다
            Log.e(TAG, "마이그레이션 중 예기치 못한 오류 — 로그인 화면으로 폴백", e)
        }
    }

    // ─── 1단계: 레거시 토큰 시딩 (1회) ─────────────────────────────

    private suspend fun seedTokensFromFlutterOnce() {
        if (dataStore.data.first()[KEY_MIGRATION_DONE] == true) return

        try {
            if (tokenStore.accessToken() != null) {
                // 이미 네이티브로 로그인한 적 있음 — 이관 불필요
                return
            }

            val legacy = readLegacyTokens()
            if (legacy == null) {
                Log.i(TAG, "이관할 레거시 세션 없음 (신규 설치 또는 미로그인)")
                return
            }

            tokenStore.saveTokens(legacy.accessToken, legacy.refreshToken)
            Log.i(TAG, "레거시 토큰 시딩 완료 — 프로필 복원은 /auth/me로 진행")
        } finally {
            // 성공/실패 무관 1회로 종료: 복호화 불가능한 데이터는 재시도해도 같다
            markDoneAndCleanupLegacy()
        }
    }

    private data class LegacyTokens(val accessToken: String, val refreshToken: String)

    /** 복호화 실패·데이터 없음이면 null (예외는 밖으로 던지지 않는다) */
    private fun readLegacyTokens(): LegacyTokens? {
        return try {
            val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

            // 플러그인이 기본이 아닌 암호화 알고리즘을 쓴 흔적이 있으면 포기
            // (본 앱은 기본 옵션만 썼으므로 정상 경로에선 이 키가 존재하지 않는다)
            if (prefs.contains(LEGACY_ALGORITHM_KEY) || prefs.contains(LEGACY_ALGORITHM_STORAGE)) {
                Log.w(TAG, "레거시 저장소가 비기본 알고리즘 사용 — 이관 포기")
                return null
            }

            val access = prefs.getString("${LEGACY_KEY_PREFIX}_auth_token", null)
            val refresh = prefs.getString("${LEGACY_KEY_PREFIX}_auth_refresh_token", null)
            if (access == null || refresh == null) return null

            val aesKey = unwrapAesKey() ?: return null
            LegacyTokens(
                accessToken = decrypt(access, aesKey),
                refreshToken = decrypt(refresh, aesKey),
            )
        } catch (e: Exception) {
            Log.w(TAG, "레거시 토큰 복호화 실패 — 이관 포기", e)
            null
        }
    }

    /** FlutterSecureKeyStorage의 RSA-wrapped AES 키를 Keystore 개인키로 언랩 */
    private fun unwrapAesKey(): java.security.Key? {
        val keyPrefs = context.getSharedPreferences(LEGACY_KEY_PREFS, Context.MODE_PRIVATE)
        val wrapped = keyPrefs.getString(LEGACY_AES_KEY, null) ?: return null

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey(
            "${context.packageName}.FlutterSecureStoragePluginKey",
            null,
        ) as? PrivateKey ?: return null

        // 플러그인과 동일한 트랜스폼/프로바이더 (M+ 분기)
        val rsa = try {
            Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidKeyStoreBCWorkaround")
        } catch (e: Exception) {
            Cipher.getInstance("RSA/ECB/PKCS1Padding")
        }
        rsa.init(Cipher.UNWRAP_MODE, privateKey)
        return rsa.unwrap(Base64.decode(wrapped, Base64.DEFAULT), "AES", Cipher.SECRET_KEY)
    }

    /** Base64(IV 16바이트 + AES/CBC/PKCS7 암호문) → 평문 */
    private fun decrypt(base64Value: String, aesKey: java.security.Key): String {
        val combined = Base64.decode(base64Value, Base64.DEFAULT)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val payload = combined.copyOfRange(IV_SIZE, combined.size)
        val aes = Cipher.getInstance("AES/CBC/PKCS7Padding")
        aes.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
        return String(aes.doFinal(payload), Charsets.UTF_8)
    }

    private suspend fun markDoneAndCleanupLegacy() {
        dataStore.edit { it[KEY_MIGRATION_DONE] = true }
        try {
            context.deleteSharedPreferences(LEGACY_PREFS)
            context.deleteSharedPreferences(LEGACY_KEY_PREFS)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("${context.packageName}.FlutterSecureStoragePluginKey")
        } catch (e: Exception) {
            Log.w(TAG, "레거시 저장소 정리 실패 (무시)", e)
        }
    }

    // ─── 2단계: 프로필 복원 ────────────────────────────────────────

    /**
     * 토큰은 있는데 프로필이 없으면 /auth/me로 채운다.
     *
     * - 시딩된 액세스 토큰이 만료됐어도 401 → TokenAuthenticator가 refresh 토큰으로
     *   자동 갱신하므로 refresh가 살아있는 한(30일 sliding) 성공한다.
     * - 인증 오류(refresh도 만료)면 토큰을 지워 로그인 화면으로 보낸다.
     * - 네트워크 오류면 토큰을 유지한다 — 다음 실행에서 재시도된다.
     */
    private suspend fun ensureProfileIfMissing() {
        if (tokenStore.accessToken() == null) return
        if (settingsStore.currentUser() != null) return

        when (val result = safeApiCall { authApi.me() }) {
            is AppResult.Success -> {
                val me = result.data
                settingsStore.saveUserProfile(UserProfile(id = me.id, email = me.email, name = me.name))
                Log.i(TAG, "레거시 세션 프로필 복원 완료: ${me.email}")
            }
            is AppResult.Failure -> when (result.error) {
                is AppError.AuthError -> {
                    Log.i(TAG, "레거시 refresh 토큰 만료 — 재로그인 필요")
                    tokenStore.clearTokens()
                }
                else -> Log.w(TAG, "프로필 복원 실패(비인증 오류) — 다음 실행에서 재시도: ${result.error.message}")
            }
        }
    }

    companion object {
        private val KEY_MIGRATION_DONE = booleanPreferencesKey("flutter_token_migration_done")

        // flutter_secure_storage 9.2.4 기본값 (android/…/FlutterSecureStorage.java)
        private const val LEGACY_PREFS = "FlutterSecureStorage"
        private const val LEGACY_KEY_PREFS = "FlutterSecureKeyStorage"
        private const val LEGACY_KEY_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"
        private const val LEGACY_AES_KEY = "VGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"
        private const val LEGACY_ALGORITHM_KEY = "FlutterSecureSAlgorithmKey"
        private const val LEGACY_ALGORITHM_STORAGE = "FlutterSecureSAlgorithmStorage"
        private const val IV_SIZE = 16
    }
}
