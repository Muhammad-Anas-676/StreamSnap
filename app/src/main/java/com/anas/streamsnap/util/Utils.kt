package com.anas.streamsnap.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File
import java.net.URI
import java.text.DecimalFormat
import java.util.Locale

object Utils {
    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'()\[\]{}|\\^`]+)""")

    /** Pulls every http(s) link out of free text (one per line, space separated, or mixed with words). */
    fun extractUrls(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in urlRegex.findAll(text)) {
            var u = m.groupValues[1].trimEnd('.', ',', ';', ':', '!', '?')
            if (u.startsWith("www.", ignoreCase = true)) u = "https://$u"
            if (u.length > 10) out.add(u)
        }
        return out.toList()
    }

    fun normalizeUrl(u: String): String {
        var s = u.trim()
        val hash = s.indexOf('#')
        if (hash >= 0) s = s.substring(0, hash)
        s = s.trimEnd('/')
        return s.lowercase(Locale.ROOT)
    }

    fun hostOf(url: String): String = try {
        (URI(url).host ?: "").removePrefix("www.").removePrefix("m.")
    } catch (e: Exception) {
        ""
    }

    fun siteName(url: String): String {
        val h = hostOf(url)
        if (h.isBlank()) return ""
        val parts = h.split('.')
        val core = if (parts.size >= 2) parts[parts.size - 2] else parts[0]
        return core.replaceFirstChar { it.uppercase() }
    }

    fun fmtBytes(b: Long): String {
        if (b <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return DecimalFormat(if (i <= 1) "#" else "#.#").format(v) + " " + units[i]
    }

    fun fmtDuration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun fmtEta(sec: Long): String = if (sec < 0) "" else fmtDuration(sec.coerceAtLeast(1))

    // ---------- Storage ----------
    fun hasStoragePermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Default location is <internal storage>/StreamSnap/Download (falls back to app folder without permission). */
    fun outputDir(ctx: Context, folder: String): File {
        val base = if (hasStoragePermission(ctx)) Environment.getExternalStorageDirectory()
        else (ctx.getExternalFilesDir(null) ?: ctx.filesDir)
        val rel = folder.trim().trim('/').ifEmpty { "StreamSnap/Download" }
        val dir = File(base, rel)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun applyNoMedia(dir: File, hidden: Boolean) {
        try {
            val f = File(dir, ".nomedia")
            if (hidden) { if (!f.exists()) f.createNewFile() } else if (f.exists()) f.delete()
        } catch (_: Exception) { }
    }

    fun freeBytes(dir: File): Long = try {
        val probe = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: dir
        StatFs(probe.absolutePath).availableBytes
    } catch (e: Exception) { Long.MAX_VALUE }

    fun totalBytes(dir: File): Long = try {
        val probe = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: dir
        StatFs(probe.absolutePath).totalBytes
    } catch (e: Exception) { 0L }

    // ---------- Network ----------
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        // A VPN running over Wi-Fi counts as Wi-Fi when the connection is not metered.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !cm.isActiveNetworkMetered)
    }

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        else -> "*/*"
    }

    fun isMediaFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "webm", "m4v", "mov", "3gp", "avi", "flv", "ts",
            "mp3", "m4a", "opus", "ogg", "aac", "wav", "flac")
    }
}
