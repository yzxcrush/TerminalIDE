package com.terminalide.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ==================== 深色主题色板（现代 IDE / Telegram 风格）====================

// 品牌蓝
val BrandBlue = Color(0xFF4FA3E3)
val BrandBlueDeep = Color(0xFF2B6DB0)
val BrandBlueDark = Color(0xFF1B4D80)

// 背景层次（由深到浅）
val BgDeepest = Color(0xFF080C12)
val BgBase = Color(0xFF0D131C)
val SurfaceLow = Color(0xFF141D28)
val SurfaceHigh = Color(0xFF1C2837)
val SurfaceHighest = Color(0xFF233244)

// 文字
val TextPrimary = Color(0xFFE9EEF4)
val TextSecondary = Color(0xFF9AA9BA)
val TextTertiary = Color(0xFF6B7A8A)

// 强调色
val AccentGreen = Color(0xFF57C77A)
val AccentRed = Color(0xFFEF5350)
val AccentAmber = Color(0xFFF5B942)
val AccentPurple = Color(0xFFB388FF)

val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDeep,
    onPrimaryContainer = Color(0xFFDCEEFF),
    secondary = Color(0xFF6FC3F5),
    onSecondary = Color(0xFF0A1C2E),
    secondaryContainer = Color(0xFF24425E),
    onSecondaryContainer = Color(0xFFD2E6FF),
    tertiary = Color(0xFFB388FF),
    onTertiary = Color(0xFF160B33),
    background = BgBase,
    onBackground = TextPrimary,
    surface = SurfaceLow,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceLow,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    error = AccentRed,
    onError = Color.White,
    outline = Color(0xFF33455A),
    outlineVariant = Color(0xFF26333F),
    inverseSurface = TextPrimary,
    inverseOnSurface = BgBase,
)

val LightColors = lightColorScheme(
    primary = BrandBlueDeep,
    onPrimary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF16202B),
    surface = Color.White,
    onSurface = Color(0xFF16202B),
)

/** 顶栏渐变（深蓝 → 亮蓝 → 青）。 */
val TopBarGradient = listOf(
    Color(0xFF1B4D80),
    Color(0xFF2E6FA8),
    Color(0xFF3E8BC8),
)

/** 编辑区行号栏背景。 */
val GutterBackground = Color(0xFF0A1018)
