package com.anas.streamsnap.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.anas.streamsnap.data.tr3
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
    /** Copies [text] to the clipboard and shows a short "Copied" toast. Never throws. */
    fun copyToClipboard(ctx: Context, text: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("StreamSnap", text))
            Toast.makeText(ctx.applicationContext, tr3("Copied", "Copy ho gaya", "کاپی ہو گیا"), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }

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

    /**
     * Hidden folder for half-finished downloads (.part files, stream fragments), one sub-folder per queue item.
     * It sits on the same storage as the Download folder, so the finished file is moved, not copied.
     */
    fun tempRoot(ctx: Context): File {
        val base: File = if (hasStoragePermission(ctx)) Environment.getExternalStorageDirectory()
        else (ctx.getExternalFilesDir(null) ?: ctx.filesDir)
        val dir = File(base, "StreamSnap/.tmp")
        try {
            if (!dir.exists()) dir.mkdirs()
            val nm = File(dir, ".nomedia")
            if (!nm.exists()) nm.createNewFile()
        } catch (_: Exception) { }
        return dir
    }

    fun applyNoMedia(dir: File, hidden: Boolean) {
        try {
            val f = File(dir, ".nomedia")
            if (hidden) { if (!f.exists()) f.createNewFile() } else if (f.exists()) f.delete()
        } catch (_: Exception) { }
    }

    /** Real size of everything inside [dir] (all sub folders), measured on the disk. */
    fun dirSize(dir: File): Long = try {
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (e: Exception) { 0L }

    fun freeBytes(dir: File): Long = try {
        val probe = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: dir
        StatFs(probe.absolutePath).availableBytes
    } catch (e: Exception) { Long.MAX_VALUE }

    fun totalBytes(dir: File): Long = try {
        val probe = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: dir
        StatFs(probe.absolutePath).totalBytes
    } catch (e: Exception) { 0L }

    // ---------- Network ----------
    /**
     * True when the phone has any usable connection. Deliberately forgiving: with a VPN the "active"
     * network can be the VPN itself (or briefly nothing while it reconnects), and some VPN apps do not
     * report the internet capability the normal way. The real request will fail by itself if there is
     * truly no internet, so a wrong "offline" here (which would block downloads) is worse than a wrong "online".
     */
    @Suppress("DEPRECATION")
    fun isOnline(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork
            val caps = if (n != null) cm.getNetworkCapabilities(n) else null
            if (caps != null && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            ) return true
            // Any other network that can reach the internet (also covers a VPN that is switching).
            for (other in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(other) ?: continue
                if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                ) return true
            }
            // Old-style check as the last word.
            cm.activeNetworkInfo?.isConnected == true
        } catch (_: Throwable) {
            true // cannot tell: let the real request decide
        }
    }

    /** One line describing every network, for the log (helps when "No internet" shows up wrongly). */
    @Suppress("DEPRECATION")
    fun netDebug(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val parts = cm.allNetworks.map { net ->
            val c = cm.getNetworkCapabilities(net)
            val t = buildList {
                if (c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
                if (c?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cell")
                if (c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
                if (c?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("eth")
            }.joinToString("+").ifEmpty { "?" }
            val internet = c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val valid = c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            "$t" + (if (net == active) "*" else "") + "[internet=$internet,validated=$valid]"
        }
        "networks: " + (if (parts.isEmpty()) "none" else parts.joinToString(" "))
    } catch (t: Throwable) {
        "networks: unreadable (${t.javaClass.simpleName})"
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

    /** A name that is safe as a file name on Android storage (no / \\ : * ? " < > |). */
    fun safeFileName(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("\\s+"), " ").trim().trim('.').trim().take(150)

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
