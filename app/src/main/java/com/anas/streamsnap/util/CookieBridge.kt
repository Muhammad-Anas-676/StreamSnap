package com.anas.streamsnap.util

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.engine.Engine

/**
 * Turns the login you made in the in-app Browser into a cookies.txt that yt-dlp understands, so
 * "login required" sites (Instagram, Facebook ...) work without a PC or a browser extension.
 * Cookie values are never written to the Log.
 */
object CookieBridge {
    /** facebook.com for m.facebook.com, bbc.co.uk for www.bbc.co.uk. */
    fun baseDomain(host: String): String {
        val h = host.lowercase().trim('.')
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val last = parts[parts.size - 1]
        val second = parts[parts.size - 2]
        return if (last.length == 2 && second.length <= 3) parts.takeLast(3).joinToString(".")
        else parts.takeLast(2).joinToString(".")
    }

    /** Result: (domain, number of cookies saved). */
    fun saveFromWebView(ctx: Context, pageUrl: String): Result<Pair<String, Int>> {
        return try {
            val host = Uri.parse(pageUrl).host.orEmpty()
            if (host.isBlank()) {
                return Result.failure(IllegalStateException("Open a website first"))
            }
            val base = baseDomain(host)
            val cm = CookieManager.getInstance()
            try { cm.flush() } catch (_: Throwable) { }

            val jar = LinkedHashMap<String, String>()
            for (u in linkedSetOf("https://$host", "https://$base", "https://www.$base")) {
                val raw = cm.getCookie(u) ?: continue
                for (pair in raw.split(";")) {
                    val s = pair.trim()
                    val i = s.indexOf('=')
                    if (i <= 0) continue
                    val name = s.substring(0, i).filter { it != '\t' && it != '\r' && it != '\n' }
                    val value = s.substring(i + 1).filter { it != '\t' && it != '\r' && it != '\n' }
                    if (name.isNotEmpty()) jar[name] = value
                }
            }
            if (jar.isEmpty()) {
                return Result.failure(IllegalStateException("No login found on $base. Sign in on the page first."))
            }

            val file = Engine.cookieFile(ctx)
            val keep = ArrayList<String>()
            if (file.exists()) {
                for (line in file.readLines()) {
                    if (line.isBlank()) continue
                    if (line.startsWith("#") && !line.startsWith("#HttpOnly_")) continue
                    val d = line.removePrefix("#HttpOnly_").substringBefore('\t').trimStart('.')
                    if (d == base || d.endsWith(".$base")) continue // replaced by the fresh ones below
                    keep.add(line)
                }
            }
            val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
            val sb = StringBuilder("# Netscape HTTP Cookie File\n")
            for (l in keep) sb.append(l).append('\n')
            for ((k, v) in jar) sb.append('.').append(base).append("\tTRUE\t/\tTRUE\t").append(expiry).append('\t').append(k).append('\t').append(v).append('\n')
            file.writeText(sb.toString())

            Settings.update { it.copy(useCookies = true) }
            LogStore.i("browser", "Login saved for $base (${jar.size} cookies)")
            Result.success(base to jar.size)
        } catch (t: Throwable) {
            LogStore.e("browser", "Could not save the login cookies", t = t)
            Result.failure(t)
        }
    }
}
