package com.anas.streamsnap.core.theme

import androidx.compose.ui.graphics.Color

/** The 10 accent colours offered in Settings. */
object Accents {
    val list: List<Pair<String, Color>> = listOf(
        "Blue" to Color(0xFF1E6BF2),
        "Purple" to Color(0xFF8A4FFF),
        "Teal" to Color(0xFF00A89D),
        "Green" to Color(0xFF2EA44F),
        "Gold" to Color(0xFFE5A100),
        "Orange" to Color(0xFFF57C00),
        "Red" to Color(0xFFE53935),
        "Pink" to Color(0xFFE91E8C),
        "Indigo" to Color(0xFF3F51B5),
        "Cyan" to Color(0xFF00B8D4),
    )

    fun name(i: Int): String = list.getOrNull(i)?.first ?: list[0].first
    fun color(i: Int): Color = list.getOrNull(i)?.second ?: list[0].second
}
