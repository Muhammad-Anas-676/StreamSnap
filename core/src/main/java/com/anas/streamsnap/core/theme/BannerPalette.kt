package com.anas.streamsnap.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The five announcement colours the admin can choose from (key is what is stored in Firestore). */
object BannerPalette {
    class Entry(val key: String, val label: String, val color: Color)

    val list = listOf(
        Entry("blue", "Info Blue", Color(0xFF1E88E5)),
        Entry("green", "Success Green", Color(0xFF43A047)),
        Entry("amber", "Warning Amber", Color(0xFFFB8C00)),
        Entry("red", "Urgent Red", Color(0xFFE53935)),
        Entry("violet", "Accent Violet", Color(0xFF8E24AA)),
    )

    fun entry(key: String): Entry = list.firstOrNull { it.key == key } ?: list[0]

    /**
     * Returns the fill colour and the text colour for a banner. The fill is darkened a little when needed so that
     * white text reaches 4.5:1; if that is not possible (amber) the best of black / white is used on the original colour.
     */
    fun fillAndText(base: Color): Pair<Color, Color> {
        var t = 0f
        while (t <= 0.45f) {
            val c = lerp(base, Color.Black, t)
            if (contrastRatio(Color.White, c) >= 4.6f) return c to Color.White
            t += 0.05f
        }
        return base to bestOn(base)
    }
}
