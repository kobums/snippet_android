package com.gowoobro.snippet.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class SnippetTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Snippet(
        route = "snippet",
        label = "스니펫",
        selectedIcon = Icons.Filled.FormatQuote,
        unselectedIcon = Icons.Outlined.FormatQuote,
    ),
    Dashboard(
        route = "dashboard",
        label = "대시보드",
        selectedIcon = Icons.Filled.Assessment,
        unselectedIcon = Icons.Outlined.Assessment,
    ),
    Records(
        route = "records",
        label = "독서기록",
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
    ),
    Library(
        route = "library",
        label = "서재",
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
    ),
    Profile(
        route = "profile",
        label = "프로필",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    ),
    ;

    companion object {
        /**
         * 푸시 딥링크 라우트 문자열(snippet/dashboard/records/library/profile)을
         * 해당 탭으로 매핑한다. 알 수 없는 값이면 null (무시).
         * iOS와 공유하는 딥링크 계약.
         */
        fun fromRoute(route: String?): SnippetTab? =
            entries.firstOrNull { it.route.equals(route, ignoreCase = true) }
    }
}
