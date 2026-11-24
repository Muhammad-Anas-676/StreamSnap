package com.anas.streamsnap.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anas.streamsnap.core.cloud.ScreenTracker
import com.anas.streamsnap.core.theme.AppTheme
import com.anas.streamsnap.session.ProfileStore
import com.anas.streamsnap.session.Session
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.AddResult
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    val locked = MutableStateFlow(false)
    val navRequest = MutableStateFlow<String?>(null)
    /** Text of a link found in the clipboard that the user has not acted on yet. */
    val clipLink = MutableStateFlow<String?>(null)

    private var backgroundedAt = 0L
    private var lastClip = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked.value = Settings.value.appLock
        if (savedInstanceState == null) handleIntent(intent)

        // Screenshot block is a live switch in Settings.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                Settings.state.map { it.blockScreenshots }.distinctUntilChanged().collect { applySecure(it) }
            }
        }
        // The keyboard opens only when a text field is tapped.
        applyKeyboardMode()

        setContent {
            val prefs by Settings.state.collectAsState()
            // Rebuild the whole UI when the language changes.
            key(prefs.language) {
                AppTheme { AppRoot(this@MainActivity) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_NAV)?.let { navRequest.value = it }
        if (i.action == Intent.ACTION_SEND && (i.type ?: "").startsWith("text/")) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val r = QueueManager.addLinks(text)
            Toast.makeText(this, addResultMessage(r), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyKeyboardMode() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun applySecure(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        ScreenTracker.resume()
        Session.setForeground(true)
        Notifier.clearClipboard(this)
        if (Settings.value.appLock && backgroundedAt > 0 &&
            SystemClock.elapsedRealtime() - backgroundedAt > 20_000
        ) locked.value = true
    }

    override fun onStop() {
        // The screen timer stops while the app is in the background; the visit is saved now.
        ScreenTracker.pause()
        HowItWorksPill.onSessionEnd() // the first session is over: the Queue pill never shows again
        Session.setForeground(false)
        ProfileStore.markLeft()
        backgroundedAt = SystemClock.elapsedRealtime()
        QueueManager.saveNow()
        // Link still sitting in the clipboard card? Leave a notification with an "Add" button.
        clipLink.value?.let { t -> Utils.extractUrls(t).firstOrNull()?.let { Notifier.clipboard(this, it) } }
        super.onStop()
    }

    override fun onDestroy() {
        Session.setForeground(false)
        if (isFinishing) QueueManager.onAppClosed()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android only lets the focused app read the clipboard.
        if (hasFocus) checkClipboard()
    }

    private fun checkClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = try {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: Throwable) { null } ?: return
        val urls = Utils.extractUrls(text)
        if (urls.isEmpty()) return
        val first = urls.first()
        if (first == lastClip) return
        lastClip = first
        val known = QueueManager.items.value.any { Utils.normalizeUrl(it.url) == Utils.normalizeUrl(first) }
        if (!known) clipLink.value = text
    }

    fun dismissClip() { clipLink.value = null }

    companion object {
        const val EXTRA_NAV = "nav"
    }
}

fun addResultMessage(r: AddResult): String = when {
    r.noLinks -> tr("No link found", "کوئی لنک نہیں ملا")
    r.added == 0 && r.duplicates > 0 -> tr("Already in your list", "پہلے سے فہرست میں موجود ہے")
    r.duplicates > 0 -> tr("Added ${r.added}, ${r.duplicates} already in list", "${r.added} شامل ہوئیں، ${r.duplicates} پہلے سے موجود")
    else -> tr("Added ${r.added} link(s)", "${r.added} لنک شامل ہو گئے")
}
