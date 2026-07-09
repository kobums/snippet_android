package com.gowoobro.snippet.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 진행 중인 독서 세션 스냅샷 저장소 (프로세스 종료 후 "이어 읽기" 복구용).
 *
 * iOS `ReadingTimer`의 UserDefaults 영속(rs_* 키)과 동일한 역할:
 * - wall-clock epoch 기반이라 startEpoch + baseElapsed 만으로 경과 시간 복원 가능
 * - 시작/일시정지/재개 시 갱신, 저장 성공/포기 시 [clear]
 */
class ActiveSessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    data class Snapshot(
        val userBookId: Long,
        val bookTitle: String,
        val startPage: Int,
        /** 현재 running 구간 시작 epoch(초) */
        val startEpoch: Long,
        /** 이전 구간 누적 경과(초) */
        val baseElapsed: Long,
        val paused: Boolean,
    )

    suspend fun save(snapshot: Snapshot) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_BOOK_ID] = snapshot.userBookId
            prefs[KEY_BOOK_TITLE] = snapshot.bookTitle
            prefs[KEY_START_PAGE] = snapshot.startPage
            prefs[KEY_START_EPOCH] = snapshot.startEpoch
            prefs[KEY_BASE_ELAPSED] = snapshot.baseElapsed
            prefs[KEY_PAUSED] = snapshot.paused
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_USER_BOOK_ID)
            prefs.remove(KEY_BOOK_TITLE)
            prefs.remove(KEY_START_PAGE)
            prefs.remove(KEY_START_EPOCH)
            prefs.remove(KEY_BASE_ELAPSED)
            prefs.remove(KEY_PAUSED)
        }
    }

    /** 진행 중 세션이 있으면 스냅샷 반환, 없으면 null */
    suspend fun peek(): Snapshot? {
        val prefs = dataStore.data.first()
        val userBookId = prefs[KEY_USER_BOOK_ID] ?: return null
        return Snapshot(
            userBookId = userBookId,
            bookTitle = prefs[KEY_BOOK_TITLE] ?: "",
            startPage = prefs[KEY_START_PAGE] ?: 0,
            startEpoch = prefs[KEY_START_EPOCH] ?: 0L,
            baseElapsed = prefs[KEY_BASE_ELAPSED] ?: 0L,
            paused = prefs[KEY_PAUSED] ?: false,
        )
    }

    companion object {
        private val KEY_USER_BOOK_ID = longPreferencesKey("rs_userbook_id")
        private val KEY_BOOK_TITLE = stringPreferencesKey("rs_book_title")
        private val KEY_START_PAGE = intPreferencesKey("rs_start_page")
        private val KEY_START_EPOCH = longPreferencesKey("rs_start_epoch")
        private val KEY_BASE_ELAPSED = longPreferencesKey("rs_base_elapsed")
        private val KEY_PAUSED = booleanPreferencesKey("rs_paused")
    }
}
