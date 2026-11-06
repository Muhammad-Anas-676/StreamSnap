package com.anas.streamsnap.engine

import android.content.Context
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.Settings
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
            return true
        }
    }

    fun versionString(): String = try {
        val r = YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") })
        r.out.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "unknown"
    } catch (t: Throwable) {
        "unknown"
    }

    // ---------------------------------------------------------------- fetch info
    private fun urlHasVideoAndList(url: String): Boolean =
        (url.contains("watch?v=") || url.contains("&v=") || url.contains("youtu.be/")) && url.contains("list=")

    fun fetchInfo(ctx: Context, url: String): FetchResult {
        if (!ensureInit(ctx)) return FetchResult.Error("Engine not ready", lastError)
        return try {
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--flat-playlist")
            req.addOption("--no-warnings")
            req.addOption("--playlist-end", "500")
            if (urlHasVideoAndList(url)) req.addOption("--no-playlist")
            applyCommon(ctx, req, Settings.value)

            val out = YoutubeDL.getInstance().execute(req).out
            val start = out.indexOf('{')
            if (start < 0) return FetchResult.Error("No information returned for this link", out)
            val o = JSONObject(out.substring(start))

            if (o.optString("_type") == "playlist" && o.optJSONArray("entries") != null) {
                val arr = o.getJSONArray("entries")
                val list = ArrayList<VideoMeta>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val u = entryUrl(e) ?: continue
                    list.add(parseVideo(e, u))
                }
                FetchResult.Playlist(o.optString("title", "Playlist"), list)
            } else {
                FetchResult.Single(parseVideo(o, o.optString("webpage_url", url).ifBlank { url }))
            }
        } catch (t: Throwable) {
            val raw = t.message ?: t.toString()
            FetchResult.Error(shortMessage(raw), raw + "\n\n" + LogStore.stack(t))
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
        val dir = Utils.outputDir(ctx, p.folder)
        Utils.applyNoMedia(dir, p.hiddenFolder)
        val startedAt = System.currentTimeMillis()
        var finalPath: String? = null

        return try {
            val req = YoutubeDLRequest(item.url)
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
            applyCommon(ctx, req, p)

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
            if (path == null) DownloadOutcome(true, "", "Finished, but the file was not found in ${dir.absolutePath}")
            else DownloadOutcome(true, path)
        } catch (t: Throwable) {
            val raw = t.message ?: t.toString()
            DownloadOutcome(false, "", shortMessage(raw), raw + "\n\n" + LogStore.stack(t))
        }
    }

    private fun newestMedia(dir: File, since: Long): String? =
        dir.listFiles { f -> f.isFile && Utils.isMediaFile(f.name) && f.lastModified() >= since - 3000 }
            ?.maxByOrNull { it.lastModified() }?.absolutePath

    fun cancel(id: String) {
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
    private fun applyCommon(ctx: Context, req: YoutubeDLRequest, p: Prefs) {
        req.addOption("--socket-timeout", "30")
        req.addOption("--retries", "10")
        if (p.proxy.isNotBlank()) req.addOption("--proxy", p.proxy.trim())
        if (p.useCookies) {
            val c = cookieFile(ctx)
            if (c.exists() && c.length() > 0) req.addOption("--cookies", c.absolutePath)
        }
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
        val m = inst.javaClass.methods.firstOrNull { it.name == "updateYoutubeDL" }
            ?: return "This engine version has no update function"
        val args = ArrayList<Any?>()
        val types = m.parameterTypes
        args.add(ctx.applicationContext)
        if (types.size >= 2) {
            val wanted = if (channel == "nightly") "NIGHTLY" else "STABLE"
            args.add(findChannel(types[1], wanted))
        }
        val res = m.invoke(inst, *args.toTypedArray())
        val v = versionString()
        LogStore.engineVersion = v
        return "${res ?: "done"} (yt-dlp $v)"
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
