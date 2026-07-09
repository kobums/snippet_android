package com.gowoobro.snippet.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** 앱 단일 DataStore 인스턴스 (TokenStore/SettingsStore가 공유) */
val Context.snippetDataStore: DataStore<Preferences> by preferencesDataStore(name = "snippet_prefs")
