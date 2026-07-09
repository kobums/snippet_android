package com.gowoobro.snippet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Snippet 타이포그래피.
 *
 * 출처: docs/native-migration/03-design-system.md §2, §7.3
 * 원칙: M3 기본 Typography(Roboto/시스템 폰트)를 그대로 쓰고,
 * 브랜드 정체성에 해당하는 것만 오버라이드/추가한다.
 *   1. displaySmall — 통계 숫자용 32sp SemiBold (대시보드 StatCard 값)
 *   2. QuoteTextStyle — 스와이프 카드 인용문 (시그니처 세리프, 별도 노출)
 *   3. SerifTitleStyle — 책 제목용 세리프 (공개 연출·책 헤더)
 */

private val M3Default = Typography()

val SnippetTypography = M3Default.copy(
    // 통계 숫자 (대시보드 StatCard 값): 32sp / SemiBold / 줄간격 40
    displaySmall = M3Default.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
)

/**
 * 시그니처 quote 스타일 — 스와이프 카드/보관함의 "책 속 한 문장".
 * 책 감성의 세리프 서체. 20sp / W300(Light) / 줄간격 1.6 (= 32sp).
 * M3 Typography 슬롯이 아니므로 화면에서 직접 참조한다.
 */
val QuoteTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Light,
    fontSize = 20.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp,
)

/**
 * 브랜드 워드마크 ("Snippet" 로고 텍스트) — 스플래시/로그인 1곳에서만 사용.
 * 문서 스펙: 28sp / W300 / 자간 5.0.
 */
val BrandWordmarkStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 28.sp,
    lineHeight = 42.sp,
    letterSpacing = 5.sp,
)
