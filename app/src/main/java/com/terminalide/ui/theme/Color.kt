package com.terminalide.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// —— Telegram 风格色板（深色为主）——
val TelegramBlue = Color(0xFF5288C1)
val TelegramBlueBright = Color(0xFF6AB2F0)
val TelegramDarkBackground = Color(0xFF0E1621)
val TelegramSurface = Color(0xFF17212B)
val TelegramSurfaceVariant = Color(0xFF1E2C3A)
val TelegramOnBackground = Color(0xFFE8EDF2)
val TelegramOnSurfaceVariant = Color(0xFF9BA8B5)
val AccentRed = Color(0xFFEF5350)
val AccentGreen = Color(0xFF5EC269)

val DarkColors = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B5278),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = TelegramBlueBright,
    onSecondary = Color(0xFF0B1E33),
    secondaryContainer = Color(0xFF24425E),
    onSecondaryContainer = Color(0xFFD2E6FF),
    background = TelegramDarkBackground,
    onBackground = TelegramOnBackground,
    surface = TelegramSurface,
    onSurface = TelegramOnBackground,
    surfaceVariant = TelegramSurfaceVariant,
    onSurfaceVariant = TelegramOnSurfaceVariant,
    surfaceContainer = Color(0xFF1B2733),
    error = AccentRed,
    onError = Color.White,
    outline = Color(0xFF39485A),
)

val LightColors = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1A222C),
    surface = Color.White,
    onSurface = Color(0xFF1A222C),
)
