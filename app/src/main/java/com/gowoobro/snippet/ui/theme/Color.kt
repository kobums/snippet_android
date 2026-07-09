package com.gowoobro.snippet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Snippet 브랜드 컬러 토큰.
 *
 * 출처: docs/native-migration/03-design-system.md §1, §7.3
 * 원칙: 모노톤(블랙/화이트) primary + 그린 secondary만 브랜드로 유지하고,
 * 나머지는 M3 ColorScheme 슬롯(Theme.kt)을 통해 소비한다.
 * 화면 코드는 가급적 MaterialTheme.colorScheme.* 만 사용할 것.
 */

// ── 브랜드 원시 토큰 ─────────────────────────────────────────────
val BrandBlack = Color(0xFF1A1A1A)      // primaryMain
val BrandBlackDeep = Color(0xFF000000)  // primaryDark
val BrandGreyLight = Color(0xFF424242)  // primaryLight
val BrandSubtle = Color(0xFFF0F0F0)     // primarySubtle (primaryContainer light)

val BrandGreen = Color(0xFF34C759)        // secondaryMain (iOS systemGreen)
val BrandGreenLight = Color(0xFF5DD97C)   // secondaryLight
val BrandGreenDark = Color(0xFF28A745)    // secondaryDark
val BrandGreenSubtle = Color(0xFFE5F7EC)  // secondarySubtle (secondaryContainer light)
val BrandGreenContainerDark = Color(0xFF1A3A26) // secondaryContainer dark

// ── 시맨틱 상태 컬러 (iOS 시스템 컬러 차용 — 라이트/다크 공통) ──
val StatusSuccess = Color(0xFF34C759)
val StatusWarning = Color(0xFFFFCC00) // iOS systemYellow — 별점에도 사용
val StatusError = Color(0xFFFF3B30)   // iOS systemRed
val StatusInfo = Color(0xFF007AFF)    // iOS systemBlue
val StatusCaution = Color(0xFFFF9500) // iOS systemOrange — 중단/주의 뱃지(흰 글씨 대비 확보)
val BadgeNeutral = Color(0xFF8E8E93)  // iOS systemGray — 대기중 등 비강조 뱃지
val AccentPurple = Color(0xFFB794F4)

// ── 차트 컬러 (카테고리 분포 등 — 라이트/다크 공통 유지) ──────
val ChartColor1 = Color(0xFFFF6B9D) // 핑크
val ChartColor2 = Color(0xFF4ECDC4) // 틸
val ChartColor3 = Color(0xFFFFA07A) // 살몬
val ChartColor4 = Color(0xFF98D8C8) // 민트
val ChartColor5 = Color(0xFFB5838D) // 로즈브라운
val ChartColors = listOf(ChartColor1, ChartColor2, ChartColor3, ChartColor4, ChartColor5)

// ── 라이트 팔레트 (M3 ColorScheme 슬롯용) ──────────────────────
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF1A1A1A)
internal val LightSurfaceVariant = Color(0xFFF0F0F5)      // surfaceSecondary
internal val LightOnSurfaceVariant = Color(0xFF666666)    // textSecondary
internal val LightSurfaceContainerHighest = Color(0xFFF8F8FA) // surfaceTertiary
internal val LightSurfaceContainerHigh = Color(0xFFF0F0F5)
internal val LightSurfaceContainer = Color(0xFFF5F5F5)    // neutral100
internal val LightSurfaceContainerLow = Color(0xFFFAFAFA) // neutral50
internal val LightOutline = Color(0xFFE0E0E0)             // border
internal val LightOutlineVariant = Color(0xFFEEEEEE)      // divider
internal val LightErrorContainer = Color(0xFFFFE9E7)
internal val LightOnErrorContainer = Color(0xFF8C1D14)

// ── 다크 팔레트 (iOS 다크모드 시스템 색 기반) ──────────────────
internal val DarkSurface = Color(0xFF1C1C1E)              // systemBackground dark
internal val DarkOnSurface = Color(0xFFFFFFFF)
internal val DarkSurfaceVariant = Color(0xFF2C2C2E)       // bgSecondary
internal val DarkOnSurfaceVariant = Color(0xFFAEAEB2)     // textSecondary dark
internal val DarkSurfaceContainerHighest = Color(0xFF3A3A3C) // bgTertiary
internal val DarkSurfaceContainerHigh = Color(0xFF2C2C2E)
internal val DarkSurfaceContainer = Color(0xFF232325)
internal val DarkSurfaceContainerLow = Color(0xFF1F1F21)
internal val DarkOutline = Color(0xFF38383A)              // border dark
internal val DarkOutlineVariant = Color(0xFF38383A)       // divider dark
internal val DarkErrorContainer = Color(0xFF4A2521)
internal val DarkOnErrorContainer = Color(0xFFFFB4AB)
