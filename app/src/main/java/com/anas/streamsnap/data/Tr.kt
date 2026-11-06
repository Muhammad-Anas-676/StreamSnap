package com.anas.streamsnap.data

/**
 * Tiny bilingual helper (English / Urdu). Used by the UI and by notifications.
 * The whole UI is rebuilt when the language changes, so plain function calls are enough.
 */
object Tr {
    @Volatile
    var lang: String = "en"

    val isUrdu: Boolean get() = lang == "ur"
}

fun tr(en: String, ur: String): String = if (Tr.isUrdu) ur else en
