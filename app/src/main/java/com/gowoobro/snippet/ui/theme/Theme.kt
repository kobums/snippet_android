package com.gowoobro.snippet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Snippet 테마 — 모노톤(블랙/화이트) 브랜드 + 그린 secondary.
 * 슬롯별 hex 출처: docs/native-migration/03-design-system.md §7.3
 * Shape는 M3 기본값(sm 8 / md 12 / lg 16)이 원본 토큰과 일치하므로 그대로 사용.
 */

private val LightColors = lightColorScheme(
    primary = BrandBlack,                 // #1A1A1A
    onPrimary = Color.White,
    primaryContainer = BrandSubtle,       // #F0F0F0
    onPrimaryContainer = BrandBlack,
    inversePrimary = Color.White,

    secondary = BrandGreen,               // #34C759 (의미상 초록이 필요한 곳 전용)
    onSecondary = Color.White,
    // M3 컴포넌트 기본 슬롯(NavigationBar 선택 필, FilledTonalIconButton 등)은
    // 브랜드 그린이 아니라 모노크롬 뉴트럴을 쓴다 — iOS 디자인과 통일
    secondaryContainer = Color(0xFFE4E4E9),
    onSecondaryContainer = BrandBlack,

    tertiary = AccentPurple,              // #B794F4
    onTertiary = BrandBlack,
    tertiaryContainer = Color(0xFFF2EAFE),
    onTertiaryContainer = Color(0xFF46306B),

    background = LightSurface,            // #FFFFFF
    onBackground = LightOnSurface,        // #1A1A1A
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,         // #F0F0F5
    onSurfaceVariant = LightOnSurfaceVariant,     // #666666
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceContainerLow,    // #FAFAFA
    surfaceContainer = LightSurfaceContainer,          // #F5F5F5
    surfaceContainerHigh = LightSurfaceContainerHigh,  // #F0F0F5
    surfaceContainerHighest = LightSurfaceContainerHighest, // #F8F8FA
    surfaceDim = Color(0xFFDEDEE3),
    surfaceBright = LightSurface,
    surfaceTint = BrandBlack,

    outline = LightOutline,               // #E0E0E0
    outlineVariant = LightOutlineVariant, // #EEEEEE

    error = StatusError,                  // #FF3B30
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    inverseSurface = DarkSurface,
    inverseOnSurface = Color(0xFFF5F5F5),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color.White,                // 모노크롬 정책: 다크에서 액센트 = 흰색
    onPrimary = Color.Black,              // #000000 — 흰 primary 배경 위 검은 글자
    primaryContainer = DarkSurfaceVariant, // #2C2C2E
    onPrimaryContainer = Color.White,
    inversePrimary = BrandBlack,

    secondary = BrandGreen,               // #34C759 (의미상 초록이 필요한 곳 전용, 다크 동일)
    onSecondary = Color.White,
    // 모노크롬 뉴트럴 — 선택 필은 은은한 회색, 그 위 콘텐츠는 흰색
    secondaryContainer = Color(0xFF3A3A3C),
    onSecondaryContainer = Color.White,

    tertiary = AccentPurple,
    onTertiary = Color(0xFF2C2152),
    tertiaryContainer = Color(0xFF46306B),
    onTertiaryContainer = Color(0xFFE5D8FB),

    background = DarkSurface,             // #1C1C1E
    onBackground = DarkOnSurface,         // #FFFFFF
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,          // #2C2C2E
    onSurfaceVariant = DarkOnSurfaceVariant,      // #AEAEB2
    surfaceContainerLowest = Color(0xFF141415),
    surfaceContainerLow = DarkSurfaceContainerLow,     // #1F1F21
    surfaceContainer = DarkSurfaceContainer,           // #232325
    surfaceContainerHigh = DarkSurfaceContainerHigh,   // #2C2C2E
    surfaceContainerHighest = DarkSurfaceContainerHighest, // #3A3A3C
    surfaceDim = DarkSurface,
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceTint = Color.White,

    outline = DarkOutline,                // #38383A
    outlineVariant = DarkOutlineVariant,  // #38383A

    error = StatusError,                  // #FF3B30 (다크 동일)
    onError = Color.White,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = DarkSurface,
    scrim = Color.Black,
)

@Composable
fun SnippetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 브랜드 아이덴티티(모노톤) 유지를 위해 다이나믹 컬러는 기본 비활성화
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnippetTypography,
        content = content,
    )
}
