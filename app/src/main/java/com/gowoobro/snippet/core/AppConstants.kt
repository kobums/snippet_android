package com.gowoobro.snippet.core

/**
 * 앱 전역 상수 (docs/native-migration/02-data-api.md §1.3)
 */
object AppConstants {
    const val APP_GROUP_ID = "group.com.gowoobro.snippet"
    const val WIDGET_PROVIDER_NAME = "SnippetWidgetProvider"

    /** 스와이프 카드 1회 fetch 개수 */
    const val SNIPPET_FETCH_COUNT = 10

    /** 카드가 이 개수 이하로 남으면 추가 fetch */
    const val SNIPPET_LOW_THRESHOLD = 3
}
