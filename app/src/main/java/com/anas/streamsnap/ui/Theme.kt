package com.anas.streamsnap.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.anas.streamsnap.data.Accents
import com.anas.streamsnap.data.Prefs

private val Orange = Color(0xFFFF7A2E)

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = lerp(accent, Color.White, 0.84f),
    onPrimaryContainer = lerp(accent, Color.Black, 0.62f),
    secondary = lerp(accent, Color(0xFF445066), 0.45f),
    secondaryContainer = lerp(accent, Color.White, 0.9f),
    onSecondaryContainer = lerp(accent, Color.Black, 0.6f),
    tertiary = Orange,
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0xFFE6EAF4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4FB),
    surfaceContainer = Color(0xFFECEFF8),
    surfaceContainerHigh = Color(0xFFE6EAF4),
    surfaceContainerHighest = Color(0xFFDFE4F0),
)

private fun darkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val s = darkColorScheme(
        primary = lerp(accent, Color.White, 0.28f),
        onPrimary = lerp(accent, Color.Black, 0.78f),
        primaryContainer = lerp(accent, Color.Black, 0.55f),
        onPrimaryContainer = lerp(accent, Color.White, 0.82f),
        secondary = lerp(accent, Color(0xFFB8C4DA), 0.55f),
        secondaryContainer = lerp(accent, Color.Black, 0.7f),
        onSecondaryContainer = lerp(accent, Color.White, 0.85f),
        tertiary = Orange,
        background = Color(0xFF0B0F1A),
        surface = Color(0xFF0F1524),
        surfaceVariant = Color(0xFF1B2335),
        surfaceContainerLowest = Color(0xFF080B14),
        surfaceContainerLow = Color(0xFF111726),
        surfaceContainer = Color(0xFF151C2E),
        surfaceContainerHigh = Color(0xFF1B2335),
        surfaceContainerHighest = Color(0xFF222B40),
    )
    return if (!amoled) s else s.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF16161A),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080809),
        surfaceContainer = Color(0xFF0E0E10),
        surfaceContainerHigh = Color(0xFF141418),
        surfaceContainerHighest = Color(0xFF1A1A1F),
    )
}

@Composable
fun StreamSnapTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val accent = Color(Accents.list.getOrElse(prefs.accent) { Accents.list[0] }.second)

    var scheme = if (prefs.accent == Accents.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkScheme(accent, prefs.amoled) else lightScheme(accent)
    }
    if (dark && prefs.amoled && prefs.accent == Accents.DYNAMIC) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }

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
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
