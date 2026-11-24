package com.anas.streamsnap.service

import android.content.Context

/**
 * Remembers which admin messages already became a phone notification, so a message that arrives by FCM,
 * by the Firestore listener and by the background check is shown only once. Survives a closed app.
 */
object PushDedupe {
    private const val FILE = "push_seen"
    private const val KEY = "ids"
    private const val MAX = 80

    /** True the first time an id is seen (the caller shows the notification); false for every later time. */
    @Synchronized
    fun markIfNew(ctx: Context, id: String): Boolean {
        if (id.isBlank()) return true
        return try {
            val sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val list = (sp.getString(KEY, "") ?: "").split(",").filter { it.isNotEmpty() }
            if (id in list) return false
            sp.edit().putString(KEY, (list + id).takeLast(MAX).joinToString(",")).apply()
            true
        } catch (_: Throwable) {
            true
        }
    }
}
