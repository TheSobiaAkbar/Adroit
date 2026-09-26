package com.company.admob.sample.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E9E),
    secondary = Color(0xFF546E7A),
    tertiary = Color(0xFF2E7D32),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCAFF),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFFA5D6A7),
)

/** Plain Material3, light and dark. It's an internal tool: clarity over branding. */
@Composable
fun PlaygroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
