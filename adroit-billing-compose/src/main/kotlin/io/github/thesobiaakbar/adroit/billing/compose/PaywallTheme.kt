package io.github.thesobiaakbar.adroit.billing.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.thesobiaakbar.adroit.billing.PaywallStyle

/**
 * The app's [MaterialTheme] with the paywall's Firebase `style` on top: `accent` becomes the primary color,
 * `background` the background and surface, and `dark` picks a dark or light scheme (null keeps the app's scheme, or
 * follows the system when the app sets none). Text colors follow the luminance of the colors set.
 */
@Composable
public fun PaywallTheme(style: PaywallStyle, content: @Composable () -> Unit) {
    val dark = style.dark
    val base = when (dark) {
        null -> MaterialTheme.colorScheme
        true -> darkColorScheme()
        false -> lightColorScheme()
    }
    val systemDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = base.withStyle(style, dark ?: systemDark),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

private fun ColorScheme.withStyle(style: PaywallStyle, dark: Boolean): ColorScheme {
    var scheme = this
    style.accent?.let { argb ->
        val accent = Color(argb)
        scheme = scheme.copy(primary = accent, onPrimary = accent.contentColor(), secondary = accent)
    }
    style.background?.let { argb ->
        val background = Color(argb)
        val onBackground = background.contentColor()
        scheme = scheme.copy(
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceContainerLow = background,
            surfaceContainer = background,
            surfaceContainerHigh = background,
            onSurfaceVariant = onBackground.copy(alpha = 0.72f),
        )
    }
    if (style.background == null && style.dark != null) {
        scheme = scheme.copy(onSurface = if (dark) Color.White else Color.Black)
    }
    return scheme
}

private fun Color.contentColor(): Color = if (luminance() > 0.5f) Color.Black else Color.White
