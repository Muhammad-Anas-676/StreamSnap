package com.anas.streamsnap.util

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val tag: String,          // fetch, download, engine, queue, network, crash ...
    val message: String,
    val url: String = "",
    val detail: String = "",  // exact error text + stack trace
)

/**
 * Activity log. Keeps the EXACT error text (only secrets such as cookies / tokens / passwords
 * are masked) so it can be copied and sent for debugging.
 */
object LogStore {
    private const val MAX_KEEP = 800
    private const val MAX_DETAIL = 24_000

    private lateinit var file: File
    private lateinit var crashFile: File
    private var appVersion = "?"
    @Volatile var engineVersion: String = "?"

    private val io = Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()
    private val lock = Any()

    private val timeFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        file = File(app.filesDir, "logs.jsonl")
        crashFile = File(app.filesDir, "crash_pending.txt")
        appVersion = try {
            @Suppress("DEPRECATION")
            val pi = app.packageManager.getPackageInfo(app.packageName, 0)
            @Suppress("DEPRECATION")
            "${pi.versionName} (${pi.versionCode})"
        } catch (e: Exception) { "?" }

        val loaded = ArrayList<LogEntry>()
        try {
            if (file.exists()) {
                file.readLines().takeLast(MAX_KEEP).forEach { line ->
                    try {
                        val o = JSONObject(line)
                        loaded.add(
                            LogEntry(
                                time = o.optLong("t"),
                                level = try { LogLevel.valueOf(o.optString("l", "INFO")) } catch (e: Exception) { LogLevel.INFO },
                                tag = o.optString("g"),
                                message = o.optString("m"),
                                url = o.optString("u"),
                                detail = o.optString("d"),
                            )
                        )
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        _entries.value = loaded

        installCrashHandler()
        ingestPendingCrash()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashFile.writeText("${System.currentTimeMillis()}\n${thread.name}\n${stack(throwable)}")
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun ingestPendingCrash() {
        try {
            if (!crashFile.exists()) return
            val lines = crashFile.readText().lines()
            val t = lines.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
            val thread = lines.getOrNull(1) ?: "?"
            val trace = lines.drop(2).joinToString("\n")
            crashFile.delete()
            val first = trace.lines().firstOrNull() ?: "crash"
            append(LogEntry(t, LogLevel.ERROR, "crash", "App crashed on the previous run (thread: $thread): $first", "", trace.take(MAX_DETAIL)))
        } catch (_: Exception) { }
    }

    // ---- API ----
    fun i(tag: String, msg: String, url: String = "", detail: String = "") = add(LogLevel.INFO, tag, msg, url, detail)
    fun w(tag: String, msg: String, url: String = "", detail: String = "") = add(LogLevel.WARN, tag, msg, url, detail)

    /** [raw] is the exact text from the engine / system; [t] adds the full stack trace. */
    fun e(tag: String, msg: String, url: String = "", t: Throwable? = null, raw: String = "") {
        val d = buildString {
            if (raw.isNotBlank()) append(raw.trimEnd())
            if (t != null) {
                if (isNotEmpty()) append("\n\n")
                append(stack(t))
            }
        }
        add(LogLevel.ERROR, tag, msg, url, d)
    }

    private fun add(level: LogLevel, tag: String, msg: String, url: String, detail: String) {
        append(LogEntry(System.currentTimeMillis(), level, tag, redact(msg), redact(url), redact(detail).take(MAX_DETAIL)))
    }

    private fun append(e: LogEntry) {
        synchronized(lock) {
            val list = _entries.value + e
            _entries.value = if (list.size > MAX_KEEP + 200) list.takeLast(MAX_KEEP) else list
        }
        io.execute {
            try {
                val o = JSONObject()
                o.put("t", e.time); o.put("l", e.level.name); o.put("g", e.tag)
                o.put("m", e.message); o.put("u", e.url); o.put("d", e.detail)
                file.appendText(o.toString() + "\n")
                if (file.length() > 2_000_000) rewrite()
            } catch (_: Exception) { }
        }
    }

    private fun rewrite() {
        try {
            val keep = _entries.value.takeLast(MAX_KEEP)
            file.writeText(keep.joinToString("\n") { x ->
                JSONObject().apply {
                    put("t", x.time); put("l", x.level.name); put("g", x.tag)
                    put("m", x.message); put("u", x.url); put("d", x.detail)
                }.toString()
            } + "\n")
        } catch (_: Exception) { }
    }

    fun clear() {
        synchronized(lock) { _entries.value = emptyList() }
        io.execute { try { file.writeText("") } catch (_: Exception) { } }
    }

    // ---- Formatting (what the Copy buttons put on the clipboard) ----
    fun fmtTime(t: Long): String = timeFmt.get()!!.format(Date(t))

    fun format(e: LogEntry): String = buildString {
        append('[').append(fmtTime(e.time)).append("] ").append(e.level.name).append(" · ").append(e.tag).append('\n')
        if (e.url.isNotBlank()) append("URL: ").append(e.url).append('\n')
        append(e.message).append('\n')
        if (e.detail.isNotBlank()) {
            append("--- exact error / details ---\n")
            append(e.detail.trimEnd()).append('\n')
        }
    }

    fun header(): String =
        "StreamSnap $appVersion | engine ${engineVersion} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
            "${Build.MANUFACTURER} ${Build.MODEL} | ABI ${Build.SUPPORTED_ABIS.joinToString()}\n"

    fun formatAll(list: List<LogEntry>): String =
        header() + "\n" + list.joinToString("\n----------------------------------------\n") { format(it) }

    fun formatOne(e: LogEntry): String = header() + "\n" + format(e)

    // ---- Helpers ----
    fun stack(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private val rxHeader = Regex("(?i)\\b(cookie|set-cookie|authorization|proxy-authorization)\\s*[:=]\\s*[^\\r\\n]+")
    private val rxParam = Regex("(?i)\\b(password|passwd|pwd|token|access_token|api[_-]?key|secret|signature|auth)=([^&\\s\"']+)")
    private val rxBearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-~+/=]+")
    private val rxProxyCreds = Regex("(?i)\\b(https?|socks[45h]*)://[^/\\s:@]+:[^/\\s@]+@")

    /** Masks only secrets. Everything else stays exactly as the engine printed it. */
    fun redact(s: String): String {
        if (s.isEmpty()) return s
        var r = rxHeader.replace(s) { "${it.groupValues[1]}: ***" }
        r = rxParam.replace(r) { "${it.groupValues[1]}=***" }
        r = rxBearer.replace(r, "Bearer ***")
        r = rxProxyCreds.replace(r) { "${it.groupValues[1]}://***@" }
        return r
    }
}
