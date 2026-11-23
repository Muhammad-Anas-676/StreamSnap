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
    val language: String = "en",         // en | ro | ur
    val autoKeyboard: Boolean = false,   // false = keyboard opens only when a text field is tapped
    val ytFast: Boolean = true,          // YouTube fast route (no JavaScript solving)
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
    // Custom video settings: when on, every video is saved with these settings (defaults = the "Video Converter" screen)
    val swipeActions: Boolean = false,   // swipe on a Downloading card: left = delete, right = pause/resume
    val cvOn: Boolean = false,
    val cvRes: Int = 240,                // short side in pixels, 0 = keep the original
    val cvFps: Int = 24,                 // 0 = keep the original
    val cvVCodec: String = "mpeg4",      // mpeg4 | h264 | h265
    val cvACodec: String = "aac",        // aac | mp3
    val cvQuality: Int = 3,              // 1 = best ... 5 = smallest
    val cvRotate: Int = 0,               // 0 | 90 | 180 | 270
    val cvFlip: String = "none",         // none | h | v
    val cvCompress: Boolean = false,
    val saveThumb: Boolean = false,
    val saveSubs: Boolean = false,
    val subLangs: String = "en,ur",      // subtitle languages (codes, comma separated)
    val autoRetry: Boolean = true,       // failed downloads try again by themselves (not for private/removed videos)
    val autoRetryMax: Int = 2,
    val siteFolders: Boolean = false,    // put downloads in a folder per site (YouTube, Instagram ...)
    val playlistAsk: Boolean = true,     // show a checklist when a playlist is pasted
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
            ytFast = sp.getBoolean("ytFast", d.ytFast),
            autoStart = sp.getBoolean("autoStart", d.autoStart),
            parallel = sp.getInt("parallel", d.parallel).coerceIn(1, 10),
            wifiOnly = sp.getBoolean("wifiOnly", d.wifiOnly),
            speedLimitKb = sp.getInt("speedLimitKb", d.speedLimitKb),
            proxy = sp.getString("proxy", d.proxy) ?: "",
            folder = sp.getString("folder", d.folder) ?: d.folder,
            nameTemplate = sp.getString("nameTemplate", d.nameTemplate) ?: d.nameTemplate,
            defaultQuality = sp.getString("defaultQuality", d.defaultQuality) ?: d.defaultQuality,
            qualityMode = sp.getString("qualityMode", d.qualityMode) ?: d.qualityMode,
            swipeActions = sp.getBoolean("swipeActions", d.swipeActions),
            cvOn = sp.getBoolean("cvOn", d.cvOn),
            cvRes = sp.getInt("cvRes", d.cvRes),
            cvFps = sp.getInt("cvFps", d.cvFps),
            cvVCodec = sp.getString("cvVCodec", d.cvVCodec) ?: d.cvVCodec,
            cvACodec = sp.getString("cvACodec", d.cvACodec) ?: d.cvACodec,
            cvQuality = sp.getInt("cvQuality", d.cvQuality).coerceIn(1, 5),
            cvRotate = sp.getInt("cvRotate", d.cvRotate),
            cvFlip = sp.getString("cvFlip", d.cvFlip) ?: d.cvFlip,
            cvCompress = sp.getBoolean("cvCompress", d.cvCompress),
            saveThumb = sp.getBoolean("saveThumb", d.saveThumb),
            saveSubs = sp.getBoolean("saveSubs", d.saveSubs),
            subLangs = sp.getString("subLangs", d.subLangs) ?: d.subLangs,
            autoRetry = sp.getBoolean("autoRetry", d.autoRetry),
            autoRetryMax = sp.getInt("autoRetryMax", d.autoRetryMax).coerceIn(1, 5),
            siteFolders = sp.getBoolean("siteFolders", d.siteFolders),
            playlistAsk = sp.getBoolean("playlistAsk", d.playlistAsk),
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
            .putBoolean("ytFast", p.ytFast)
            .putBoolean("autoStart", p.autoStart)
            .putInt("parallel", p.parallel)
            .putBoolean("wifiOnly", p.wifiOnly)
            .putInt("speedLimitKb", p.speedLimitKb)
            .putString("proxy", p.proxy)
            .putString("folder", p.folder)
            .putString("nameTemplate", p.nameTemplate)
            .putString("defaultQuality", p.defaultQuality)
            .putString("qualityMode", p.qualityMode)
            .putBoolean("swipeActions", p.swipeActions)
            .putBoolean("cvOn", p.cvOn)
            .putInt("cvRes", p.cvRes)
            .putInt("cvFps", p.cvFps)
            .putString("cvVCodec", p.cvVCodec)
            .putString("cvACodec", p.cvACodec)
            .putInt("cvQuality", p.cvQuality)
            .putInt("cvRotate", p.cvRotate)
            .putString("cvFlip", p.cvFlip)
            .putBoolean("cvCompress", p.cvCompress)
            .putBoolean("saveThumb", p.saveThumb)
            .putBoolean("saveSubs", p.saveSubs)
            .putString("subLangs", p.subLangs)
            .putBoolean("autoRetry", p.autoRetry)
            .putInt("autoRetryMax", p.autoRetryMax)
            .putBoolean("siteFolders", p.siteFolders)
            .putBoolean("playlistAsk", p.playlistAsk)
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

    // ---- PIN helpers: PBKDF2 with a random salt; the PIN itself is never stored ----
    private fun legacyHash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2(pin: String, salt: String): String {
        val algo = if (android.os.Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 60_000, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded
        return "v2:" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        sp.edit().putInt("pinFails", 0).putLong("pinLockedUntil", 0).apply()
        update { it.copy(pinSalt = salt, pinHash = pbkdf2(pin, salt), appLock = true) }
    }

    /** Seconds left before another PIN try is allowed (0 = allowed now). */
    fun lockRemainingSec(): Int {
        val left = sp.getLong("pinLockedUntil", 0) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun checkPin(pin: String): Boolean {
        if (lockRemainingSec() > 0) return false
        val p = value
        if (p.pinHash.isEmpty()) return false
        val ok = if (p.pinHash.startsWith("v2:")) {
            pbkdf2(pin, p.pinSalt) == p.pinHash
        } else {
            // A PIN saved by an older version: accept it once and upgrade it to the stronger format.
            val legacy = legacyHash(pin, p.pinSalt) == p.pinHash
            if (legacy) setPin(pin)
            legacy
        }
        if (ok) {
            sp.edit().putInt("pinFails", 0).putLong("pinLockedUntil", 0).apply()
        } else {
            // 5 wrong tries lock the PIN pad for 30 s, then longer each round (up to 15 min).
            val fails = sp.getInt("pinFails", 0) + 1
            val e = sp.edit().putInt("pinFails", fails)
            if (fails % 5 == 0) {
                val round = (fails / 5 - 1).coerceAtMost(5)
                e.putLong("pinLockedUntil", System.currentTimeMillis() + minOf(30_000L shl round, 900_000L))
            }
            e.apply()
        }
        return ok
    }

    fun disableLock() = update { it.copy(appLock = false, pinHash = "", pinSalt = "") }

    // ---- Backup support ----
    fun exportMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        for ((k, v) in sp.all) {
            if (k == "pinHash" || k == "pinSalt" || k == "pinFails" || k == "pinLockedUntil") continue // never export lock secrets
            m[k] = v
        }
        return m
    }

    fun importMap(map: Map<String, Any?>) {
        val e = sp.edit()
        for ((k, v) in map) {
            if (k == "pinHash" || k == "pinSalt" || k == "appLock" || k == "pinFails" || k == "pinLockedUntil") continue
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
