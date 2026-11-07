package com.anas.streamsnap.engine

import android.content.Context
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.util.LogStore
import com.anas.streamsnap.util.Utils
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap

/** What we learn about one video before downloading it (preview card). */
data class VideoMeta(
    val url: String,
    val title: String,
    val thumbnail: String,
    val durationSec: Long,
    val site: String,
    val uploader: String,
    val sizes: Map<Int, Long>,   // height -> video size (0 = unknown)
    val audioSize: Long,
)

sealed class FetchResult {
    data class Single(val meta: VideoMeta) : FetchResult()
    data class Playlist(val title: String, val entries: List<VideoMeta>) : FetchResult()
    data class Error(val message: String, val raw: String) : FetchResult()
}

data class DownloadOutcome(
    val ok: Boolean,
    val filePath: String = "",
    val message: String = "",
    val raw: String = "",
)

/**
 * ALL calls to the youtubedl-android library live in this one file, so an engine
 * problem is always fixed by replacing just this file.
 */
object Engine {
    private val _state = MutableStateFlow("starting") // starting | ready | error
    val state: StateFlow<String> = _state.asStateFlow()

    @Volatile var lastError: String = ""
    @Volatile private var ready = false
    private val initLock = Any()

    /** Best guess of the file each running download is writing (used to clean up after Cancel). */
    private val partials = ConcurrentHashMap<String, String>()

    /** Downloads the user paused / cancelled: they must NOT be retried with other settings. */
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    /** True while yt-dlp is being replaced; fetches and downloads wait so they never read a half-written file. */
    @Volatile private var updating = false

    /** Set when the running yt-dlp does not know one of the extra YouTube options (older engine). */
    @Volatile private var extrasBroken = false

    @Volatile private var qjsCache: String? = null

    // ---------------------------------------------------------------- init
    fun ensureInit(ctx: Context): Boolean {
        if (ready) return true
        synchronized(initLock) {
            if (ready) return true
            try {
                YoutubeDL.getInstance().init(ctx.applicationContext)
            } catch (t: Throwable) {
                lastError = t.message ?: t.toString()
                _state.value = "error"
                LogStore.e("engine", "Download engine failed to start", t = t)
                return false
            }
            try {
                FFmpeg.getInstance().init(ctx.applicationContext)
            } catch (t: Throwable) {
                LogStore.e("engine", "FFmpeg failed to start (merging video+audio and MP3 may not work)", t = t)
            }
            ready = true
            _state.value = "ready"
            val v = versionString()
            LogStore.engineVersion = v
            LogStore.i("engine", "Engine ready (yt-dlp $v)")
            LogStore.i("engine", "JS runtime for YouTube: ${qjsPath(ctx) ?: "not found (engine will use its own)"}")
            cleanOldInfo(ctx)
            return true
        }
    }

    fun versionString(): String = try {
        val r = YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") })
        r.out.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "unknown"
    } catch (t: Throwable) {
        "unknown"
    }

    private fun waitWhileUpdating() {
        var n = 0
        while (updating && n < 240) {
            try { Thread.sleep(500) } catch (_: InterruptedException) { return }
            n++
        }
    }

    // ---------------------------------------------------------------- retry plans
    private class Plan(
        val label: String,
        val extra: List<Pair<String, String?>>,
        /** Download from the page info already read when the link was pasted (skips the slow second extraction). */
        val useInfo: Boolean = false,
        /** YouTube fast route (no JavaScript solving). If it fails the slower full route takes over. */
        val fast: Boolean = false,
    )

    // YouTube "fast mode": this player client needs no JavaScript solving, which is what takes minutes on a phone.
    private fun ytFastPlan() = Plan(
        "fast YouTube (no JavaScript)",
        listOf("--extractor-args" to "youtube:player_client=android_vr"),
        fast = true,
    )

    // A failure of the fast route that the full route cannot fix either: stop at once instead of waiting minutes.
    private val fastStopRx = Regex(
        "private video|login required|unsupported url|does not exist|has been removed|removed by the uploader|" +
            "not available in your country|members-only|copyright|age-restricted",
        RegexOption.IGNORE_CASE,
    )

    // ---------------------------------------------------------------- saved page info
    private const val INFO_MAX_AGE_MS = 3L * 3600 * 1000

    private fun infoFile(ctx: Context, url: String): File {
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val dir = File(ctx.cacheDir, "info")
        dir.mkdirs()
        return File(dir, h.take(20) + ".json")
    }

    /** The info file for this link when it is still fresh (video links expire after a few hours). */
    private fun freshInfoFile(ctx: Context, url: String): File? {
        val f = infoFile(ctx, url)
        return if (f.exists() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < INFO_MAX_AGE_MS) f else null
    }

    private fun cleanOldInfo(ctx: Context) {
        try {
            File(ctx.cacheDir, "info").listFiles()?.forEach {
                if (System.currentTimeMillis() - it.lastModified() > 6L * 3600 * 1000) it.delete()
            }
        } catch (_: Throwable) { }
    }

    private fun YoutubeDLRequest.addAll(extra: List<Pair<String, String?>>) {
        for ((k, v) in extra) {
            if (v == null) addOption(k) else addOption(k, v)
        }
    }

    private val optionErrRx = Regex("no such option|unrecognized arguments|unknown option", RegexOption.IGNORE_CASE)

    // Problems a retry cannot fix (login, private, removed ...).
    private val noRetryRx = Regex(
        "login required|rate-limit|cookies|unsupported url|private video|video unavailable|this video is|" +
            "not available in your country|age-restricted|sign in to|members-only|copyright|" +
            "removed by the uploader|has been removed|does not exist",
        RegexOption.IGNORE_CASE,
    )

    // Network-type problems: a different connection style (IPv4, legacy TLS) sometimes fixes them.
    private val netRx = Regex(
        "closed file|connection reset|reset by peer|timed out|timeout|transporterror|unable to download webpage|" +
            "remote end closed|incomplete ?read|ssl|eof occurred|network is unreachable|temporary failure|" +
            "errno 1\\d\\d|http error 403|http error 5\\d\\d|connection aborted|connection refused|name or service not known",
        RegexOption.IGNORE_CASE,
    )

    // YouTube refusing the video data (SABR / 403 / missing formats): other player clients may work.
    private val ytBlockRx = Regex(
        "403|sabr|requested format is not available|unable to download video data|http error 4\\d\\d|po token",
        RegexOption.IGNORE_CASE,
    )

    private fun shouldRetry(msg: String, youtube: Boolean, download: Boolean): Boolean {
        if (noRetryRx.containsMatchIn(msg)) return false
        if (netRx.containsMatchIn(msg)) return true
        return youtube && download && ytBlockRx.containsMatchIn(msg)
    }

    private val credRx = Regex("""(://)[^/\s:@]+:[^/\s@]+@""")

    /** Removes lines/parts that could expose a proxy password before the text goes into the Log. */
    private fun cleanRaw(s: String): String =
        s.lineSequence().filterNot { it.contains("Command-line config", true) }.joinToString("\n")
            .replace(credRx, "$1***:***@")

    private fun failureText(firstRaw: String, lastRaw: String, attempts: List<String>, stack: String): String {
        val sb = StringBuilder()
        sb.append(cleanRaw(lastRaw))
        if (firstRaw != lastRaw) {
            sb.append("\n\n--- first attempt ---\n").append(cleanRaw(firstRaw).take(2500))
        }
        if (attempts.isNotEmpty()) {
            sb.append("\n\n--- attempts ---\n").append(attempts.joinToString("\n"))
        }
        if (stack.isNotBlank()) sb.append("\n\n").append(stack)
        return sb.toString()
    }

    // ---------------------------------------------------------------- site specific extras
    private fun isYoutube(url: String): Boolean {
        val h = Utils.hostOf(url).lowercase()
        return h == "youtu.be" || h.endsWith("youtube.com") || h.endsWith("youtube-nocookie.com")
    }

    /**
     * YouTube needs a small JavaScript program to be solved before it hands out the video data.
     * We give yt-dlp the QuickJS runtime that ships with the engine (if we can find it) and allow it
     * to fetch its solver script from GitHub.
     */
    private fun siteExtras(ctx: Context, url: String): List<Pair<String, String?>> {
        if (extrasBroken || !isYoutube(url)) return emptyList()
        val out = ArrayList<Pair<String, String?>>()
        out.add("--remote-components" to "ejs:github")
        qjsPath(ctx)?.let { out.add("--js-runtimes" to "quickjs:$it") }
        return out
    }

    private fun qjsPath(ctx: Context): String? {
        qjsCache?.let { return it.ifEmpty { null } }
        var found: String? = null
        try {
            val roots = listOf(
                File(ctx.applicationInfo.nativeLibraryDir),
                File(ctx.noBackupFilesDir, "youtubedl-android"),
                File(ctx.filesDir, "youtubedl-android"),
            )
            for (r in roots) {
                if (!r.exists()) continue
                val hit = r.walkTopDown().maxDepth(7).firstOrNull { f ->
                    val n = f.name.lowercase()
                    f.isFile && !n.contains(".zip") && f.length() > 100_000 &&
                        (n == "qjs" || n == "quickjs" || n.startsWith("libqjs") || n.startsWith("libquickjs")) && f.canExecute()
                }
                if (hit != null) { found = hit.absolutePath; break }
            }
        } catch (_: Throwable) { }
        qjsCache = found ?: ""
        return found
    }

    // ---------------------------------------------------------------- fetch info
    private fun urlHasVideoAndList(url: String): Boolean =
        (url.contains("watch?v=") || url.contains("&v=") || url.contains("youtu.be/")) && url.contains("list=")

    fun fetchInfo(ctx: Context, url: String): FetchResult {
        if (!ensureInit(ctx)) return FetchResult.Error("Engine not ready", lastError)
        waitWhileUpdating()

        val yt = isYoutube(url)
        val plans = buildList {
            if (yt && Settings.value.ytFast && !extrasBroken) add(ytFastPlan())
            add(Plan("normal", emptyList()))
            add(
                Plan(
                    "IPv4 + legacy TLS",
                    listOf("--force-ipv4" to null, "--legacy-server-connect" to null, "--retries" to "3"),
                ),
            )
            add(
                Plan(
                    "diagnostic (verbose)",
                    listOf("--force-ipv4" to null, "--legacy-server-connect" to null, "--retries" to "1", "--verbose" to null),
                ),
            )
        }
        val attempts = ArrayList<String>()
        var firstRaw = ""
        var lastRaw = ""
        var stack = ""
        var idx = 0
        var optionRetried = false
        while (idx < plans.size) {
            val plan = plans[idx]
            try {
                val t0 = System.currentTimeMillis()
                val r = fetchOnce(ctx, url, plan)
                // The time is logged so a slow link can be traced from the Log screen.
                LogStore.i("fetch", "Link read in ${(System.currentTimeMillis() - t0) / 1000} s (${plan.label})", url)
                return r
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                // A failed fast route is not "the" error: the full route runs next and tells the real story.
                if (firstRaw.isEmpty() && !plan.fast) firstRaw = msg
                lastRaw = msg
                stack = LogStore.stack(t)
                if (!extrasBroken && !optionRetried && optionErrRx.containsMatchIn(msg)) {
                    extrasBroken = true
                    optionRetried = true
                    LogStore.w("engine", "This engine does not know an extra YouTube option; trying again without it", url, shortMessage(msg))
                    continue
                }
                attempts.add("${idx + 1}) ${plan.label}: ${shortMessage(msg)}")
                val stopHere = if (plan.fast) fastStopRx.containsMatchIn(msg) else !shouldRetry(msg, yt, false)
                if (idx == plans.size - 1 || stopHere) {
                    if (firstRaw.isEmpty()) firstRaw = msg
                    break
                }
                LogStore.w("fetch", "Trying again (${plans[idx + 1].label})", url, shortMessage(msg))
                idx++
            }
        }
        return FetchResult.Error(shortMessage(firstRaw), failureText(firstRaw, lastRaw, attempts, stack))
    }

    private fun fetchOnce(ctx: Context, url: String, plan: Plan): FetchResult {
        val req = YoutubeDLRequest(url)
        req.addOption("--dump-single-json")
        req.addOption("--flat-playlist")
        if (plan.extra.none { it.first == "--verbose" }) req.addOption("--no-warnings")
        req.addOption("--playlist-end", "500")
        if (urlHasVideoAndList(url)) req.addOption("--no-playlist")
        applyCommon(ctx, req, Settings.value, url)
        req.addAll(plan.extra)

        val out = YoutubeDL.getInstance().execute(req).out
        val start = out.indexOf('{')
        if (start < 0) return FetchResult.Error("No information returned for this link", out)
        val json = out.substring(start)
        val o = JSONObject(json)

        return if (o.optString("_type") == "playlist" && o.optJSONArray("entries") != null) {
            val arr = o.getJSONArray("entries")
            val list = ArrayList<VideoMeta>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val u = entryUrl(e) ?: continue
                list.add(parseVideo(e, u))
            }
            FetchResult.Playlist(o.optString("title", "Playlist"), list)
        } else {
            // Keep what was read, so the download can start at once instead of reading the page again.
            try {
                if (json.length < 8_000_000) infoFile(ctx, url).writeText(json)
            } catch (_: Throwable) { }
            FetchResult.Single(parseVideo(o, o.optString("webpage_url", url).ifBlank { url }))
        }
    }

    private fun entryUrl(e: JSONObject): String? {
        val u = e.optString("url", "")
        if (u.startsWith("http")) return u
        val w = e.optString("webpage_url", "")
        if (w.startsWith("http")) return w
        val id = e.optString("id", "")
        if (id.isNotBlank() && e.optString("ie_key", "").equals("Youtube", true)) return "https://www.youtube.com/watch?v=$id"
        return null
    }

    private fun parseVideo(o: JSONObject, url: String): VideoMeta {
        val sizes = HashMap<Int, Long>()
        var audio = 0L
        val formats = o.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val h = f.optInt("height", 0)
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                var size = f.optLong("filesize", 0L)
                if (size <= 0) size = f.optLong("filesize_approx", 0L)
                if (h > 0 && vcodec != "none") {
                    val old = sizes[h] ?: 0L
                    sizes[h] = maxOf(old, size)
                } else if (vcodec == "none" && acodec != "none") {
                    audio = maxOf(audio, size)
                }
            }
        }
        var thumb = o.optString("thumbnail", "")
        if (thumb.isBlank()) {
            val th = o.optJSONArray("thumbnails")
            if (th != null && th.length() > 0) thumb = th.optJSONObject(th.length() - 1)?.optString("url", "") ?: ""
        }
        val site = o.optString("extractor_key", "").ifBlank { o.optString("ie_key", "") }
            .ifBlank { Utils.siteName(url) }
        return VideoMeta(
            url = url,
            title = o.optString("title", "").let { if (it == "null") "" else it },
            thumbnail = if (thumb == "null") "" else thumb,
            durationSec = o.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
            site = site,
            uploader = o.optString("uploader", "").ifBlank { o.optString("channel", "") }.let { if (it == "null") "" else it },
            sizes = sizes,
            audioSize = audio,
        )
    }

    // ---------------------------------------------------------------- download
    private val destRx = Regex("""(?:\[download] Destination:|\[ExtractAudio] Destination:)\s*(.+)$""")
    private val mergeRx = Regex("""\[Merger] Merging formats into "(.+)"""")
    private val existsRx = Regex("""\[download]\s+(.+?)\s+has already been downloaded""")
    private val speedRx = Regex("""\bat\s+([\d.,]+\s*[KMGT]?i?B/s)""")

    fun parseSpeed(line: String): String? = speedRx.find(line)?.groupValues?.get(1)?.replace(" ", "")

    private fun pathFromLine(line: String): String? {
        mergeRx.find(line)?.let { return it.groupValues[1] }
        destRx.find(line.trim())?.let { return it.groupValues[1].trim() }
        existsRx.find(line)?.let { return it.groupValues[1].trim() }
        return null
    }

    fun download(
        ctx: Context,
        item: DownloadItem,
        p: Prefs,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): DownloadOutcome {
        if (!ensureInit(ctx)) return DownloadOutcome(false, message = "Engine not ready", raw = lastError)
        waitWhileUpdating()
        cancelled.remove(item.id)
        val dir = Utils.outputDir(ctx, p.folder)
        Utils.applyNoMedia(dir, p.hiddenFolder)
        val startedAt = System.currentTimeMillis()

        val yt = isYoutube(item.url)
        val hasInfo = freshInfoFile(ctx, item.url) != null
        val basePlans = if (yt) {
            buildList {
                // Without saved page info, start with the fast no-JavaScript route (the saved info already came from it or from the full route).
                if (p.ytFast && !hasInfo && !extrasBroken) add(ytFastPlan())
                add(Plan("normal", emptyList()))
                add(
                    Plan(
                        "other YouTube clients + IPv4",
                        listOf(
                            "--extractor-args" to "youtube:player_client=default,android,web_embedded,mweb",
                            "--force-ipv4" to null,
                        ),
                    ),
                )
            }
        } else {
            listOf(
                Plan("normal", emptyList()),
                Plan("IPv4 + legacy TLS", listOf("--force-ipv4" to null, "--legacy-server-connect" to null)),
            )
        }
        // First try the page info that was already read when the link was pasted (much faster to start).
        val plans = if (hasInfo) {
            listOf(Plan("saved page info", emptyList(), useInfo = true)) + basePlans
        } else basePlans

        val attempts = ArrayList<String>()
        var firstRaw = ""
        var lastRaw = ""
        var stack = ""
        var idx = 0
        var optionRetried = false
        while (idx < plans.size) {
            val plan = plans[idx]
            try {
                val outcome = downloadOnce(ctx, item, p, dir, plan, startedAt, onProgress)
                if (plan.useInfo || outcome.ok) {
                    try { infoFile(ctx, item.url).delete() } catch (_: Throwable) { }
                }
                return outcome
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                if (plan.useInfo && !cancelled.contains(item.id)) {
                    // Not an error for the user: just read the page the normal way.
                    LogStore.w("download", "Saved page info did not work; reading the page again", item.url, shortMessage(msg))
                    try { infoFile(ctx, item.url).delete() } catch (_: Throwable) { }
                    idx++
                    continue
                }
                if (firstRaw.isEmpty() && !plan.fast) firstRaw = msg
                lastRaw = msg
                stack = LogStore.stack(t)
                if (cancelled.contains(item.id)) { // paused / cancelled by the user
                    if (firstRaw.isEmpty()) firstRaw = msg
                    break
                }
                if (!extrasBroken && !optionRetried && optionErrRx.containsMatchIn(msg)) {
                    extrasBroken = true
                    optionRetried = true
                    LogStore.w("engine", "This engine does not know an extra YouTube option; trying again without it", item.url, shortMessage(msg))
                    continue
                }
                attempts.add("${idx + 1}) ${plan.label}: ${shortMessage(msg)}")
                val stopHere = if (plan.fast) fastStopRx.containsMatchIn(msg) else !shouldRetry(msg, yt, true)
                if (idx == plans.size - 1 || stopHere) {
                    if (firstRaw.isEmpty()) firstRaw = msg
                    break
                }
                LogStore.w("download", "Trying again (${plans[idx + 1].label})", item.url, shortMessage(msg))
                idx++
            }
        }
        return DownloadOutcome(false, "", shortMessage(firstRaw), failureText(firstRaw, lastRaw, attempts, stack))
    }

    private fun downloadOnce(
        ctx: Context,
        item: DownloadItem,
        p: Prefs,
        dir: File,
        plan: Plan,
        startedAt: Long,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): DownloadOutcome {
        var finalPath: String? = null
        val saved = if (plan.useInfo) freshInfoFile(ctx, item.url) else null
        if (plan.useInfo && saved == null) throw IllegalStateException("No saved page info for this link")
        // With saved info the engine skips the slow page reading and starts downloading straight away.
        val req = if (saved != null) YoutubeDLRequest(emptyList<String>()) else YoutubeDLRequest(item.url)
        if (saved != null) {
            req.addOption("--load-info-json", saved.absolutePath)
            LogStore.i("download", "Starting from the already read page info (no second page read)", item.url)
        }
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("-N", "4")
        req.addOption("-P", dir.absolutePath)
        req.addOption("-o", p.nameTemplate.ifBlank { "%(title).120B.%(ext)s" })
        applyQuality(req, item.quality)
        if (p.speedLimitKb > 0) req.addOption("-r", "${p.speedLimitKb}K")
        if (p.saveThumb) {
            req.addOption("--write-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
        }
        if (p.saveSubs) {
            req.addOption("--write-subs")
            req.addOption("--sub-langs", "en.*,ur.*")
            req.addOption("--convert-subs", "srt")
        }
        applyCommon(ctx, req, p, item.url)
        req.addAll(plan.extra)

        YoutubeDL.getInstance().execute(req, item.id) { progress, eta, line ->
            val path = pathFromLine(line)
            if (path != null) {
                partials[item.id] = path
                if (Utils.isMediaFile(path)) finalPath = path
            }
            onProgress(progress, eta, line)
        }

        var path = finalPath
        if (path == null || !File(path).exists()) path = newestMedia(dir, startedAt)
        partials.remove(item.id)
        return if (path == null) DownloadOutcome(true, "", "Finished, but the file was not found in ${dir.absolutePath}")
        else DownloadOutcome(true, path)
    }

    private fun newestMedia(dir: File, since: Long): String? =
        dir.listFiles { f -> f.isFile && Utils.isMediaFile(f.name) && f.lastModified() >= since - 3000 }
            ?.maxByOrNull { it.lastModified() }?.absolutePath

    fun cancel(id: String) {
        cancelled.add(id)
        try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Throwable) { }
    }

    fun cleanupPartial(id: String) {
        val p = partials.remove(id) ?: return
        try {
            for (s in listOf(p, "$p.part", "$p.ytdl", "$p.temp")) {
                val f = File(s)
                if (f.exists()) f.delete()
            }
        } catch (_: Throwable) { }
    }

    // ---------------------------------------------------------------- options
    private fun applyCommon(ctx: Context, req: YoutubeDLRequest, p: Prefs, url: String) {
        req.addOption("--socket-timeout", "30")
        req.addOption("--retries", "10")
        if (p.proxy.isNotBlank()) req.addOption("--proxy", p.proxy.trim())
        if (p.useCookies) {
            val c = cookieFile(ctx)
            if (c.exists() && c.length() > 0) req.addOption("--cookies", c.absolutePath)
        }
        req.addAll(siteExtras(ctx, url))
    }

    fun cookieFile(ctx: Context): File = File(ctx.filesDir, "cookies.txt")

    /**
     * "Best" prefers H.264 so the video plays on every phone (VP9/AV1 can stutter on older phones).
     * A number means "closest to this height, not above it; if the video has nothing that low,
     * the next higher one".
     */
    private fun applyQuality(req: YoutubeDLRequest, q: String) {
        when (q) {
            "audio" -> {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", "0")
            }
            "m4a" -> {
                req.addOption("-f", "ba[ext=m4a]/ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", "best")
            }
            "smallest" -> {
                req.addOption("-f", "bv*+ba/b")
                req.addOption("-S", "+size,+br,+res,+fps")
                req.addOption("--merge-output-format", "mp4")
            }
            "best" -> {
                req.addOption("-f", "bv*+ba/b")
                req.addOption("-S", "vcodec:h264,res,acodec:m4a")
                req.addOption("--merge-output-format", "mp4")
            }
            else -> {
                val h = q.toIntOrNull() ?: 720
                req.addOption("-f", "bv*+ba/b")
                req.addOption("-S", if (h >= 1440) "res:$h" else "res:$h,vcodec:h264,acodec:m4a")
                req.addOption("--merge-output-format", if (h >= 1440) "mkv" else "mp4")
            }
        }
    }

    // ---------------------------------------------------------------- update
    /**
     * Updates yt-dlp. Done through reflection on purpose: the library's update API changed names
     * between versions, and a name mismatch must never stop the whole app from compiling.
     */
    fun update(ctx: Context, channel: String): String {
        if (!ensureInit(ctx)) return "Engine not ready: $lastError"
        val inst = YoutubeDL.getInstance()
        val cands = inst.javaClass.methods.filter { it.name == "updateYoutubeDL" }
        if (cands.isEmpty()) return "This engine version has no update function"

        val wanted = if (channel == "nightly") "NIGHTLY" else "STABLE"
        val two = cands.firstOrNull { it.parameterTypes.size == 2 }
        val chan = two?.let { findChannel(it.parameterTypes[1], wanted) }
        val method = if (two != null && chan != null) two
        else cands.firstOrNull { it.parameterTypes.size == 1 } ?: two ?: cands.first()
        val args: Array<Any?> = when (method.parameterTypes.size) {
            1 -> arrayOf(ctx.applicationContext)
            else -> arrayOf(ctx.applicationContext, chan)
        }
        if (method.parameterTypes.size >= 2 && chan == null) {
            return "Could not pick the $wanted update channel for this engine version"
        }
        if (method.parameterTypes.size == 1 && wanted == "NIGHTLY") {
            LogStore.w("engine", "This engine version has no channel choice: updating the default channel")
        }

        updating = true
        try {
            val res = try {
                method.invoke(inst, *args)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
            extrasBroken = false
            val v = versionString()
            LogStore.engineVersion = v
            return "${res ?: "done"} (yt-dlp $v)"
        } finally {
            updating = false
        }
    }

    private fun findChannel(cls: Class<*>, name: String): Any? {
        try { cls.getField(name).get(null)?.let { return it } } catch (_: Throwable) { }
        try { cls.getField("_$name").get(null)?.let { return it } } catch (_: Throwable) { }
        try {
            val comp = cls.getField("Companion").get(null)
            comp?.javaClass?.methods?.firstOrNull { it.name.equals("get$name", true) || it.name.equals("get_$name", true) }
                ?.invoke(comp)?.let { return it }
        } catch (_: Throwable) { }
        try { cls.enumConstants?.firstOrNull { (it as Enum<*>).name.contains(name, true) }?.let { return it } } catch (_: Throwable) { }
        return null
    }

    /** Days since the engine's release date (version looks like 2025.11.12), or null when unknown. */
    fun versionAgeDays(version: String): Long? {
        val m = Regex("""^(\d{4})\.(\d{2})\.(\d{2})""").find(version.trim()) ?: return null
        val (y, mo, d) = m.destructured
        val cal = GregorianCalendar(y.toInt(), mo.toInt() - 1, d.toInt())
        return (System.currentTimeMillis() - cal.timeInMillis) / 86_400_000L
    }

    /**
     * Websites (YouTube above all) change every few weeks, so an old engine stops working.
     * When the engine is more than 3 weeks old this updates it quietly in the background.
     */
    fun autoUpdateIfOld(ctx: Context) {
        try {
            if (!ensureInit(ctx)) return
            val age = versionAgeDays(LogStore.engineVersion) ?: return
            if (age < 21) return
            val p = Settings.value
            val now = System.currentTimeMillis()
            if (now - p.lastEngineCheck < 12L * 3600 * 1000) return
            if (!Utils.isOnline(ctx)) return
            if (p.wifiOnly && !Utils.isWifi(ctx)) return
            Settings.update { it.copy(lastEngineCheck = now) }
            LogStore.i("engine", "Engine is $age days old: updating it automatically (${p.engineChannel})")
            val msg = update(ctx, p.engineChannel)
            LogStore.i("engine", "Automatic engine update: $msg")
            Notifier.engineUpdate(ctx, msg.take(120))
        } catch (t: Throwable) {
            LogStore.e("engine", "Automatic engine update failed", t = (t.cause ?: t))
        }
    }

    /** Wipes the engine's working folder; the bundled yt-dlp is extracted again on the next start. */
    fun resetFiles(ctx: Context): Boolean = try {
        // The library keeps its files in noBackupFilesDir (older builds used filesDir): clear both.
        val dirs = listOf(File(ctx.noBackupFilesDir, "youtubedl-android"), File(ctx.filesDir, "youtubedl-android"))
        var ok = true
        for (d in dirs) if (d.exists() && !d.deleteRecursively()) ok = false
        ok
    } catch (_: Throwable) { false }

    // ---------------------------------------------------------------- helpers
    /** One readable line out of yt-dlp's multi-line error text. */
    fun shortMessage(raw: String): String {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val err = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.firstOrNull() ?: raw
        return err.removePrefix("ERROR:").trim().take(220)
    }
}
