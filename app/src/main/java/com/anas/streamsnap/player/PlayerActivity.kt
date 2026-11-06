package com.anas.streamsnap.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.ui.StreamSnapTheme
import com.anas.streamsnap.util.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

private data class SubSource(val uri: Uri, val lang: String?, val label: String, val picked: Boolean)

private fun fmtMs(ms: Long): String {
    val t = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun fmtSpeedLabel(x: Float): String = if (x % 1f == 0f) "${x.toInt()}x" else "${x}x"

/**
 * Built-in player. Gestures: tap = show/hide controls, double tap = +-10s (middle = play/pause),
 * long press = 2x while held, horizontal drag = seek, vertical drag = brightness (left) / volume (right, up to 200%),
 * pinch = zoom. No background play: playback stops when the app is left (except Picture-in-Picture).
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_LIST = "list"

        fun intent(ctx: Context, path: String, playlist: ArrayList<String>? = null): Intent =
            Intent(ctx, PlayerActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
                if (playlist != null) putStringArrayListExtra(EXTRA_LIST, playlist)
            }
    }

    // ---- engine objects
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var gestures: GestureLayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = -1
    private val scope: CoroutineScope = MainScope()
    private var sleepJob: Job? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val positions by lazy { getSharedPreferences("player_positions", MODE_PRIVATE) }

    // ---- what is playing
    private var playlist = ArrayList<String>()
    private var index by mutableIntStateOf(0)
    private val subs = ArrayList<SubSource>()
    private var videoW = 0
    private var videoH = 0

    // ---- UI state
    private var videoTitle by mutableStateOf("")
    private var playing by mutableStateOf(false)
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(0L)
    private var nowTick by mutableLongStateOf(0L)
    private var controlsVisible by mutableStateOf(true)
    private var interaction by mutableIntStateOf(0)
    private var locked by mutableStateOf(false)
    private var showUnlock by mutableStateOf(false)
    private var speed by mutableFloatStateOf(1f)
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var tracks by mutableStateOf(Tracks.EMPTY)
    private var sheet by mutableStateOf<String?>(null)
    private var hudText by mutableStateOf("")
    private var hudVisible by mutableStateOf(false)
    private var hudTick by mutableIntStateOf(0)
    private var holdHud = false
    private var inPip by mutableStateOf(false)
    private var sleepEndsAt by mutableLongStateOf(0L)
    private var sleepAtEnd by mutableStateOf(false)
    private var boost by mutableFloatStateOf(1f)
    private var sliderDragging by mutableStateOf(false)

    // ---- gesture helpers
    private var prevSpeed = 1f
    private var seekStart = 0L
    private var seekTarget = 0L
    private var brightness = -1f
    private var zoom = 1f
    private var userOrientation = false

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addExternalSub(uri)
    }

    // ============================================================================ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.value.blockScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        watchBars()

        val p = ExoPlayer.Builder(this).build()
        player = p
        p.addListener(playerListener)
        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                ensureEnhancer()
            }
        })

        val pv = PlayerView(this).apply {
            useController = false
            player = p
            setKeepContentOnPlayerReset(true)
        }
        playerView = pv
        gestures = GestureLayer(this).also { wireGestures(it) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    showUnlock = true
                    Toast.makeText(this@PlayerActivity, tr("Screen is locked. Unlock first.", "اسکرین لاک ہے۔ پہلے اَن لاک کریں۔"), Toast.LENGTH_SHORT).show()
                } else finish()
            }
        })

        handleIntent(intent)

        setContent {
            val prefs by Settings.state.collectAsState()
            StreamSnapTheme(prefs) { PlayerUi() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onStop() {
        super.onStop()
        // No background play: stop when the screen is left. Picture-in-Picture keeps playing.
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        saveProgress()
        sleepJob?.cancel()
        scope.cancel()
        try { enhancer?.release() } catch (_: Throwable) { }
        enhancer = null
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && playing) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { sheet = null; controlsVisible = false }
    }

    /** Full screen: no status bar (clock, battery, Wi-Fi) and no navigation buttons. */
    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Older Android: set the immersive flags directly so they cannot be lost.
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** If the bars come back (edge swipe, dialog, sheet), hide them again after a moment. */
    @Suppress("DEPRECATION")
    private fun watchBars() {
        if (Build.VERSION.SDK_INT >= 30) return // the controller's transient behaviour handles this
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 && !isInPictureInPictureMode) {
                window.decorView.postDelayed({ hideBars() }, 700)
            }
        }
    }

    // Dialogs and bottom sheets take the window focus and bring the bars back: hide them again.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    // ============================================================================ loading
    private fun handleIntent(i: Intent) {
        val path = i.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) { finish(); return }
        val list = i.getStringArrayListExtra(EXTRA_LIST)
        playlist = if (list != null && list.isNotEmpty()) ArrayList(list) else arrayListOf(path)
        var at = playlist.indexOf(path)
        if (at < 0) { playlist.add(0, path); at = 0 }
        index = at
        loadCurrent(resume = true)
    }

    private fun sidecarSubs(f: File): List<SubSource> {
        val base = f.nameWithoutExtension
        val dir = f.parentFile ?: return emptyList()
        val files = try { dir.listFiles() } catch (_: Throwable) { null } ?: return emptyList()
        return files
            .filter { it.isFile && it.name.endsWith(".srt", true) && it.name.startsWith(base) }
            .map { x ->
                val mid = x.name.substring(base.length).dropLast(4).trim('.')
                SubSource(Uri.fromFile(x), mid.ifBlank { null }, if (mid.isBlank()) x.name else mid, false)
            }
    }

    private fun buildItem(f: File): MediaItem {
        val cfgs = subs.map { s ->
            MediaItem.SubtitleConfiguration.Builder(s.uri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(s.lang)
                .setLabel(s.label)
                .setSelectionFlags(if (s.picked) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return MediaItem.Builder().setUri(Uri.fromFile(f)).setSubtitleConfigurations(cfgs).build()
    }

    private fun loadCurrent(resume: Boolean) {
        val p = player ?: return
        val path = playlist.getOrNull(index) ?: return
        val f = File(path)
        if (!f.exists()) {
            Toast.makeText(this, tr("File not found", "فائل نہیں ملی"), Toast.LENGTH_LONG).show()
            LogStore.w("player", "File not found: $path")
            finish()
            return
        }
        videoTitle = f.nameWithoutExtension
        subs.clear()
        subs.addAll(sidecarSubs(f))
        userOrientation = false
        val start = if (resume) positions.getLong(path, 0L) else 0L
        if (start > 0) p.setMediaItem(buildItem(f), start) else p.setMediaItem(buildItem(f))
        p.prepare()
        p.playWhenReady = true
        controlsVisible = true
    }

    private fun reloadKeepPosition() {
        val p = player ?: return
        val f = File(playlist.getOrNull(index) ?: return)
        val pos = p.currentPosition
        val wasPlaying = p.playWhenReady
        p.setMediaItem(buildItem(f), pos)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    private fun addExternalSub(uri: Uri) {
        val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle.srt"
        subs.removeAll { it.picked }
        subs.add(SubSource(uri, null, label, true))
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        reloadKeepPosition()
        showHud(tr("Subtitle loaded", "سب ٹائٹل لوڈ ہو گیا"))
    }

    private fun saveProgress() {
        val p = player ?: return
        val path = playlist.getOrNull(index) ?: return
        try {
            val d = p.duration
            val pos = p.currentPosition
            val e = positions.edit()
            if (d != C.TIME_UNSET && d > 0 && pos > d - 5000) e.remove(path)
            else if (pos > 5000) e.putLong(path, pos)
            e.apply()
        } catch (_: Throwable) { }
    }

    private fun onEnded() {
        val path = playlist.getOrNull(index)
        if (path != null) positions.edit().remove(path).apply()
        if (sleepAtEnd) {
            sleepAtEnd = false
            Toast.makeText(this, tr("Sleep timer: stopped", "سلیپ ٹائمر: رک گیا"), Toast.LENGTH_SHORT).show()
            controlsVisible = true
            return
        }
        if (index + 1 < playlist.size) {
            index += 1
            loadCurrent(resume = false)
        } else {
            controlsVisible = true
        }
    }

    // ============================================================================ player events
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ensureEnhancer()
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onTracksChanged(t: Tracks) { tracks = t }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width
            videoH = videoSize.height
            autoOrient()
        }

        override fun onPlayerError(error: PlaybackException) {
            val path = playlist.getOrNull(index) ?: ""
            LogStore.e("player", "Cannot play this file: ${error.errorCodeName}", url = path, t = error)
            Toast.makeText(
                this@PlayerActivity,
                tr("Cannot play this file (see Log)", "یہ فائل نہیں چل سکی (لاگ دیکھیں)"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // ============================================================================ gestures
    private fun wireGestures(g: GestureLayer) {
        g.cbSingleTap = {
            if (locked) showUnlock = !showUnlock
            else { controlsVisible = !controlsVisible; interaction++ }
        }
        g.cbDoubleTap = { zone ->
            when (zone) {
                -1 -> seekBy(-10_000, "-10s")
                1 -> seekBy(10_000, "+10s")
                else -> togglePlay()
            }
        }
        g.cbLongPress = { start ->
            val p = player
            if (p != null) {
                if (start) {
                    prevSpeed = speed
                    p.setPlaybackSpeed(2f)
                    showHud("2x", hold = true)
                } else {
                    p.setPlaybackSpeed(prevSpeed)
                    hideHud()
                }
            }
        }
        g.cbSeekStart = {
            seekStart = player?.currentPosition ?: 0L
            seekTarget = seekStart
        }
        g.cbSeekDrag = { frac ->
            val d = player?.duration ?: C.TIME_UNSET
            val max = if (d == C.TIME_UNSET || d <= 0) Long.MAX_VALUE else d
            val delta = (frac * 120_000f).toLong() // dragging the full width = 2 minutes
            seekTarget = (seekStart + delta).coerceIn(0L, max)
            val sign = if (delta >= 0) "+" else "-"
            showHud("${fmtMs(seekTarget)}  ($sign${fmtMs(kotlin.math.abs(delta))})", hold = true)
        }
        g.cbSeekEnd = { commit ->
            if (commit) player?.seekTo(seekTarget)
            hideHud()
        }
        g.cbVertical = { left, delta -> if (left) changeBrightness(delta) else changeVolume(delta) }
        g.cbScale = { f ->
            zoom = (zoom * f).coerceIn(1f, 4f)
            applyZoom()
            showHud("${tr("Zoom", "زوم")} ${(zoom * 100).roundToInt()}%")
        }
    }

    private fun applyZoom() {
        playerView?.scaleX = zoom
        playerView?.scaleY = zoom
    }

    private fun seekBy(ms: Long, label: String) {
        val p = player ?: return
        var t = (p.currentPosition + ms).coerceAtLeast(0L)
        val d = p.duration
        if (d != C.TIME_UNSET && d > 0) t = t.coerceAtMost(d)
        p.seekTo(t)
        showHud(label)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    private fun showHud(text: String, hold: Boolean = false) {
        hudText = text
        hudVisible = true
        holdHud = hold
        hudTick++
    }

    private fun hideHud() {
        holdHud = false
        hudVisible = false
    }

    private fun currentBrightness(): Float {
        val w = window.attributes.screenBrightness
        if (w >= 0f) return w
        return try {
            AndroidSettings.System.getInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Throwable) { 0.5f }
    }

    private fun changeBrightness(delta: Float) {
        if (brightness < 0f) brightness = currentBrightness()
        brightness = (brightness + delta * 1.3f).coerceIn(0.02f, 1f)
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
        showHud("${tr("Brightness", "روشنی")} ${(brightness * 100).roundToInt()}%")
    }

    /** Volume 0..200%: up to 100% moves the phone volume, above that the boost is raised. */
    private fun changeVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var level = if (boost > 1.001f) boost else cur.toFloat() / max
        level = (level + delta * 1.4f).coerceIn(0f, 2f)
        if (level <= 1f) {
            boost = 1f
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
        } else {
            if (cur < max) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            boost = level
        }
        applyBoost()
        showHud("${tr("Volume", "آواز")} ${(level * 100).roundToInt()}%")
    }

    private fun ensureEnhancer() {
        val p = player ?: return
        val sid = p.audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        try {
            if (enhancer == null || enhancerSession != sid) {
                enhancer?.release()
                enhancer = LoudnessEnhancer(sid)
                enhancerSession = sid
            }
            applyBoost()
        } catch (t: Throwable) {
            enhancer = null
            LogStore.w("player", "Volume boost is not available on this phone", detail = t.toString())
        }
    }

    private fun applyBoost() {
        val e = enhancer ?: return
        try {
            val gainMb = (2000.0 * log10(boost.toDouble().coerceAtLeast(1.0))).toInt()
            e.setTargetGain(gainMb)
            e.enabled = boost > 1.001f
        } catch (_: Throwable) { }
    }

    // ============================================================================ orientation, PiP, sleep
    private fun autoOrient() {
        if (userOrientation || videoW <= 0 || videoH <= 0) return
        requestedOrientation = if (videoW >= videoH) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    private fun rotate() {
        userOrientation = true
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, tr("Picture-in-Picture needs Android 8+", "پکچر اِن پکچر کے لیے اینڈرائیڈ 8+ چاہیے"), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val b = PictureInPictureParams.Builder()
            if (videoW > 0 && videoH > 0) {
                val r = (videoW.toFloat() / videoH).coerceIn(0.42f, 2.39f)
                b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
            }
            enterPictureInPictureMode(b.build())
        } catch (t: Throwable) {
            LogStore.w("player", "Could not enter Picture-in-Picture", detail = t.toString())
            Toast.makeText(this, tr("Picture-in-Picture is not available", "پکچر اِن پکچر دستیاب نہیں"), Toast.LENGTH_SHORT).show()
        }
    }

    /** minutes: 0 = off, -1 = end of this video. */
    private fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepEndsAt = 0L
        sleepAtEnd = false
        when {
            minutes > 0 -> {
                sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
                sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    player?.pause()
                    sleepEndsAt = 0L
                    Toast.makeText(this@PlayerActivity, tr("Sleep timer: stopped", "سلیپ ٹائمر: رک گیا"), Toast.LENGTH_SHORT).show()
                }
                showHud(tr("Sleep in $minutes min", "$minutes منٹ بعد بند"))
            }
            minutes < 0 -> {
                sleepAtEnd = true
                showHud(tr("Stops after this video", "اس ویڈیو کے بعد رک جائے گا"))
            }
            else -> showHud(tr("Sleep timer off", "سلیپ ٹائمر بند"))
        }
    }

    // ============================================================================ tracks
    private fun trackLabel(g: Tracks.Group, n: Int): String {
        val f = g.getTrackFormat(0)
        val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
        val base = f.label ?: lang ?: "${tr("Track", "ٹریک")} ${n + 1}"
        val extra = buildString {
            if (f.channelCount > 0) append(" · ${f.channelCount}ch")
            if (f.bitrate > 0) append(" · ${f.bitrate / 1000}kbps")
        }
        return base + extra
    }

    private fun selectText(group: Tracks.Group?) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        if (group == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        p.trackSelectionParameters = b.build()
    }

    private fun selectAudio(group: Tracks.Group) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    // ============================================================================ UI
    @Composable
    private fun PlayerUi() {
        val pv = playerView ?: return
        val gl = gestures ?: return

        LaunchedEffect(Unit) {
            while (true) {
                player?.let { p ->
                    if (!sliderDragging) position = p.currentPosition
                    val d = p.duration
                    duration = if (d == C.TIME_UNSET) 0L else d
                }
                nowTick = System.currentTimeMillis()
                delay(250)
            }
        }
        LaunchedEffect(hudTick) {
            if (hudVisible && !holdHud) {
                delay(900)
                if (!holdHud) hudVisible = false
            }
        }
        LaunchedEffect(controlsVisible, playing, interaction, sheet) {
            if (controlsVisible && playing && sheet == null && !sliderDragging) {
                delay(3500)
                controlsVisible = false
            }
        }
        LaunchedEffect(showUnlock) {
            if (showUnlock) { delay(2500); showUnlock = false }
        }
        LaunchedEffect(resizeMode) { pv.resizeMode = resizeMode }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { pv }, modifier = Modifier.fillMaxSize())
            AndroidView(factory = { gl }, modifier = Modifier.fillMaxSize(), update = { it.locked = locked })

            if (!inPip) {
                // HUD (volume, brightness, seek, speed)
                AnimatedVisibility(
                    visible = hudVisible,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.65f)) {
                        Text(
                            hudText, color = Color.White, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }

                // Locked: only a small unlock button
                if (locked) {
                    AnimatedVisibility(
                        visible = showUnlock,
                        enter = fadeIn(), exit = fadeOut(),
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                .clickable { locked = false; controlsVisible = true; interaction++ },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.LockOpen, contentDescription = tr("Unlock", "اَن لاک"), tint = Color.White) }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible && !locked,
                    enter = fadeIn(), exit = fadeOut(),
                ) { Controls() }
            }

            ActiveSheet()
        }
    }

    @Composable
    private fun Controls() {
        Box(Modifier.fillMaxSize()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "پیچھے"), tint = Color.White)
                }
                Text(
                    videoTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { enterPip() }) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                }
            }

            // centre buttons
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (playlist.size > 1) {
                    IconButton(
                        onClick = { if (index > 0) { saveProgress(); index -= 1; loadCurrent(true) }; interaction++ },
                        enabled = index > 0,
                    ) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                        .clickable { togglePlay(); interaction++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp),
                    )
                }
                if (playlist.size > 1) {
                    IconButton(
                        onClick = { if (index + 1 < playlist.size) { saveProgress(); index += 1; loadCurrent(true) }; interaction++ },
                        enabled = index + 1 < playlist.size,
                    ) { Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }

            // bottom: seek bar + tools
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .navigationBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                var dragValue by remember { mutableFloatStateOf(0f) }
                val maxV = if (duration > 0) duration.toFloat() else 1f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtMs(if (sliderDragging) dragValue.toLong() else position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = (if (sliderDragging) dragValue else position.toFloat()).coerceIn(0f, maxV),
                        onValueChange = { sliderDragging = true; dragValue = it; interaction++ },
                        onValueChangeFinished = {
                            player?.seekTo(dragValue.toLong())
                            position = dragValue.toLong()
                            sliderDragging = false
                        },
                        valueRange = 0f..maxV,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(fmtMs(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ToolButton(Icons.Filled.Lock, tr("Lock", "لاک")) { locked = true; controlsVisible = false }
                    ToolButton(Icons.Filled.Speed, fmtSpeedLabel(speed)) { sheet = "speed" }
                    ToolButton(Icons.Filled.AspectRatio, tr("Aspect", "تناسب")) { sheet = "aspect" }
                    ToolButton(Icons.Filled.Subtitles, tr("Subtitles", "سب ٹائٹل")) { sheet = "subs" }
                    ToolButton(Icons.Filled.Audiotrack, tr("Audio", "آڈیو")) { sheet = "audio" }
                    ToolButton(Icons.AutoMirrored.Filled.VolumeUp, if (boost > 1.001f) "${(boost * 100).roundToInt()}%" else tr("Boost", "بوسٹ")) { sheet = "boost" }
                    val remain = if (sleepEndsAt > 0) (((sleepEndsAt - nowTick) / 60_000L) + 1).coerceAtLeast(1) else 0L
                    ToolButton(
                        Icons.Filled.Bedtime,
                        when {
                            remain > 0 -> "${remain}m"
                            sleepAtEnd -> tr("End", "اختتام")
                            else -> tr("Sleep", "سلیپ")
                        },
                    ) { sheet = "sleep" }
                    if (playlist.size > 1) ToolButton(Icons.AutoMirrored.Filled.PlaylistPlay, "${index + 1}/${playlist.size}") { sheet = "list" }
                    ToolButton(Icons.Filled.ScreenRotation, tr("Rotate", "گھمائیں")) { rotate() }
                }
            }
        }
    }

    @Composable
    private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { interaction++; onClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp).width(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    // ---- option sheets
    @Composable
    private fun SheetDialog(title: String, content: @Composable () -> Unit) {
        AlertDialog(
            onDismissRequest = { sheet = null },
            title = { Text(title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
            confirmButton = { TextButton(onClick = { sheet = null }) { Text(tr("Close", "بند کریں")) } },
        )
    }

    @Composable
    private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    private fun ActiveSheet() {
        when (sheet) {
            "speed" -> SheetDialog(tr("Playback speed", "پلے بیک اسپیڈ")) {
                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f).forEach { x ->
                    OptionRow(fmtSpeedLabel(x), speed == x) {
                        speed = x
                        prevSpeed = x
                        player?.setPlaybackSpeed(x)
                        sheet = null
                    }
                }
            }
            "aspect" -> SheetDialog(tr("Aspect ratio", "اسپیکٹ ریشو")) {
                OptionRow(tr("Fit", "فٹ"), resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; sheet = null
                }
                OptionRow(tr("Stretch", "کھینچیں"), resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; sheet = null
                }
                OptionRow(tr("Crop (fill screen)", "کراپ (پوری اسکرین)"), resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; sheet = null
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { zoom = 1f; applyZoom(); sheet = null }) { Text(tr("Reset pinch zoom", "پنچ زوم ری سیٹ")) }
            }
            "subs" -> {
                val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val textOff = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
                SheetDialog(tr("Subtitles", "سب ٹائٹلز")) {
                    OptionRow(tr("Off", "بند"), textOff || groups.none { it.isSelected }) { selectText(null); sheet = null }
                    groups.forEachIndexed { n, g ->
                        OptionRow(trackLabel(g, n), !textOff && g.isSelected) { selectText(g); sheet = null }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        sheet = null
                        try { subPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) } catch (_: Throwable) { }
                    }) { Text(tr("Load .srt file…", ".srt فائل لوڈ کریں…")) }
                    if (groups.isEmpty()) {
                        Text(
                            tr("No subtitles found with this video.", "اس ویڈیو کے ساتھ کوئی سب ٹائٹل نہیں ملا۔"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            "audio" -> {
                val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                SheetDialog(tr("Audio track", "آڈیو ٹریک")) {
                    if (groups.isEmpty()) {
                        Text(tr("No audio track found.", "کوئی آڈیو ٹریک نہیں ملا۔"))
                    }
                    groups.forEachIndexed { n, g ->
                        OptionRow(trackLabel(g, n), g.isSelected) { selectAudio(g); sheet = null }
                    }
                    if (groups.size == 1) {
                        Text(
                            tr("This video has only one audio track.", "اس ویڈیو میں صرف ایک آڈیو ٹریک ہے۔"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            "boost" -> SheetDialog(tr("Volume boost", "آواز بوسٹ")) {
                listOf(1f, 1.25f, 1.5f, 1.75f, 2f).forEach { b ->
                    OptionRow("${(b * 100).roundToInt()}%", kotlin.math.abs(boost - b) < 0.01f) {
                        boost = b
                        ensureEnhancer()
                        applyBoost()
                        sheet = null
                    }
                }
                Text(
                    tr(
                        "Above 100% makes quiet videos louder. Very high boost can distort the sound. You can also swipe up on the right side of the screen.",
                        "100% سے اوپر ہلکی آواز والی ویڈیوز بلند ہوتی ہیں۔ بہت زیادہ بوسٹ سے آواز بگڑ سکتی ہے۔ اسکرین کے دائیں حصے پر اوپر سوائپ بھی کر سکتے ہیں۔",
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "sleep" -> SheetDialog(tr("Sleep timer", "سلیپ ٹائمر")) {
                OptionRow(tr("Off", "بند"), sleepEndsAt == 0L && !sleepAtEnd) { setSleep(0); sheet = null }
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    OptionRow(tr("$m minutes", "$m منٹ"), false) { setSleep(m); sheet = null }
                }
                OptionRow(tr("End of this video", "اس ویڈیو کے اختتام پر"), sleepAtEnd) { setSleep(-1); sheet = null }
            }
            "list" -> SheetDialog(tr("Playlist", "پلے لسٹ")) {
                playlist.forEachIndexed { n, path ->
                    OptionRow(File(path).nameWithoutExtension, n == index) {
                        if (n != index) { saveProgress(); index = n; loadCurrent(true) }
                        sheet = null
                    }
                }
            }
            else -> {}
        }
    }
}
