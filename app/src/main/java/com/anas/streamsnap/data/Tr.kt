package com.anas.streamsnap.data

import com.anas.streamsnap.core.CoreLang

/**
 * Three-language helper: English (default), Roman Urdu ("ro") and Urdu script ("ur").
 * The whole UI is rebuilt when the language changes, so plain function calls are enough.
 * The shared core module (legal documents, theme picker) follows the same language.
 */
object Tr {
    @Volatile
    var lang: String = "en"
        set(value) {
            field = value
            CoreLang.lang = value
        }

    val isUrdu: Boolean get() = lang == "ur"
    val isRoman: Boolean get() = lang == "ro"
}

/** Older two-language calls: Roman Urdu looks the English text up in [RomanMap] and falls back to English. */
fun tr(en: String, ur: String): String = when (Tr.lang) {
    "ur" -> ur
    "ro" -> RomanMap.map[en] ?: en
    else -> en
}

/** English, Roman Urdu and Urdu written out. Used by all new screens. */
fun tr3(en: String, ro: String, ur: String): String = when (Tr.lang) {
    "ur" -> ur
    "ro" -> ro
    else -> en
}
