package com.example.courseproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6F40),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1BD),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF3A646F),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6B5E2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5A3),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF14512A),
    onPrimaryContainer = Color(0xFFB1F1BD),
    secondary = Color(0xFFA2CDD9),
    onSecondary = Color(0xFF1F333B),
    tertiary = Color(0xFFD8C68E),
)

/**
 * Тема приложения на основе Material Design. Динамические цвета отключены,
 * чтобы оформление было одинаковым на всех устройствах.
 */
@Composable
fun CourseProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
