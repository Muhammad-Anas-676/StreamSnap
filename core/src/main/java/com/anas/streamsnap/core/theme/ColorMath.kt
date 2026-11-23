package com.anas.streamsnap.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio between two opaque colours (1.0 .. 21.0). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Moves [fg] toward black (on a light background) or white (on a dark one) in small steps
 * until it reaches [min] contrast against [bg]. Colours that already pass are returned untouched.
 */
fun ensureContrast(fg: Color, bg: Color, min: Float = 4.5f): Color {
    if (contrastRatio(fg, bg) >= min) return fg
    val target = if (bg.luminance() > 0.18f) Color.Black else Color.White
    var i = 1
    while (i <= 50) {
        val c = lerp(fg, target, i / 50f)
        if (contrastRatio(c, bg) >= min) return c
        i++
    }
    return target
}

/** Black or white, whichever is easier to read on [bg]. */
fun bestOn(bg: Color): Color =
    if (contrastRatio(Color.White, bg) >= contrastRatio(Color.Black, bg)) Color.White else Color.Black

/** Rotates the hue of [c] by [degrees]. */
fun rotateHue(c: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb((c.red * 255f + 0.5f).toInt(), (c.green * 255f + 0.5f).toInt(), (c.blue * 255f + 0.5f).toInt()),
        hsv,
    )
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
