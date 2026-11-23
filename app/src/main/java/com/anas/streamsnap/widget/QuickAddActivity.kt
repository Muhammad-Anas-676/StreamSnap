package com.anas.streamsnap.widget

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.ui.addResultMessage
import com.anas.streamsnap.util.LogStore
import com.anas.streamsnap.util.Utils

/**
 * Invisible activity opened by the widget. It reads the clipboard (Android only allows that while a
 * window has focus), adds the copied link(s) to the queue, shows a short message and closes.
 */
class QuickAddActivity : Activity() {
    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            } else ""
            if (Utils.extractUrls(text).isEmpty()) {
                Toast.makeText(this, tr("No link copied. Copy a video link first.", "کوئی لنک کاپی نہیں۔ پہلے ویڈیو لنک کاپی کریں۔"), Toast.LENGTH_LONG).show()
            } else {
                val r = QueueManager.addLinks(text)
                Toast.makeText(this, addResultMessage(r), Toast.LENGTH_LONG).show()
            }
        } catch (t: Throwable) {
            LogStore.e("widget", "Quick paste failed", t = t)
            Toast.makeText(this, tr("Could not add the link", "لنک شامل نہیں ہو سکا"), Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
