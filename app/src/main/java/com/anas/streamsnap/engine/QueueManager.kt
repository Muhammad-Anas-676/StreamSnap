package com.anas.streamsnap.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.PLAYLIST_CONFIRM_THRESHOLD
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.service.DownloadService
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.service.QueueSnapshot
import com.anas.streamsnap.service.ScheduleReceiver
import com.anas.streamsnap.util.LogStore
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AddResult(val added: Int, val duplicates: Int, val noLinks: Boolean)

/** A playlist/channel with more than [PLAYLIST_CONFIRM_THRESHOLD] videos waiting for the user's OK. */
data class PendingPlaylist(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val entries: List<VideoMeta>,
)

/**
 * The download queue. Owns the list, runs fetch + download jobs, reacts to network changes.
 *
 * Modes (Settings):
 *  - autoStart = true  -> Automatic: paste a link and it starts, no button.
 *  - autoStart = false -> Manual: item waits as READY until the download icon is tapped.
 *  - qualityMode = "fixed" -> every video uses Prefs.defaultQuality.
 *  - qualityMode = "ask"   -> every card shows quality chips; in Automatic mode tapping a chip starts it.
 */
object QueueManager {
    private lateinit var app: Context
    private lateinit var file: File
    private var inited = false

    val items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val pendingPlaylists = MutableStateFlow<List<PendingPlaylist>>(emptyList())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchSem = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val actions = ConcurrentHashMap<String, String>() // id -> pause | cancel | wait:<reason>
    private val pumpLock = Any()
    private val itemsLock = Any()
    private var saveJob: Job? = null

    private var batchDone = 0
    private var batchFailed = 0
    private var lastAllowed = true
    private var lowStorageWarned = false
    private var lastNotify = 0L

    @Volatile var serviceRunning = false

    // ------------------------------------------------------------------ init
    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        file = File(app.filesDir, "items.json")
        load()
        housekeeping()
        registerNetwork()
        ScheduleReceiver.arm(app)

        // Items that were fetching when the app died need their info again.
        items.value.filter { it.status == Status.FETCHING }.forEach { startFetch(it.id) }
        pump()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val list = ArrayList<DownloadItem>()
            for (i in 0 until arr.length()) {
                var it = DownloadItem.fromJson(arr.getJSONObject(i))
                it = when (it.status) {
                    Status.DOWNLOADING, Status.WAITING -> it.copy(status = Status.QUEUED, speedText = "", etaSec = -1, waitReason = "")
                    else -> it.copy(speedText = "", etaSec = -1)
                }
                list.add(it)
            }
            items.value = list
        } catch (t: Throwable) {
            LogStore.e("queue", "Could not read the saved queue (starting empty)", t = t)
        }
    }

    /** Auto-clear history and auto-delete old files, as chosen in Settings. */
    private fun housekeeping() {
        val p = Settings.value
        var list = items.value
        if (p.autoDeleteDays > 0) {
            val limit = System.currentTimeMillis() - p.autoDeleteDays * 86_400_000L
            val old = list.filter { it.status == Status.DONE && it.finishedAt in 1 until limit }
            old.forEach { deleteFileQuiet(it.filePath) }
            if (old.isNotEmpty()) LogStore.i("queue", "Auto-deleted ${old.size} old download(s) (older than ${p.autoDeleteDays} days)")
            list = list.filter { x -> old.none { it.id == x.id } }
        }
        if (p.autoClearHistory) list = list.filter { it.status != Status.DONE }
        if (list.size != items.value.size) {
            items.value = list
            saveNow()
        }
    }

    fun onAppClosed() {
        if (Settings.value.autoClearHistory) {
            items.update { l -> l.filter { it.status != Status.DONE } }
        }
        saveNow()
    }

    // ------------------------------------------------------------------ persistence
    private fun persistSoon() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(1500); saveNow() }
    }

    fun saveNow() {
        try {
            val arr = JSONArray()
            items.value.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Throwable) { }
    }

    // ------------------------------------------------------------------ helpers
    fun get(id: String): DownloadItem? = items.value.firstOrNull { it.id == id }

    private fun newId() = UUID.randomUUID().toString().take(12)

    private fun change(id: String, block: (DownloadItem) -> DownloadItem) {
        items.update { l -> l.map { if (it.id == id) block(it) else it } }
        persistSoon()
    }

    private fun changeAll(block: (DownloadItem) -> DownloadItem) {
        items.update { l -> l.map(block) }
        persistSoon()
    }

    private fun emit(msg: String) { _events.tryEmit(msg) }

    private fun statusAfterInfo(): Status {
        val p = Settings.value
        return when {
            p.qualityMode == "ask" || !p.autoStart -> Status.READY
            p.scheduleEnabled -> Status.SCHEDULED
            else -> Status.QUEUED
        }
    }

    // ------------------------------------------------------------------ adding links
    fun addLinks(text: String): AddResult {
        val urls = Utils.extractUrls(text)
        if (urls.isEmpty()) return AddResult(0, 0, true)
        val p = Settings.value
        var dup = 0
        val fresh = ArrayList<DownloadItem>()
        synchronized(itemsLock) {
            val known = items.value.map { Utils.normalizeUrl(it.url) }.toHashSet()
            for (u in urls) {
                if (!known.add(Utils.normalizeUrl(u))) { dup++; continue }
                fresh.add(DownloadItem(id = newId(), url = u, site = Utils.siteName(u), quality = p.defaultQuality))
            }
            if (fresh.isNotEmpty()) items.value = items.value + fresh
        }
        if (fresh.isEmpty()) return AddResult(0, dup, false)
        LogStore.i("queue", "Added ${fresh.size} link(s)" + if (dup > 0) " ($dup duplicate skipped)" else "",
            url = fresh.first().url, detail = fresh.joinToString("\n") { it.url })
        persistSoon()
        startServiceIfNeeded()
        fresh.forEach { startFetch(it.id) }
        return AddResult(fresh.size, dup, false)
    }

    private fun startFetch(id: String) {
        jobs[id] = scope.launch { fetchSem.withPermit { doFetch(id) } }
    }

    private fun doFetch(id: String) {
        val item = get(id) ?: return
        val res = Engine.fetchInfo(app, item.url)
        if (get(id) == null) return // removed while fetching
        when (res) {
            is FetchResult.Single -> {
                val m = res.meta
                change(id) {
                    it.copy(
                        title = m.title.ifBlank { it.title }, thumbnail = m.thumbnail, durationSec = m.durationSec,
                        site = m.site.ifBlank { it.site }, uploader = m.uploader,
                        heights = m.sizes.keys.sorted(), sizes = m.sizes, audioSize = m.audioSize,
                        error = "",
                    )
                }
                LogStore.i("fetch", "Info loaded: ${m.title.ifBlank { item.url }}", item.url)
                makeReady(id)
            }
            is FetchResult.Playlist -> handlePlaylist(id, item, res)
            is FetchResult.Error -> {
                change(id) { it.copy(status = Status.FAILED, error = res.message) }
                LogStore.e("fetch", "Could not read this link: ${res.message}", item.url, raw = res.raw)
                get(id)?.let { Notifier.failed(app, it) }
                afterStateChange()
            }
        }
        jobs.remove(id)
    }

    private fun makeReady(id: String) {
        val st = statusAfterInfo()
        change(id) { it.copy(status = st, sizeEstimate = it.sizeFor(it.quality), progress = 0f) }
        afterStateChange()
    }

    private fun metaToItem(m: VideoMeta, quality: String) = DownloadItem(
        id = newId(), url = m.url, title = m.title, thumbnail = m.thumbnail, durationSec = m.durationSec,
        site = m.site.ifBlank { Utils.siteName(m.url) }, uploader = m.uploader,
        heights = m.sizes.keys.sorted(), sizes = m.sizes, audioSize = m.audioSize, quality = quality,
    )

    private fun handlePlaylist(id: String, src: DownloadItem, r: FetchResult.Playlist) {
        val n = r.entries.size
        if (n == 0) {
            change(id) { it.copy(status = Status.FAILED, error = "No videos found in this playlist/channel") }
            LogStore.e("fetch", "No videos found in this playlist/channel", src.url, raw = r.title)
            afterStateChange()
            return
        }
        LogStore.i("fetch", "Playlist \"${r.title}\" has $n video(s)", src.url)
        if (n > PLAYLIST_CONFIRM_THRESHOLD) {
            items.update { l -> l.filter { it.id != id } }
            pendingPlaylists.update { it + PendingPlaylist(newId(), r.title, src.url, r.entries) }
            persistSoon()
            afterStateChange()
        } else {
            replaceWithEntries(id, r.entries)
        }
    }

    private fun replaceWithEntries(placeholderId: String?, entries: List<VideoMeta>) {
        val p = Settings.value
        val st = statusAfterInfo()
        val created = ArrayList<DownloadItem>()
        synchronized(itemsLock) {
            val known = items.value.map { Utils.normalizeUrl(it.url) }.toHashSet()
            for (m in entries) {
                if (!known.add(Utils.normalizeUrl(m.url))) continue
                val it = metaToItem(m, p.defaultQuality)
                created.add(it.copy(status = st, sizeEstimate = it.sizeFor(it.quality)))
            }
            val cur = items.value
            val idx = if (placeholderId == null) -1 else cur.indexOfFirst { it.id == placeholderId }
            items.value = when {
                idx >= 0 -> cur.subList(0, idx) + created + cur.subList(idx + 1, cur.size)
                else -> cur + created
            }
        }
        persistSoon()
        emit(tr("Added ${created.size} video(s) from playlist", "پلے لسٹ سے ${created.size} ویڈیوز شامل ہوئیں"))
        afterStateChange()
    }

    /** [count] = how many to add (0 = cancel). */
    fun confirmPlaylist(pp: PendingPlaylist, count: Int) {
        pendingPlaylists.update { l -> l.filter { it.id != pp.id } }
        if (count <= 0) {
            LogStore.i("queue", "Playlist \"${pp.title}\" not added", pp.sourceUrl)
            return
        }
        LogStore.i("queue", "Adding ${minOf(count, pp.entries.size)} of ${pp.entries.size} video(s) from \"${pp.title}\"", pp.sourceUrl)
        startServiceIfNeeded()
        replaceWithEntries(null, pp.entries.take(count))
    }

    // ------------------------------------------------------------------ user actions
    /** Download icon (Manual mode) for a READY item. */
    fun startItem(id: String) {
        val it = get(id) ?: return
        if (it.status != Status.READY && it.status != Status.PAUSED && it.status != Status.FAILED) return
        val st = if (Settings.value.scheduleEnabled && it.status == Status.READY) Status.SCHEDULED else Status.QUEUED
        change(id) { x -> x.copy(status = st, error = "", waitReason = "") }
        startServiceIfNeeded()
        afterStateChange()
    }

    fun startAllReady() {
        val ready = items.value.filter { it.status == Status.READY }
        if (ready.isEmpty()) return
        val st = if (Settings.value.scheduleEnabled) Status.SCHEDULED else Status.QUEUED
        changeAll { if (it.status == Status.READY) it.copy(status = st) else it }
        startServiceIfNeeded()
        afterStateChange()
    }

    /** Quality chip tapped. Automatic mode + per-video quality: the tap also starts the download. */
    fun chooseQuality(id: String, q: String) {
        val it = get(id) ?: return
        if (it.status == Status.DOWNLOADING || it.status == Status.DONE || it.status == Status.FETCHING) return
        val p = Settings.value
        change(id) { x -> x.copy(quality = q, sizeEstimate = x.sizeFor(q)) }
        if (it.status == Status.READY && p.autoStart) startItem(id)
    }

    /** Settings: the "all videos this quality" choice changed -> apply it to videos that have not started yet. */
    fun applyDefaultQuality(q: String) {
        changeAll { x ->
            if (x.status == Status.READY || x.status == Status.QUEUED || x.status == Status.SCHEDULED || x.status == Status.FETCHING)
                x.copy(quality = q, sizeEstimate = x.sizeFor(q))
            else x
        }
    }

    /** Settings: Automatic/Manual or quality mode changed. Items that were only waiting for a tap now start by themselves. */
    fun onModeChanged() {
        val p = Settings.value
        if (p.autoStart && p.qualityMode == "fixed") startAllReady()
        pump()
    }

    fun pause(id: String) {
        val it = get(id) ?: return
        when (it.status) {
            Status.DOWNLOADING -> { actions[id] = "pause"; Engine.cancel(id) }
            Status.QUEUED, Status.WAITING, Status.SCHEDULED -> change(id) { x -> x.copy(status = Status.PAUSED, waitReason = "") }
            else -> {}
        }
        afterStateChange()
    }

    fun resume(id: String) {
        val it = get(id) ?: return
        if (it.status == Status.PAUSED || it.status == Status.WAITING || it.status == Status.SCHEDULED) {
            change(id) { x -> x.copy(status = Status.QUEUED, waitReason = "", error = "") }
            startServiceIfNeeded()
            afterStateChange()
        }
    }

    fun retry(id: String) {
        val it = get(id) ?: return
        if (it.status != Status.FAILED) return
        if (it.title.isBlank() && it.sizes.isEmpty()) {
            change(id) { x -> x.copy(status = Status.FETCHING, error = "") }
            startServiceIfNeeded()
            startFetch(id)
        } else {
            change(id) { x -> x.copy(status = Status.QUEUED, error = "", progress = 0f) }
            startServiceIfNeeded()
            afterStateChange()
        }
        LogStore.i("queue", "Retry: ${it.displayTitle}", it.url)
    }

    /** Download again from History. */
    fun redownload(id: String) {
        val it = get(id) ?: return
        change(id) { x -> x.copy(status = Status.QUEUED, progress = 0f, filePath = "", fileSize = 0, error = "", finishedAt = 0) }
        startServiceIfNeeded()
        afterStateChange()
    }

    /** Remove from the queue (stops the download if running, removes partial files). */
    fun cancel(id: String) {
        val it = get(id) ?: return
        if (it.status == Status.DOWNLOADING) {
            actions[id] = "cancel"
            Engine.cancel(id)
        } else {
            jobs.remove(id)?.cancel()
            Engine.cleanupPartial(id)
        }
        items.update { l -> l.filter { x -> x.id != id } }
        Notifier.clearItem(app, id)
        LogStore.i("queue", "Removed: ${it.displayTitle}", it.url)
        persistSoon()
        afterStateChange()
    }

    fun removeFromHistory(id: String) {
        items.update { l -> l.filter { it.id != id } }
        persistSoon()
    }

    fun deleteWithFile(id: String) {
        val it = get(id) ?: return
        deleteFileQuiet(it.filePath)
        LogStore.i("queue", "Deleted file: ${it.displayTitle}", it.url, detail = it.filePath)
        removeFromHistory(id)
    }

    fun clearHistory(deleteFiles: Boolean) {
        val done = items.value.filter { it.status == Status.DONE }
        if (deleteFiles) done.forEach { deleteFileQuiet(it.filePath) }
        items.update { l -> l.filter { it.status != Status.DONE } }
        LogStore.i("queue", "History cleared (${done.size} item(s)${if (deleteFiles) ", files deleted" else ""})")
        persistSoon()
    }

    fun pauseAll() {
        items.value.forEach { pause(it.id) }
    }

    fun resumeAll() {
        items.value.forEach {
            when (it.status) {
                Status.PAUSED, Status.WAITING, Status.SCHEDULED -> resume(it.id)
                Status.READY -> if (Settings.value.autoStart) startItem(it.id)
                else -> {}
            }
        }
    }

    fun cancelAll() {
        items.value.filter { it.status != Status.DONE }.forEach { cancel(it.id) }
    }

    fun swap(a: String, b: String) {
        items.update { l ->
            val i = l.indexOfFirst { it.id == a }
            val j = l.indexOfFirst { it.id == b }
            if (i < 0 || j < 0 || i == j) l
            else l.toMutableList().also { m -> val t = m[i]; m[i] = m[j]; m[j] = t }
        }
        persistSoon()
    }

    /** Scheduled time reached (or "Start now" tapped). */
    fun releaseScheduled() {
        if (items.value.none { it.status == Status.SCHEDULED }) return
        LogStore.i("queue", "Scheduled downloads released")
        changeAll { if (it.status == Status.SCHEDULED) it.copy(status = Status.QUEUED) else it }
        startServiceIfNeeded()
        afterStateChange()
    }

    private fun deleteFileQuiet(path: String) {
        if (path.isBlank()) return
        try {
            val f = File(path)
            if (f.exists()) f.delete()
            File("$path.part").takeIf { it.exists() }?.delete()
        } catch (_: Throwable) { }
    }

    // ------------------------------------------------------------------ scheduler
    private fun afterStateChange() {
        pump()
        refreshNotifications(force = true)
        idleCheck()
    }

    fun pump() {
        val toStart = ArrayList<String>()
        synchronized(pumpLock) {
            val p = Settings.value
            val list = items.value
            if (!networkAllowed()) {
                if (list.any { it.status == Status.QUEUED }) {
                    val reason = networkReason()
                    changeAll { if (it.status == Status.QUEUED) it.copy(status = Status.WAITING, waitReason = reason) else it }
                }
                return
            }
            var running = list.count { it.status == Status.DOWNLOADING }
            for (x in list) {
                if (running >= p.parallel) break
                if (x.status == Status.QUEUED) { toStart.add(x.id); running++ }
            }
            if (toStart.isNotEmpty()) {
                val set = toStart.toHashSet()
                changeAll {
                    if (it.id in set) it.copy(status = Status.DOWNLOADING, error = "", speedText = "", etaSec = -1, waitReason = "")
                    else it
                }
            }
        }
        toStart.forEach { launchDownload(it) }
    }

    private fun launchDownload(id: String) {
        startServiceIfNeeded()
        jobs[id] = scope.launch { runDownload(id) }
    }

    private fun runDownload(id: String) {
        val item = get(id) ?: return
        val p = Settings.value

        val dir = Utils.outputDir(app, p.folder)
        val free = Utils.freeBytes(dir)
        if (free < 30L * 1024 * 1024) {
            change(id) { it.copy(status = Status.WAITING, waitReason = tr("Low storage", "اسٹوریج کم ہے")) }
            Notifier.lowStorage(app, free)
            LogStore.w("download", "Not started: only ${Utils.fmtBytes(free)} free", item.url)
            jobs.remove(id)
            refreshNotifications(true)
            return
        }
        if (free < 300L * 1024 * 1024 && !lowStorageWarned) {
            lowStorageWarned = true
            Notifier.lowStorage(app, free)
        }

        LogStore.i("download", "Started: ${item.displayTitle} [${qualityLabel(item.quality)}]", item.url)
        var lastUi = 0L
        var lastNote = 0L
        val outcome = Engine.download(app, item, p) { percent, eta, line ->
            val now = SystemClock.elapsedRealtime()
            val speed = Engine.parseSpeed(line)
            if ((percent >= 0f || speed != null) && now - lastUi >= 400) {
                lastUi = now
                change(id) {
                    it.copy(
                        progress = if (percent >= 0f) (percent / 100f).coerceIn(0f, 1f) else it.progress,
                        etaSec = if (eta >= 0) eta else it.etaSec,
                        speedText = speed ?: it.speedText,
                    )
                }
            }
            if (now - lastNote >= 1000) {
                lastNote = now
                get(id)?.let { Notifier.progress(app, it) }
                refreshNotifications(false)
            }
        }

        val action = actions.remove(id)
        jobs.remove(id)
        Notifier.clearProgress(app, id)

        when {
            outcome.ok -> onSuccess(id, outcome)
            action == "pause" -> {
                change(id) { it.copy(status = Status.PAUSED, speedText = "", etaSec = -1) }
                LogStore.i("download", "Paused: ${item.displayTitle}", item.url)
            }
            action == "cancel" -> Engine.cleanupPartial(id)
            action != null && action.startsWith("wait:") -> {
                change(id) { it.copy(status = Status.WAITING, waitReason = action.removePrefix("wait:"), speedText = "", etaSec = -1) }
                LogStore.i("download", "Waiting (${action.removePrefix("wait:")}): ${item.displayTitle}", item.url)
            }
            else -> onFailure(id, outcome)
        }
        afterStateChange()
    }

    private fun onSuccess(id: String, o: DownloadOutcome) {
        val f = if (o.filePath.isNotBlank()) File(o.filePath) else null
        val size = if (f != null && f.exists()) f.length() else 0L
        change(id) {
            it.copy(
                status = Status.DONE, progress = 1f, filePath = o.filePath, fileSize = size,
                finishedAt = System.currentTimeMillis(), speedText = "", etaSec = -1, error = o.message,
            )
        }
        batchDone++
        val item = get(id) ?: return
        if (o.message.isNotBlank()) LogStore.w("download", o.message, item.url)
        LogStore.i("download", "Finished: ${item.displayTitle}", item.url, detail = o.filePath)
        Notifier.done(app, item)
    }

    private fun onFailure(id: String, o: DownloadOutcome) {
        val item = get(id) ?: return
        if (!Utils.isOnline(app)) {
            change(id) { it.copy(status = Status.WAITING, waitReason = tr("No internet", "انٹرنیٹ نہیں"), speedText = "", etaSec = -1) }
            LogStore.w("download", "Interrupted, no internet: ${item.displayTitle}", item.url, detail = o.raw)
            return
        }
        change(id) { it.copy(status = Status.FAILED, error = o.message, speedText = "", etaSec = -1) }
        batchFailed++
        LogStore.e("download", "Download failed: ${item.displayTitle}", item.url, raw = o.raw.ifBlank { o.message })
        get(id)?.let { Notifier.failed(app, it) }
    }

    // ------------------------------------------------------------------ network rules
    private fun networkAllowed(): Boolean {
        val p = Settings.value
        return Utils.isOnline(app) && (!p.wifiOnly || Utils.isWifi(app))
    }

    private fun networkReason(): String =
        if (!Utils.isOnline(app)) tr("No internet", "انٹرنیٹ نہیں") else tr("Waiting for Wi-Fi", "وائی فائی کا انتظار")

    private fun registerNetwork() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { recheck() }
                override fun onLost(network: Network) { recheck() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { recheck() }
            })
        } catch (t: Throwable) {
            LogStore.w("network", "Could not watch network changes", detail = t.toString())
        }
    }

    private var lossJob: Job? = null

    /** Called on every network change and after Settings (Wi-Fi only, parallel...) change. */
    fun recheck() {
        val allowed = networkAllowed()
        if (allowed) {
            lossJob?.cancel()
            lossJob = null
            val resumed = items.value.any { it.status == Status.WAITING && it.waitReason != tr("Low storage", "اسٹوریج کم ہے") }
            if (resumed) {
                changeAll {
                    if (it.status == Status.WAITING && it.waitReason != tr("Low storage", "اسٹوریج کم ہے"))
                        it.copy(status = Status.QUEUED, waitReason = "") else it
                }
                LogStore.i("network", "Connection OK again, resuming downloads")
            }
            if (!lastAllowed) Notifier.clearNetwork(app)
            lastAllowed = true
            afterStateChange()
        } else {
            // Connecting / switching a VPN makes the network disappear for a second or two.
            // Wait a little and only react if the connection is really gone.
            if (lossJob?.isActive == true) return
            lossJob = scope.launch {
                delay(4000)
                lossJob = null
                if (networkAllowed()) recheck() else handleNetworkLost()
            }
        }
    }

    private fun handleNetworkLost() {
        run {
            val reason = networkReason()
            val active = items.value.filter { it.status == Status.DOWNLOADING }
            active.forEach { actions[it.id] = "wait:$reason"; Engine.cancel(it.id) }
            val had = active.isNotEmpty() || items.value.any { it.status == Status.QUEUED }
            if (lastAllowed && had) {
                LogStore.w("network", reason)
                Notifier.networkLost(app, reason)
            }
            lastAllowed = false
            pump() // moves QUEUED -> WAITING
            refreshNotifications(true)
        }
    }

    // ------------------------------------------------------------------ service + notifications
    fun snapshot(): QueueSnapshot {
        val l = items.value
        val dl = l.filter { it.status == Status.DOWNLOADING }
        val queued = l.count { it.status == Status.QUEUED || it.status == Status.FETCHING }
        val waiting = l.filter { it.status == Status.WAITING }
        val total = (batchDone + batchFailed + dl.size + queued + waiting.size).coerceAtLeast(1)
        val overall = (batchDone + dl.sumOf { it.progress.toDouble() }.toFloat()) / total
        return QueueSnapshot(
            downloading = dl.size, queued = queued, waiting = waiting.size,
            done = batchDone, failed = batchFailed, total = total, overall = overall,
            speed = dl.firstOrNull { it.speedText.isNotBlank() }?.speedText ?: "",
            waitReason = waiting.firstOrNull()?.waitReason ?: "",
        )
    }

    private fun startServiceIfNeeded() {
        if (!serviceRunning) DownloadService.start(app)
    }

    private fun refreshNotifications(force: Boolean) {
        if (!serviceRunning) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotify < 1000) return
        lastNotify = now
        Notifier.postQueue(app, snapshot())
    }

    private fun idleCheck() {
        val l = items.value
        val busy = l.any { it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.FETCHING }
        val waiting = l.any { it.status == Status.WAITING }
        if (!busy && !waiting) {
            if (serviceRunning) DownloadService.stop(app)
            if (batchDone + batchFailed > 0) {
                Notifier.summary(app, batchDone, batchFailed)
                batchDone = 0
                batchFailed = 0
            }
            lowStorageWarned = false
        }
    }
}
