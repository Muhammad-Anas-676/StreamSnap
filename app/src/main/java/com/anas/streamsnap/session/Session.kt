package com.anas.streamsnap.session

import android.content.Context
import android.os.Build
import com.anas.streamsnap.core.cloud.AdminNotice
import com.anas.streamsnap.core.cloud.AppConfig
import com.anas.streamsnap.core.cloud.AuthRepo
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.ScreenTracker
import com.anas.streamsnap.core.legal.LegalDocs
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.service.Notifier
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Why the app is blocked right now (shown instead of the main screens). */
sealed class Gate {
    object Banned : Gate()
    data class Maintenance(val message: String) : Gate()
    object UpdateRequired : Gate()
}

/** The announcement banner at the top of the Queue screen. [color] is a key of BannerPalette. */
data class Banner(val text: String, val color: String, val id: Long)

/**
 * Sign-in state, consent, remote switches, admin messages and the live presence of this user.
 * When Firebase is not configured (no google-services.json) [cloudOn] is false and the app simply runs without an account.
 */
object Session {
    private lateinit var app: Context
    private val sp by lazy { app.getSharedPreferences("session", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val cloudOn: Boolean get() = Cloud.ready

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _consented = MutableStateFlow(false)
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    private val _gate = MutableStateFlow<Gate?>(null)
    val gate: StateFlow<Gate?> = _gate.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _notices = MutableStateFlow<List<AdminNotice>>(emptyList())
    val notices: StateFlow<List<AdminNotice>> = _notices.asStateFlow()

    private val _permissionsDone = MutableStateFlow(false)
    val permissionsDone: StateFlow<Boolean> = _permissionsDone.asStateFlow()

    // live state
    private var banned = false
    private var cfg = AppConfig()
    private var liveUid: String? = null
    private val regs = ArrayList<ListenerRegistration>()
    private val shown = HashSet<String>()
    private var heartbeat: Job? = null

    /** True while the app is in the foreground (set by MainActivity). */
    @Volatile
    var foreground: Boolean = false
        private set

    val versionName: String
        get() = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }

    @Suppress("DEPRECATION")
    val versionCode: Long
        get() = try {
            val p = app.packageManager.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        } catch (_: Throwable) { 0L }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _permissionsDone.value = sp.getBoolean("permissions_done", false)
        ScreenTracker.onScreenChanged = { refreshPresence() }
        if (!Cloud.ready) return
        _user.value = Cloud.auth().currentUser
        _consented.value = hasConsent(_user.value?.uid)
        Cloud.auth().addAuthStateListener { fa ->
            _user.value = fa.currentUser
            _consented.value = hasConsent(fa.currentUser?.uid)
        }
    }

    private fun hasConsent(uid: String?): Boolean = uid != null && sp.getInt("consent_$uid", 0) >= LegalDocs.VERSION

    /** The user accepted the documents: remembered here and recorded in the cloud. */
    fun acceptConsent() {
        val uid = _user.value?.uid ?: return
        sp.edit().putInt("consent_$uid", LegalDocs.VERSION).apply()
        _consented.value = true
        Cloud.saveConsent(uid, LegalDocs.VERSION)
    }

    /** The permission step after sign-in and consent was finished (allowed or skipped). */
    fun completePermissions() {
        sp.edit().putBoolean("permissions_done", true).apply()
        _permissionsDone.value = true
    }

    // ------------------------------------------------------------------------------ sign-in and profile merge

    private val mergeMutex = Mutex()
    @Volatile private var mergedUid: String? = null

    /** True once the cloud profile of [uid] was read and merged into this phone (so counters may be pushed safely). */
    fun profileMerged(uid: String?): Boolean = uid != null && mergedUid == uid

    /**
     * Reads the existing `users/{uid}` document and merges it into the local profile BEFORE anything is pushed,
     * so a fresh install never overwrites the name, phone, country or download counter that the cloud already has.
     * If the cloud cannot be read (offline) nothing is marked as merged and no profile field or counter is pushed.
     */
    private suspend fun mergeFromCloud(uid: String, keepName: Boolean = false, keepPhone: Boolean = false, keepCountry: Boolean = false) {
        mergeMutex.withLock {
            if (mergedUid == uid) return
            val result = withTimeoutOrNull(8_000) { Cloud.fetchUserResult(uid) } ?: return
            val remote = result.getOrElse { return }
            if (remote != null) {
                ProfileStore.mergeFromCloud(remote, keepName, keepPhone, keepCountry)
                // The documents were already accepted on another phone: do not ask again for the same version.
                if (remote.consentVersion > sp.getInt("consent_$uid", 0)) {
                    sp.edit().putInt("consent_$uid", remote.consentVersion).apply()
                    if (_user.value?.uid == uid) _consented.value = hasConsent(uid)
                }
            }
            mergedUid = uid
        }
    }

    /** Sends the profile to the cloud. Profile fields and the counter are only sent after a successful merge (or when typed just now). */
    private fun pushProfile(uid: String, provider: String, exName: String = "", exPhone: String = "", exCountry: String = "") {
        val merged = mergedUid == uid
        val p = ProfileStore.state.value
        Cloud.upsertUser(
            uid = uid,
            name = if (merged) p.name else exName,
            email = p.email,
            provider = provider,
            phone = if (merged) p.phone else exPhone,
            country = if (merged) p.country else exCountry,
            createdAt = p.createdAt,
            versionName = versionName,
            totalDownloads = if (merged) ProfileStore.totalDownloads() else -1,
        )
    }

    /** Called right after a successful sign-in, sign-up, guest start or account upgrade. */
    fun onSignedIn(u: FirebaseUser, provider: String, name: String = "", phone: String = "", country: String = "") {
        val guest = u.isAnonymous
        val exName = if (guest) "" else name.trim()
        val finalName = if (guest) "Guest User" else name.ifBlank { u.displayName ?: "" }
        val email = u.email ?: ""
        val created = u.metadata?.creationTimestamp ?: 0L
        // The phone shows the profile at once; the cloud copy is merged in right after.
        ProfileStore.onSignedIn(u.uid, finalName, email, phone, country, u.photoUrl?.toString() ?: "", created, guest)
        _user.value = Cloud.auth().currentUser
        val prov = if (guest) "guest" else provider
        scope.launch {
            mergeFromCloud(u.uid, keepName = exName.isNotBlank(), keepPhone = phone.isNotBlank(), keepCountry = country.isNotBlank())
            pushProfile(u.uid, prov, exName, phone.trim(), country.trim())
            CloudSync.flush()
        }
    }

    /** Refreshes "last active", app version and device info once per app start (after the cloud profile was merged). */
    fun touch() {
        val u = _user.value ?: return
        scope.launch {
            mergeFromCloud(u.uid)
            pushProfile(u.uid, AuthRepo.provider)
            CloudSync.flush()
        }
    }

    // ------------------------------------------------------------------------------ live listeners

    /** Starts the live listeners (banner, maintenance, ban, admin messages) and the heartbeat. Safe to call again. */
    fun startLive() {
        val uid = _user.value?.uid ?: return
        if (!Cloud.ready || liveUid == uid) return
        stopLive()
        liveUid = uid
        CrashReporter.flush(uid)
        Cloud.listenConfig { applyConfig(it) }?.let { regs.add(it) }
        Cloud.listenUserDoc(uid) { me -> banned = me?.banned == true; recomputeGate() }?.let { regs.add(it) }
        Cloud.listenNotifications(uid) { onNotices(uid, it) }?.let { regs.add(it) }
        heartbeat = scope.launch {
            while (true) {
                delay(120_000)
                if (foreground) pushPresence(true)
            }
        }
        pushPresence(foreground)
    }

    private fun stopLive() {
        regs.forEach { try { it.remove() } catch (_: Throwable) { } }
        regs.clear()
        heartbeat?.cancel()
        heartbeat = null
        liveUid = null
    }

    private fun applyConfig(c: AppConfig) {
        cfg = c
        val dismissed = sp.getLong("dismissed_banner", 0)
        _banner.value = if (c.announcement.isNotBlank() && !c.maintenance && c.announcementId != dismissed) {
            Banner(c.announcement, c.announcementColor, c.announcementId)
        } else null
        recomputeGate()
    }

    private fun recomputeGate() {
        _gate.value = when {
            banned -> Gate.Banned
            cfg.maintenance -> Gate.Maintenance(cfg.announcement)
            cfg.minVersionCode > versionCode -> Gate.UpdateRequired
            else -> null
        }
    }

    /** One-time refresh used by the "Try again" button on the blocked screen. */
    suspend fun refreshGate() {
        val u = _user.value ?: return
        Cloud.fetchUser(u.uid)?.let { banned = it.banned }
        Cloud.fetchConfig()?.let { applyConfig(it) }
        recomputeGate()
    }

    fun dismissBanner() {
        val b = _banner.value ?: return
        sp.edit().putLong("dismissed_banner", b.id).apply()
        _banner.value = null
    }

    private fun onNotices(uid: String, list: List<AdminNotice>) {
        val fresh = list.filter { shown.add(it.id) }
        if (fresh.isEmpty()) return
        _notices.value = _notices.value + fresh
        fresh.forEach {
            Notifier.adminMessage(app, it.id, it.title, it.message)
            Cloud.markNoticeDelivered(uid, it.id)
        }
    }

    fun dismissNotice(id: String) {
        _notices.value = _notices.value.filter { it.id != id }
    }

    // ------------------------------------------------------------------------------ presence

    /** MainActivity calls this from onStart (true) and onStop / onDestroy (false). */
    fun setForeground(fg: Boolean) {
        foreground = fg
        if (liveUid != null) pushPresence(fg)
    }

    /** Re-sends the presence with the current screen (called when the screen or the download state changes). */
    fun refreshPresence() {
        if (liveUid != null && foreground) pushPresence(true)
    }

    private fun label(): String {
        val downloading = QueueManager.items.value.any { it.status == Status.DOWNLOADING }
        return when (ScreenTracker.currentScreen) {
            "Queue" -> if (downloading) "Downloading Media" else "Viewing Queue"
            "History" -> "Viewing History"
            "Browser" -> "Browsing Web"
            "Profile" -> "Viewing Profile"
            "Settings" -> "Changing Settings"
            "Legal" -> "Reading Legal Documents"
            else -> if (downloading) "Downloading Media" else "Using the App"
        }
    }

    private fun pushPresence(online: Boolean) {
        val uid = liveUid ?: return
        Cloud.updatePresence(uid, online, label())
    }

    suspend fun logout() {
        pushPresence(false)
        stopLive()
        shown.clear()
        _notices.value = emptyList()
        _banner.value = null
        _gate.value = null
        banned = false
        ScreenTracker.pause()
        ScreenTracker.reset()
        mergedUid = null
        Cloud.resetUserGate()
        AuthRepo.signOut(app)
        ProfileStore.clear()
    }
}
