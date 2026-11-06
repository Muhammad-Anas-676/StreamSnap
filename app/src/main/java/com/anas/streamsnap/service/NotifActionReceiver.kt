package com.anas.streamsnap.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anas.streamsnap.engine.QueueManager

/** Buttons inside notifications: Pause, Cancel, Retry, Pause all, Cancel all, Add clipboard link. */
class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: ""
        when (intent.action) {
            ACTION_PAUSE -> QueueManager.pause(id)
            ACTION_RESUME -> QueueManager.resume(id)
            ACTION_CANCEL -> QueueManager.cancel(id)
            ACTION_RETRY -> { QueueManager.retry(id); Notifier.clearItem(context, id) }
            ACTION_PAUSE_ALL -> QueueManager.pauseAll()
            ACTION_CANCEL_ALL -> QueueManager.cancelAll()
            ACTION_ADD_URL -> {
                intent.getStringExtra("url")?.let { QueueManager.addLinks(it) }
                Notifier.clearClipboard(context)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.anas.streamsnap.PAUSE"
        const val ACTION_RESUME = "com.anas.streamsnap.RESUME"
        const val ACTION_CANCEL = "com.anas.streamsnap.CANCEL"
        const val ACTION_RETRY = "com.anas.streamsnap.RETRY"
        const val ACTION_PAUSE_ALL = "com.anas.streamsnap.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.anas.streamsnap.CANCEL_ALL"
        const val ACTION_ADD_URL = "com.anas.streamsnap.ADD_URL"
    }
}
