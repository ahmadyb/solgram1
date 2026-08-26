package com.solgram.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class SolgramTheme {
    ABYSSAL_SONAR,
    LEDGER_NOIR,
    GRAPHITE_SLATE,
    IVORY_LEDGER,
    SIGNAL_CONTRAST
}

data class SolgramColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val accent: Color
)

object ThemeRegistry {
    val themes = mapOf(
        SolgramTheme.ABYSSAL_SONAR to SolgramColors(
            background = Color(0xFF0A0E1A),
            surface = Color(0xFF151B2E),
            primary = Color(0xFF00D4FF),
            secondary = Color(0xFF7B61FF),
            text = Color(0xFFE0E6FF),
            accent = Color(0xFF00FFA3)
        ),
        SolgramTheme.LEDGER_NOIR to SolgramColors(
            background = Color(0xFF0D0D0D),
            surface = Color(0xFF1A1A1A),
            primary = Color(0xFFFFD700),
            secondary = Color(0xFF888888),
            text = Color(0xFFF5F5F5),
            accent = Color(0xFFFF6B00)
        ),
        SolgramTheme.GRAPHITE_SLATE to SolgramColors(
            background = Color(0xFF1E1E1E),
            surface = Color(0xFF2D2D2D),
            primary = Color(0xFF4A9EFF),
            secondary = Color(0xFF6C6C6C),
            text = Color(0xFFE8E8E8),
            accent = Color(0xFF00C896)
        ),
        SolgramTheme.IVORY_LEDGER to SolgramColors(
            background = Color(0xFFF8F6F0),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF1A1A1A),
            secondary = Color(0xFF666666),
            text = Color(0xFF1A1A1A),
            accent = Color(0xFF0066CC)
        ),
        SolgramTheme.SIGNAL_CONTRAST to SolgramColors(
            background = Color(0xFF000000),
            surface = Color(0xFF111111),
            primary = Color(0xFFFFFF00),
            secondary = Color(0xFF00FF00),
            text = Color(0xFFFFFFFF),
            accent = Color(0xFFFF0000)
        )
    )

    fun getColors(theme: SolgramTheme): SolgramColors = themes[theme] ?: themes[SolgramTheme.ABYSSAL_SONAR]!!
}

@Composable
fun SolgramThemeWrapper(
    theme: SolgramTheme,
    content: @Composable () -> Unit
) {
    val colors = ThemeRegistry.getColors(theme)
    val colorScheme = when (theme) {
        SolgramTheme.IVORY_LEDGER -> lightColorScheme(
            primary = colors.primary,
            secondary = colors.secondary,
            background = colors.background,
            surface = colors.surface
        )
        else -> darkColorScheme(
            primary = colors.primary,
            secondary = colors.secondary,
            background = colors.background,
            surface = colors.surface
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
