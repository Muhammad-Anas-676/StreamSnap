package com.anas.streamsnap.core

/**
 * Language used by the shared screens (theme picker, consent, legal documents, admin).
 * The user app keeps this in sync with its own language setting.
 * Codes: "en" = English (default), "ro" = Roman Urdu, "ur" = Urdu (script).
 */
object CoreLang {
    @Volatile
    var lang: String = "en"

    val isUrdu: Boolean get() = lang == "ur"
}

/** Picks the text for the current language. */
fun t(en: String, ro: String, ur: String): String = when (CoreLang.lang) {
    "ro" -> ro
    "ur" -> ur
    else -> en
}
