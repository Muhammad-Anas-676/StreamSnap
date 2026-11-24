package com.anas.streamsnap.session

import android.content.Context
import android.util.Log
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.ErrorKinds

/**
 * Keeps the stack trace of an app crash on the phone and sends it to the admin the next time the app opens
 * (after sign-in and consent). Only the technical trace is sent; no files, cookies or pages.
 */
object CrashReporter {
    private var sp: android.content.SharedPreferences? = null

    fun install(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("crash", Context.MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                sp?.edit()
                    ?.putString("message", (e.javaClass.simpleName + ": " + (e.message ?: "")).take(300))
                    ?.putString("detail", Log.getStackTraceString(e).take(6000))
                    ?.putLong("at", System.currentTimeMillis())
                    ?.commit() // must be written before the process dies
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Sends a saved crash (if any) and forgets it. */
    fun flush(uid: String) {
        val p = sp ?: return
        val detail = p.getString("detail", null) ?: return
        val message = p.getString("message", "") ?: ""
        p.edit().clear().apply()
        Cloud.logError(uid, ErrorKinds.CRASH, message, detail)
    }
}
