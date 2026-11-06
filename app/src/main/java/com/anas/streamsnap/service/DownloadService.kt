package com.anas.streamsnap.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.LogStore

/** Keeps downloads alive when the app is in the background (foreground service + CPU wake lock). */
class DownloadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.queueNotification(this, QueueManager.snapshot())
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this, Notifier.ID_QUEUE, n, type)
        } catch (t: Throwable) {
            LogStore.w("service", "Could not start the background service (downloads still run while the app is open)", detail = t.toString())
            QueueManager.serviceRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        QueueManager.serviceRunning = true
        if (wakeLock == null) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StreamSnap:downloads").apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L)
                }
            } catch (_: Throwable) { }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        QueueManager.serviceRunning = false
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Throwable) { }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (t: Throwable) {
                LogStore.w("service", "Could not start the background service (downloads still run while the app is open)", detail = t.toString())
            }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, DownloadService::class.java)) } catch (_: Throwable) { }
            QueueManager.serviceRunning = false
        }
    }
}
