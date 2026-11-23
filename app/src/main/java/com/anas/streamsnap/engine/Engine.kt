package com.anas.streamsnap.engine

import android.content.Context
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.tr
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

    // YouTube "fast mode": the visionos player client needs no JavaScript solving and no PO token, which is what
    // takes minutes on a phone (up to 1080p). The older android_vr client lists formats but YouTube answers 403 when downloading them.
    private fun ytFastPlan() = Plan(
        "fast YouTube (no JavaScript)",
        listOf("--extractor-args" to "youtube:player_client=visionos"),
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

    // The server cut the secure connection (some sites refuse apps that do not look like a browser).
    private val tlsCutRx = Regex("unexpected_eof|eof occurred|ssl:|connection reset|reset by peer", RegexOption.IGNORE_CASE)

    // "Too many requests": a retry right away is refused again, so wait a little first.
    private val rateLimitRx = Regex("http error 429|too many requests", RegexOption.IGNORE_CASE)

    private fun backoffIfLimited(msg: String) {
        if (rateLimitRx.containsMatchIn(msg)) {
            try { Thread.sleep(10_000) } catch (_: InterruptedException) { }
        }
    }

    /** True when trying again later cannot help (private / removed / login / full storage ...). Used by auto-retry. */
    fun isPermanentError(msg: String): Boolean =
        noRetryRx.containsMatchIn(msg) || fastStopRx.containsMatchIn(msg) ||
            Regex("no space left|unsupported url|no videos found", RegexOption.IGNORE_CASE).containsMatchIn(msg)

    /** True for "too many requests": the auto-retry waits longer. */
    fun isRateLimited(msg: String): Boolean = rateLimitRx.containsMatchIn(msg)

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
                backoffIfLimited(msg)
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
        if (!isYoutube(url)) {
            // The preview only needs title, picture and the quality list. Do not test every video server now
            // (a server that cuts the connection would turn a good link into "Requested format is not available").
            req.addOption("--no-check-formats")
            req.addOption("--ignore-no-formats-error")
        }
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
    /** yt-dlp finishes in the hidden temp folder, then moves the file to the Download folder and says so here. */
    private val moveRx = Regex("""\[MoveFiles] Moving file ".+" to "(.+)"$""")
    private val speedRx = Regex("""\bat\s+([\d.,]+\s*[KMGT]?i?B/s)""")

    fun parseSpeed(line: String): String? = speedRx.find(line)?.groupValues?.get(1)?.replace(" ", "")

    private fun pathFromLine(line: String): String? {
        moveRx.find(line.trim())?.let { return it.groupValues[1] }
        mergeRx.find(line)?.let { return it.groupValues[1] }
        destRx.find(line.trim())?.let { return it.groupValues[1].trim() }
        existsRx.find(line)?.let { return it.groupValues[1].trim() }
        return null
    }

    fun download(
        ctx: Context,
        item: DownloadItem,
        p: Prefs,
        onPhase: (String) -> Unit = {},
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): DownloadOutcome {
        if (!ensureInit(ctx)) return DownloadOutcome(false, message = "Engine not ready", raw = lastError)
        waitWhileUpdating()
        cancelled.remove(item.id)
        val baseDir = Utils.outputDir(ctx, p.folder)
        Utils.applyNoMedia(baseDir, p.hiddenFolder)
        val dir = if (p.siteFolders) File(baseDir, siteFolderName(item.url)).also { it.mkdirs() } else baseDir
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
                // Last try: ffmpeg downloads the video with its own TLS, which some video servers accept when the engine's is refused.
                Plan("ffmpeg downloader", listOf("--downloader" to "ffmpeg", "--no-check-formats" to null, "--force-ipv4" to null)),
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
                if (outcome.ok && outcome.filePath.isNotBlank() && customizing(item, p)) {
                    return applyCustomSettings(ctx, item, p, outcome.filePath, onPhase)
                }
                return outcome
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                if (plan.useInfo && !cancelled.contains(item.id)) {
                    // Not an error for the user: just read the page the normal way.
                    LogStore.w("download", "Saved page info did not work; reading the page again", item.url, shortMessage(msg))
                    try { infoFile(ctx, item.url).delete() } catch (_: Throwable) { }
                    // A connection that is cut (SSL EOF / reset) will be cut again by the "normal" run, so skip it.
                    idx += if (!yt && tlsCutRx.containsMatchIn(msg) && idx + 2 < plans.size) 2 else 1
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
                backoffIfLimited(msg)
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
        // Half-finished pieces (.part, stream fragments) stay in a hidden temp folder, one per item, so the Download folder stays clean.
        req.addOption("--paths", "temp:" + File(Utils.tempRoot(ctx), item.id).absolutePath)
        req.addOption("-o", outputTemplate(item, p))
        applyQuality(req, effectiveQuality(item, p))
        if (p.speedLimitKb > 0) req.addOption("-r", "${p.speedLimitKb}K")
        if (p.saveThumb) {
            req.addOption("--write-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
        }
        val subs = effectiveSubLangs(item, p)
        if (subs.isNotEmpty()) {
            req.addOption("--write-subs")
            req.addOption("--sub-langs", subs.joinToString(",") { "$it.*" })
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

    // ---------------------------------------------------------------- custom video settings
    /** Custom video settings apply to video downloads only (MP3 / M4A are left alone). */
    private fun customizing(item: DownloadItem, p: Prefs): Boolean =
        p.cvOn && item.quality != "audio" && item.quality != "m4a"

    /** With a custom resolution the video is fetched near that size already (less to convert, less data). */
    private fun effectiveQuality(item: DownloadItem, p: Prefs): String =
        if (customizing(item, p) && p.cvRes > 0) p.cvRes.toString() else item.quality

    /** ffmpeg arguments for the custom settings. [vcodec] may differ from the Settings value when a codec is missing. */
    private fun customArgs(p: Prefs, vcodec: String): String {
        val vf = ArrayList<String>()
        if (p.cvRes > 0) {
            // cvRes is the SHORT side (like "240p"), never scaled up
            vf.add("scale='if(gt(iw,ih),-2,min(${p.cvRes},iw))':'if(gt(iw,ih),min(${p.cvRes},ih),-2)'")
        }
        when (p.cvRotate) {
            90 -> vf.add("transpose=1")
            180 -> { vf.add("hflip"); vf.add("vflip") }
            270 -> vf.add("transpose=2")
        }
        when (p.cvFlip) {
            "h" -> vf.add("hflip")
            "v" -> vf.add("vflip")
        }
        // Compress = two steps coarser, smaller audio
        val q = (if (p.cvCompress) p.cvQuality + 2 else p.cvQuality).coerceIn(1, 5)
        val a = ArrayList<String>()
        if (vf.isNotEmpty()) { a.add("-vf"); a.add("\"" + vf.joinToString(",") + "\"") }
        if (p.cvFps > 0) { a.add("-r"); a.add(p.cvFps.toString()) }
        when (vcodec) {
            "h264" -> a.addAll(listOf("-c:v", "libx264", "-preset", "ultrafast", "-crf", listOf(20, 23, 26, 29, 33)[q - 1].toString(), "-pix_fmt", "yuv420p"))
            "h265" -> a.addAll(listOf("-c:v", "libx265", "-preset", "ultrafast", "-crf", listOf(24, 27, 30, 33, 37)[q - 1].toString(), "-tag:v", "hvc1", "-pix_fmt", "yuv420p"))
            else -> a.addAll(listOf("-c:v", "mpeg4", "-q:v", listOf(2, 4, 6, 10, 16)[q - 1].toString(), "-pix_fmt", "yuv420p"))
        }
        val ab = if (p.cvCompress) "64k" else "128k"
        if (p.cvACodec == "mp3") a.addAll(listOf("-c:a", "libmp3lame", "-b:a", ab)) else a.addAll(listOf("-c:a", "aac", "-b:a", ab))
        return a.joinToString(" ")
    }

    private val missingEncoderRx = Regex("unknown encoder|encoder .* not found|automatic encoder selection failed|no such encoder", RegexOption.IGNORE_CASE)

    /**
     * Re-encodes the finished download with the user's custom video settings (resolution, frame rate, codecs,
     * quality, rotation, flip, compress). yt-dlp does it: it "downloads" the local file (file:// switched on for
     * this one call) and recodes it with ffmpeg. yt-dlp skips "mp4 to mp4", so the working copy is given an
     * .mkv name first (ffmpeg looks at the content, not the name). The original is only replaced when the
     * new file is ready; if anything fails the downloaded video stays as it was and the Log says why.
     */
    private fun applyCustomSettings(ctx: Context, item: DownloadItem, p: Prefs, srcPath: String, onPhase: (String) -> Unit): DownloadOutcome {
        val src = File(srcPath)
        val parent = src.parentFile
        if (!src.exists() || parent == null) return DownloadOutcome(true, srcPath)
        val work = File(parent, ".cvtmp-" + System.currentTimeMillis())
        val inFile = File(work, "in.mkv")
        val outDir = File(work, "out")
        var moved = false
        partials[item.id] = srcPath
        try {
            onPhase(tr("Applying your video settings…", "آپ کی ویڈیو سیٹنگز لگ رہی ہیں…"))
            outDir.mkdirs()
            moved = src.renameTo(inFile)
            if (!moved) {
                src.copyTo(inFile, overwrite = true)
            }
            var fileUrl = inFile.toURI().toASCIIString()
            if (!fileUrl.startsWith("file:///")) fileUrl = fileUrl.replaceFirst("file:/", "file:///")

            var codec = p.cvVCodec
            var result: File? = null
            var lastErr = ""
            for (attempt in 0..1) {
                outDir.listFiles()?.forEach { it.delete() }
                try {
                    val req = YoutubeDLRequest(fileUrl)
                    req.addOption("--enable-file-urls")
                    req.addOption("--no-playlist")
                    req.addOption("--no-mtime")
                    req.addOption("-P", outDir.absolutePath)
                    req.addOption("-o", "vid.%(ext)s")
                    req.addOption("--recode-video", "mp4")
                    req.addOption("--postprocessor-args", "VideoConvertor:" + customArgs(p, codec))
                    YoutubeDL.getInstance().execute(req, item.id) { _, _, _ -> }
                    result = outDir.listFiles { f -> f.isFile && f.name.endsWith(".mp4", true) }?.maxByOrNull { it.length() }
                    break
                } catch (t: Throwable) {
                    val msg = t.message ?: t.toString()
                    lastErr = msg
                    if (cancelled.contains(item.id)) throw t
                    if (attempt == 0 && codec != "mpeg4" && missingEncoderRx.containsMatchIn(msg)) {
                        LogStore.w("download", "This phone's engine has no $codec encoder; using MPEG4 instead", item.url, shortMessage(msg))
                        codec = "mpeg4"
                        continue
                    }
                    throw t
                }
            }
            val out = result
            if (out == null || out.length() <= 0L) {
                throw IllegalStateException("No converted file was produced" + if (lastErr.isNotBlank()) ": $lastErr" else "")
            }
            // Same name as the download, always .mp4
            var target = File(parent, src.nameWithoutExtension + ".mp4")
            var n = 1
            while (target.exists()) { target = File(parent, src.nameWithoutExtension + " (" + n + ").mp4"); n++ }
            if (!out.renameTo(target)) {
                out.copyTo(target, overwrite = false)
                out.delete()
            }
            inFile.delete() // the original is replaced
            LogStore.i("download", "Applied your video settings (${customSummary(p, codec)})", item.url, detail = target.absolutePath)
            return DownloadOutcome(true, target.absolutePath)
        } catch (t: Throwable) {
            val msg = t.message ?: t.toString()
            if (cancelled.contains(item.id)) {
                // paused / cancelled: put the original back so nothing is lost
                restoreOriginal(inFile, src)
                return DownloadOutcome(false, message = shortMessage(msg), raw = msg)
            }
            restoreOriginal(inFile, src)
            LogStore.w("download", "Your video settings could not be applied; the video is saved as downloaded", item.url, detail = failureText(msg, msg, emptyList(), LogStore.stack(t)))
            return DownloadOutcome(true, srcPath, tr("Saved as downloaded: your video settings could not be applied.", "جیسی ڈاؤن لوڈ ہوئی ویسی محفوظ ہے: آپ کی ویڈیو سیٹنگز لاگو نہیں ہو سکیں۔"))
        } finally {
            partials.remove(item.id)
            try { work.deleteRecursively() } catch (_: Throwable) { }
        }
    }

    private fun restoreOriginal(inFile: File, src: File) {
        try {
            if (inFile.exists() && !src.exists()) {
                if (!inFile.renameTo(src)) inFile.copyTo(src, overwrite = false)
            }
        } catch (_: Throwable) { }
    }

    private fun customSummary(p: Prefs, codec: String): String =
        listOf(
            if (p.cvRes > 0) "${p.cvRes}p" else "original size",
            if (p.cvFps > 0) "${p.cvFps} fps" else "original fps",
            codec, p.cvACodec, "quality ${p.cvQuality}",
            if (p.cvRotate != 0) "rotate ${p.cvRotate}" else "",
            if (p.cvFlip != "none") "flip ${p.cvFlip}" else "",
            if (p.cvCompress) "compress" else "",
        ).filter { it.isNotEmpty() }.joinToString(", ")

    /** Folder name for "site-wise folders": the same name for every link of one site. */
    private fun siteFolderName(url: String): String {
        val h = Utils.hostOf(url).lowercase()
        val name = when {
            h == "youtu.be" || h.endsWith("youtube.com") || h.endsWith("youtube-nocookie.com") -> "YouTube"
            else -> Utils.siteName(url)
        }
        val clean = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "").trim()
        return clean.ifEmpty { "Other" }
    }

    /** The file name template: the user's own name for this video, or the Settings template. */
    private fun outputTemplate(item: DownloadItem, p: Prefs): String {
        val custom = item.customName.trim()
        if (custom.isEmpty()) return p.nameTemplate.ifBlank { "%(title).120B.%(ext)s" }
        val clean = Utils.safeFileName(custom).replace("%", "%%") // % means "field" in the yt-dlp template
        return (clean.ifEmpty { "video" }) + ".%(ext)s"
    }

    /** Subtitle language codes to download for this video (empty = none). */
    private fun effectiveSubLangs(item: DownloadItem, p: Prefs): List<String> {
        val raw = when {
            item.subLangs == "none" -> ""
            item.subLangs.isNotBlank() -> item.subLangs
            p.saveSubs -> p.subLangs
            else -> ""
        }
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
    }

    /**
     * Converts a finished video to MP3 next to it. yt-dlp is asked to "download" the local file (file:// is
     * switched on for this one call) and to extract the audio with ffmpeg. The work happens in a temp folder
     * and the MP3 is moved next to the video at the end, so the original is never touched.
     */
    fun toMp3(ctx: Context, srcPath: String, processId: String): DownloadOutcome {
        if (!ensureInit(ctx)) return DownloadOutcome(false, message = "Engine not ready", raw = lastError)
        waitWhileUpdating()
        val src = File(srcPath)
        if (!src.exists()) return DownloadOutcome(false, message = "The video file is missing")
        val parent = src.parentFile ?: return DownloadOutcome(false, message = "Folder not found")
        val tmp = File(parent, ".mp3tmp-" + System.currentTimeMillis())
        return try {
            tmp.mkdirs()
            val base = src.nameWithoutExtension
            var fileUrl = src.toURI().toASCIIString() // percent-encoded, e.g. file:/storage/emulated/0/...
            if (!fileUrl.startsWith("file:///")) fileUrl = fileUrl.replaceFirst("file:/", "file:///")
            val req = YoutubeDLRequest(fileUrl)
            req.addOption("--enable-file-urls")
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
            req.addOption("-P", tmp.absolutePath)
            req.addOption("-o", base.replace("%", "%%") + ".%(ext)s")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "0")
            YoutubeDL.getInstance().execute(req, processId) { _, _, _ -> }
            val mp3 = tmp.listFiles { f -> f.isFile && f.name.endsWith(".mp3", true) }?.maxByOrNull { it.lastModified() }
                ?: return DownloadOutcome(false, message = "No MP3 file was produced")
            var target = File(parent, mp3.name)
            var n = 1
            while (target.exists()) { target = File(parent, mp3.nameWithoutExtension + " (" + n + ").mp3"); n++ }
            if (!mp3.renameTo(target)) {
                mp3.copyTo(target, overwrite = false)
                mp3.delete()
            }
            DownloadOutcome(true, target.absolutePath)
        } catch (t: Throwable) {
            val msg = t.message ?: t.toString()
            DownloadOutcome(false, message = shortMessage(msg), raw = failureText(msg, msg, emptyList(), LogStore.stack(t)))
        } finally {
            try { tmp.deleteRecursively() } catch (_: Throwable) { }
        }
    }

    private fun newestMedia(dir: File, since: Long): String? =
        dir.listFiles { f -> f.isFile && Utils.isMediaFile(f.name) && f.lastModified() >= since - 3000 }
            ?.maxByOrNull { it.lastModified() }?.absolutePath

    fun cancel(id: String) {
        cancelled.add(id)
        try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Throwable) { }
    }

    fun cleanupPartial(ctx: Context, id: String) {
        cleanupTemp(ctx, id)
        val p = partials.remove(id) ?: return
        try {
            for (s in listOf(p, "$p.part", "$p.ytdl", "$p.temp")) {
                val f = File(s)
                if (f.exists()) f.delete()
            }
        } catch (_: Throwable) { }
    }

    /** Deletes the hidden temp folder of one download (its half-finished pieces). */
    fun cleanupTemp(ctx: Context, id: String) {
        try { File(Utils.tempRoot(ctx), id).deleteRecursively() } catch (_: Throwable) { }
    }

    /** Only yt-dlp's own temporary names (never a plain ".part" of another app). */
    private val leftoverRx = Regex(
        """\.part-Frag\d+(\.part)?$|\.(mp4|m4v|mkv|webm|m4a|mp3|opus|ogg|mov|flv|ts|3gp|aac|wav|flac)\.(part|ytdl)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Runs once when the app starts: removes half-finished pieces nobody needs any more, i.e. temp folders of
     * downloads that are gone, and the .part / -Frag files that older versions left inside the Download folder.
     */
    fun sweepLeftovers(ctx: Context, p: Prefs, keepIds: Set<String>) {
        var n = 0
        try {
            Utils.tempRoot(ctx).listFiles()?.forEach { d ->
                if (d.isDirectory && d.name !in keepIds && d.deleteRecursively()) n++
            }
            val hourAgo = System.currentTimeMillis() - 3_600_000L
            Utils.outputDir(ctx, p.folder).walkTopDown().maxDepth(3).forEach { f ->
                if (f.isFile && leftoverRx.containsMatchIn(f.name) && f.lastModified() < hourAgo && f.delete()) n++
            }
        } catch (_: Throwable) { }
        if (n > 0) LogStore.i("storage", "Cleaned $n leftover file(s) of unfinished downloads")
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
