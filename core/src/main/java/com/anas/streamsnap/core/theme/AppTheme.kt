package com.anas.streamsnap.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Full Material 3 colour scheme built from one accent colour, for Light, Dark and AMOLED.
 * Every text/icon colour is an "on" colour that is checked against the colour it sits on
 * (see [ensureContrast]), so nothing can become hard to see in any of the 30 combinations.
 */
private class Surfaces(
    val background: Color, val surface: Color,
    val lowest: Color, val low: Color, val container: Color, val high: Color, val highest: Color,
    val onSurface: Color, val onSurfaceVariant: Color, val outline: Color, val outlineVariant: Color,
)

private val LightSurfaces = Surfaces(
    background = Color(0xFFF6F7FB), surface = Color(0xFFFAFBFE),
    lowest = Color(0xFFFFFFFF), low = Color(0xFFF3F4F9), container = Color(0xFFEEF0F6),
    high = Color(0xFFE8EAF1), highest = Color(0xFFE2E5ED),
    onSurface = Color(0xFF14161C), onSurfaceVariant = Color(0xFF464B58),
    outline = Color(0xFF767B89), outlineVariant = Color(0xFFC9CCD6),
)

private val DarkSurfaces = Surfaces(
    background = Color(0xFF0E1117), surface = Color(0xFF121620),
    lowest = Color(0xFF0A0D12), low = Color(0xFF151A24), container = Color(0xFF191F2B),
    high = Color(0xFF202735), highest = Color(0xFF272F3F),
    onSurface = Color(0xFFE6E9F1), onSurfaceVariant = Color(0xFFB4BAC8),
    outline = Color(0xFF8A91A3), outlineVariant = Color(0xFF3A4254),
)

private val AmoledSurfaces = Surfaces(
    background = Color(0xFF000000), surface = Color(0xFF000000),
    lowest = Color(0xFF000000), low = Color(0xFF0A0A0C), container = Color(0xFF101013),
    high = Color(0xFF16161A), highest = Color(0xFF1C1C21),
    onSurface = Color(0xFFF2F4F8), onSurfaceVariant = Color(0xFFB8BDCA),
    outline = Color(0xFF8C92A2), outlineVariant = Color(0xFF3A3B44),
)

fun buildColorScheme(accent: Color, mode: ThemeMode, systemDark: Boolean): ColorScheme {
    val dark = isDarkMode(mode, systemDark)
    val s = when {
        !dark -> LightSurfaces
        mode == ThemeMode.AMOLED -> AmoledSurfaces
        else -> DarkSurfaces
    }
    val worst = s.highest // the brightest/darkest container: text must read on every surface above it

    val primary = ensureContrast(accent, worst, 4.7f)
    val onPrimary = bestOn(primary)

    val primaryContainer = if (dark) lerp(accent, Color.Black, 0.30f) else lerp(accent, Color.White, 0.66f)
    val onPrimaryContainer = ensureContrast(
        if (dark) lerp(accent, Color.White, 0.8f) else lerp(accent, Color.Black, 0.6f), primaryContainer, 4.5f,
    )

    val secBase = if (dark) lerp(accent, Color(0xFFB8C0D4), 0.5f) else lerp(accent, Color(0xFF4B5568), 0.5f)
    val secondary = ensureContrast(secBase, worst, 4.7f)
    val onSecondary = bestOn(secondary)
    val secondaryContainer = if (dark) lerp(accent, Color.Black, 0.36f) else lerp(accent, Color.White, 0.60f)
    val onSecondaryContainer = ensureContrast(
        if (dark) lerp(accent, Color.White, 0.85f) else lerp(accent, Color.Black, 0.65f), secondaryContainer, 4.5f,
    )

    val tAccent = rotateHue(accent, 60f)
    val tertiary = ensureContrast(tAccent, worst, 4.7f)
    val onTertiary = bestOn(tertiary)
    val tertiaryContainer = if (dark) lerp(tAccent, Color.Black, 0.6f) else lerp(tAccent, Color.White, 0.8f)
    val onTertiaryContainer = ensureContrast(
        if (dark) lerp(tAccent, Color.White, 0.85f) else lerp(tAccent, Color.Black, 0.65f), tertiaryContainer, 4.5f,
    )

    val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val onError = if (dark) Color(0xFF601410) else Color.White
    val errorContainer = if (dark) Color(0xFF8C1D18) else Color(0xFFF9DEDC)
    val onErrorContainer = if (dark) Color(0xFFF9DEDC) else Color(0xFF410E0B)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = ensureContrast(accent, if (dark) Color(0xFFE6E9F1) else Color(0xFF2B2F3A), 3f),
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = s.background, onBackground = s.onSurface,
        surface = s.surface, onSurface = s.onSurface,
        surfaceVariant = s.high, onSurfaceVariant = s.onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = if (dark) Color(0xFFE6E9F1) else Color(0xFF2B2F3A),
        inverseOnSurface = if (dark) Color(0xFF14161C) else Color(0xFFF1F2F7),
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        outline = s.outline, outlineVariant = s.outlineVariant,
        scrim = Color.Black,
        surfaceBright = if (dark) s.highest else s.lowest,
        surfaceDim = if (dark) s.lowest else s.highest,
        surfaceContainerLowest = s.lowest, surfaceContainerLow = s.low,
        surfaceContainer = s.container, surfaceContainerHigh = s.high, surfaceContainerHighest = s.highest,
    )
}

/** True when the given mode shows a dark surface. */
fun isDarkMode(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
    ThemeMode.SYSTEM -> systemDark
}

@Composable
fun AppTheme(applyTextScale: Boolean = false, content: @Composable () -> Unit) {
    val st by ThemeManager.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = isDarkMode(st.mode, systemDark)
    val scheme = remember(st.mode, st.accent, systemDark) {
        buildColorScheme(Accents.color(st.accent), st.mode, systemDark)
    }

    // Status bar and navigation bar icons follow the active theme (dark icons on light, light icons on dark).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = {
            if (applyTextScale) {
                // Small / Medium / Large: scales every sp size at once, no restart needed.
                val base = LocalDensity.current
                val density = remember(base.density, base.fontScale, st.textScale) {
                    Density(base.density, base.fontScale * st.textScale.factor)
                }
                CompositionLocalProvider(LocalDensity provides density) { content() }
            } else {
                content()
            }
        },
    )
}
