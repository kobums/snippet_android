package com.gowoobro.snippet.core.model

/** 다크모드 설정 (로컬 전용, DataStore `themeMode` 키 저장 값) */
enum class AppThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}
