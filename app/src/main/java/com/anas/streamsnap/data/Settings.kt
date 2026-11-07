package com.anas.streamsnap.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

data class Prefs(
    // Appearance
    val themeMode: Int = 0,              // 0 system, 1 light, 2 dark
    val amoled: Boolean = false,
    val accent: Int = 0,                 // index in Accents, 99 = dynamic (Material You)
    val language: String = "en",         // en | ur
    val autoKeyboard: Boolean = false,   // false = keyboard opens only when a text field is tapped
    // Downloads
    val autoStart: Boolean = true,       // true = Automatic download, false = Manual (tap the download icon)
    val parallel: Int = 2,
    val wifiOnly: Boolean = false,
    val speedLimitKb: Int = 0,           // 0 = unlimited
    val proxy: String = "",
    val folder: String = "StreamSnap/Download",
    val nameTemplate: String = "%(title).120B.%(ext)s",
    val defaultQuality: String = "best", // best | 1080 | 720 | 480 | 360 | audio
    val qualityMode: String = "fixed",   // fixed = one quality for all videos, ask = pick per video
    val saveThumb: Boolean = false,
    val saveSubs: Boolean = false,
    val hiddenFolder: Boolean = false,
    val useCookies: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val scheduleHour: Int = 2,
    val scheduleMinute: Int = 0,
    // Privacy
    val blockScreenshots: Boolean = false,
    val appLock: Boolean = false,
    val lockBiometric: Boolean = true,
    val pinHash: String = "",
    val pinSalt: String = "",
    val autoClearHistory: Boolean = false,
    val autoDeleteDays: Int = 0,         // 0 = never
    // Notifications
    val notifSound: Boolean = true,
    val notifVibrate: Boolean = true,
    val nProgress: Boolean = true,
    val nDone: Boolean = true,
    val nFailed: Boolean = true,
    val nSummary: Boolean = true,
    val nNetwork: Boolean = true,
    val nStorage: Boolean = true,
    val nEngine: Boolean = true,
    val nClipboard: Boolean = true,
    // Engine / misc
    val engineChannel: String = "stable", // stable | nightly
    val onboarded: Boolean = false,
    val lastEngineCheck: Long = 0L,
)

object Settings {
    private const val FILE = "streamsnap_settings"
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Prefs())
    val state: StateFlow<Prefs> = _state.asStateFlow()
    val value: Prefs get() = _state.value

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _state.value = load()
        Tr.lang = _state.value.language
    }

    private fun load(): Prefs {
        val d = Prefs()
        return Prefs(
            themeMode = sp.getInt("themeMode", d.themeMode),
            amoled = sp.getBoolean("amoled", d.amoled),
            accent = sp.getInt("accent", d.accent),
            language = sp.getString("language", d.language) ?: d.language,
            autoKeyboard = sp.getBoolean("autoKeyboard", d.autoKeyboard),
            autoStart = sp.getBoolean("autoStart", d.autoStart),
            parallel = sp.getInt("parallel", d.parallel).coerceIn(1, 3),
            wifiOnly = sp.getBoolean("wifiOnly", d.wifiOnly),
            speedLimitKb = sp.getInt("speedLimitKb", d.speedLimitKb),
            proxy = sp.getString("proxy", d.proxy) ?: "",
            folder = sp.getString("folder", d.folder) ?: d.folder,
            nameTemplate = sp.getString("nameTemplate", d.nameTemplate) ?: d.nameTemplate,
            defaultQuality = sp.getString("defaultQuality", d.defaultQuality) ?: d.defaultQuality,
            qualityMode = sp.getString("qualityMode", d.qualityMode) ?: d.qualityMode,
            saveThumb = sp.getBoolean("saveThumb", d.saveThumb),
            saveSubs = sp.getBoolean("saveSubs", d.saveSubs),
            hiddenFolder = sp.getBoolean("hiddenFolder", d.hiddenFolder),
            useCookies = sp.getBoolean("useCookies", d.useCookies),
            scheduleEnabled = sp.getBoolean("scheduleEnabled", d.scheduleEnabled),
            scheduleHour = sp.getInt("scheduleHour", d.scheduleHour),
            scheduleMinute = sp.getInt("scheduleMinute", d.scheduleMinute),
            blockScreenshots = sp.getBoolean("blockScreenshots", d.blockScreenshots),
            appLock = sp.getBoolean("appLock", d.appLock),
            lockBiometric = sp.getBoolean("lockBiometric", d.lockBiometric),
            pinHash = sp.getString("pinHash", "") ?: "",
            pinSalt = sp.getString("pinSalt", "") ?: "",
            autoClearHistory = sp.getBoolean("autoClearHistory", d.autoClearHistory),
            autoDeleteDays = sp.getInt("autoDeleteDays", d.autoDeleteDays),
            notifSound = sp.getBoolean("notifSound", d.notifSound),
            notifVibrate = sp.getBoolean("notifVibrate", d.notifVibrate),
            nProgress = sp.getBoolean("nProgress", d.nProgress),
            nDone = sp.getBoolean("nDone", d.nDone),
            nFailed = sp.getBoolean("nFailed", d.nFailed),
            nSummary = sp.getBoolean("nSummary", d.nSummary),
            nNetwork = sp.getBoolean("nNetwork", d.nNetwork),
            nStorage = sp.getBoolean("nStorage", d.nStorage),
            nEngine = sp.getBoolean("nEngine", d.nEngine),
            nClipboard = sp.getBoolean("nClipboard", d.nClipboard),
            engineChannel = sp.getString("engineChannel", d.engineChannel) ?: d.engineChannel,
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            lastEngineCheck = sp.getLong("lastEngineCheck", d.lastEngineCheck),
        )
    }

    private fun save(p: Prefs) {
        sp.edit()
            .putInt("themeMode", p.themeMode)
            .putBoolean("amoled", p.amoled)
            .putInt("accent", p.accent)
            .putString("language", p.language)
            .putBoolean("autoKeyboard", p.autoKeyboard)
            .putBoolean("autoStart", p.autoStart)
            .putInt("parallel", p.parallel)
            .putBoolean("wifiOnly", p.wifiOnly)
            .putInt("speedLimitKb", p.speedLimitKb)
            .putString("proxy", p.proxy)
            .putString("folder", p.folder)
            .putString("nameTemplate", p.nameTemplate)
            .putString("defaultQuality", p.defaultQuality)
            .putString("qualityMode", p.qualityMode)
            .putBoolean("saveThumb", p.saveThumb)
            .putBoolean("saveSubs", p.saveSubs)
            .putBoolean("hiddenFolder", p.hiddenFolder)
            .putBoolean("useCookies", p.useCookies)
            .putBoolean("scheduleEnabled", p.scheduleEnabled)
            .putInt("scheduleHour", p.scheduleHour)
            .putInt("scheduleMinute", p.scheduleMinute)
            .putBoolean("blockScreenshots", p.blockScreenshots)
            .putBoolean("appLock", p.appLock)
            .putBoolean("lockBiometric", p.lockBiometric)
            .putString("pinHash", p.pinHash)
            .putString("pinSalt", p.pinSalt)
            .putBoolean("autoClearHistory", p.autoClearHistory)
            .putInt("autoDeleteDays", p.autoDeleteDays)
            .putBoolean("notifSound", p.notifSound)
            .putBoolean("notifVibrate", p.notifVibrate)
            .putBoolean("nProgress", p.nProgress)
            .putBoolean("nDone", p.nDone)
            .putBoolean("nFailed", p.nFailed)
            .putBoolean("nSummary", p.nSummary)
            .putBoolean("nNetwork", p.nNetwork)
            .putBoolean("nStorage", p.nStorage)
            .putBoolean("nEngine", p.nEngine)
            .putBoolean("nClipboard", p.nClipboard)
            .putString("engineChannel", p.engineChannel)
            .putBoolean("onboarded", p.onboarded)
            .putLong("lastEngineCheck", p.lastEngineCheck)
            .apply()
    }

    @Synchronized
    fun update(block: (Prefs) -> Prefs) {
        val next = block(_state.value)
        Tr.lang = next.language // must be set before observers see the new state
        _state.value = next
        save(next)
    }

    // ---- PIN helpers (salted SHA-256; the PIN itself is never stored) ----
    private fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val saltBytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        update { it.copy(pinSalt = salt, pinHash = hash(pin, salt), appLock = true) }
    }

    fun checkPin(pin: String): Boolean {
        val p = value
        return p.pinHash.isNotEmpty() && hash(pin, p.pinSalt) == p.pinHash
    }

    fun disableLock() = update { it.copy(appLock = false, pinHash = "", pinSalt = "") }

    // ---- Backup support ----
    fun exportMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        for ((k, v) in sp.all) {
            if (k == "pinHash" || k == "pinSalt") continue // never export lock secrets
            m[k] = v
        }
        return m
    }

    fun importMap(map: Map<String, Any?>) {
        val e = sp.edit()
        for ((k, v) in map) {
            if (k == "pinHash" || k == "pinSalt" || k == "appLock") continue
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> if (v % 1.0 == 0.0) e.putInt(k, v.toInt()) else e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        _state.value = load()
        Tr.lang = _state.value.language
    }
}
