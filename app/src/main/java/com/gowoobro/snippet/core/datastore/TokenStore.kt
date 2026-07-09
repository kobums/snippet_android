package com.gowoobro.snippet.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * JWT access/refresh 토큰 저장소 (02-data-api.md §5.4 — 키 이름 Flutter와 동일 유지).
 *
 * OkHttp 인터셉터/Authenticator는 동기 컨텍스트라 in-memory 캐시를 둔다.
 * 토큰 변경은 항상 이 클래스를 통해서만 일어나므로 캐시는 일관성이 보장된다.
 */
class TokenStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Volatile
    private var cacheInitialized = false

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    private fun ensureCache() {
        if (cacheInitialized) return
        synchronized(this) {
            if (cacheInitialized) return
            runBlocking {
                val prefs = dataStore.data.first()
                cachedAccessToken = prefs[KEY_ACCESS_TOKEN]
                cachedRefreshToken = prefs[KEY_REFRESH_TOKEN]
            }
            cacheInitialized = true
        }
    }

    /** OkHttp 스레드용 동기 접근 (AuthInterceptor) */
    fun accessTokenBlocking(): String? {
        ensureCache()
        return cachedAccessToken
    }

    /** OkHttp 스레드용 동기 접근 (TokenAuthenticator) */
    fun refreshTokenBlocking(): String? {
        ensureCache()
        return cachedRefreshToken
    }

    suspend fun accessToken(): String? {
        return dataStore.data.first()[KEY_ACCESS_TOKEN].also {
            cachedAccessToken = it
        }
    }

    suspend fun refreshToken(): String? {
        return dataStore.data.first()[KEY_REFRESH_TOKEN].also {
            cachedRefreshToken = it
        }
    }

    val accessTokenFlow: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }

    suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) {
                prefs[KEY_REFRESH_TOKEN] = refreshToken
            }
        }
        synchronized(this) {
            cachedAccessToken = accessToken
            if (refreshToken != null) cachedRefreshToken = refreshToken
            cacheInitialized = true
        }
    }

    /** 401 + refreshToken 없음 케이스: access만 삭제 (§2.2) */
    suspend fun clearAccessToken() {
        dataStore.edit { it.remove(KEY_ACCESS_TOKEN) }
        synchronized(this) {
            cachedAccessToken = null
            cacheInitialized = true
        }
    }

    suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
        }
        synchronized(this) {
            cachedAccessToken = null
            cachedRefreshToken = null
            cacheInitialized = true
        }
    }

    companion object {
        /** Flutter FlutterSecureStorage와 동일 키 */
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
    }
}
