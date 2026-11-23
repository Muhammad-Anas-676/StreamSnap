package com.anas.streamsnap.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anas.streamsnap.R
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.player.PlayerActivity
import com.anas.streamsnap.ui.MainActivity
import com.anas.streamsnap.util.Utils
import java.io.File

data class QueueSnapshot(
    val downloading: Int,
    val queued: Int,
    val waiting: Int,
    val done: Int,
    val failed: Int,
    val total: Int,
    val overall: Float,
    val speed: String,
    val waitReason: String,
)

object Notifier {
    const val ID_QUEUE = 1001
    private const val ID_SUMMARY = 1002
    private const val ID_NETWORK = 1003
    private const val ID_STORAGE = 1004
    private const val ID_ENGINE = 1005
    private const val ID_CLIPBOARD = 1006
    private const val BASE_PROGRESS = 100_000
    private const val BASE_DONE = 200_000
    private const val BASE_FAILED = 300_000

    private const val CH_PROGRESS = "progress"
    private const val CH_CLIPBOARD = "clipboard"
    private val SOUND_BASES = listOf("done", "failed", "alerts", "engine")

    private fun suffix(p: Prefs) = "s${if (p.notifSound) 1 else 0}v${if (p.notifVibrate) 1 else 0}"
    private fun chan(base: String, p: Prefs) = "${base}_${suffix(p)}"

    // ------------------------------------------------------------ channels
    /** Call at start and whenever the sound/vibrate toggles or the language change. */
    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val p = Settings.value
        val current = suffix(p)

        for (base in SOUND_BASES) for (s in 0..1) for (v in 0..1) {
            val sfx = "s${s}v${v}"
            if (sfx != current) nm.deleteNotificationChannel("${base}_$sfx")
        }

        fun sound(id: String, name: String, desc: String) {
            val ch = NotificationChannel(id, name, if (p.notifSound) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW)
            ch.description = desc
            ch.enableVibration(p.notifVibrate)
            if (!p.notifSound) ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }

        val prog = NotificationChannel(CH_PROGRESS, tr("Download progress", "ڈاؤن لوڈ کی پیش رفت"), NotificationManager.IMPORTANCE_LOW)
        prog.setSound(null, null); prog.enableVibration(false)
        nm.createNotificationChannel(prog)

        val clip = NotificationChannel(CH_CLIPBOARD, tr("Clipboard links", "کلپ بورڈ لنکس"), NotificationManager.IMPORTANCE_LOW)
        clip.setSound(null, null); clip.enableVibration(false)
        nm.createNotificationChannel(clip)

        sound(chan("done", p), tr("Completed downloads", "مکمل ڈاؤن لوڈز"), tr("A video finished downloading", "ویڈیو ڈاؤن لوڈ ہو گئی"))
        sound(chan("failed", p), tr("Failed downloads", "ناکام ڈاؤن لوڈز"), tr("A download failed (Retry button)", "ڈاؤن لوڈ ناکام (دوبارہ کوشش کا بٹن)"))
        sound(chan("alerts", p), tr("Alerts", "الرٹس"), tr("Summary, network lost, low storage", "خلاصہ، نیٹ ورک ختم، کم اسٹوریج"))
        sound(chan("engine", p), tr("Engine updates", "انجن اپڈیٹس"), tr("New yt-dlp engine", "نیا yt-dlp انجن"))
    }

    // ------------------------------------------------------------ intents
    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openApp(ctx: Context, nav: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (nav != null) putExtra(MainActivity.EXTRA_NAV, nav)
        }
        return PendingIntent.getActivity(ctx, (nav ?: "home").hashCode(), i, flags())
    }

    private fun play(ctx: Context, path: String): PendingIntent {
        val i = PlayerActivity.intent(ctx, path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, path.hashCode(), i, flags())
    }

    private fun action(ctx: Context, act: String, id: String = "", url: String = ""): PendingIntent {
        val i = Intent(ctx, NotifActionReceiver::class.java).setAction(act)
            .putExtra("id", id).putExtra("url", url)
        return PendingIntent.getBroadcast(ctx, (act + id + url).hashCode(), i, flags())
    }

    // ------------------------------------------------------------ builders
    private fun base(ctx: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(0xFF1C68FA.toInt())

    private fun alertDefaults(b: NotificationCompat.Builder, p: Prefs): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT < 26) {
            var d = 0
            if (p.notifSound) d = d or Notification.DEFAULT_SOUND
            if (p.notifVibrate) d = d or Notification.DEFAULT_VIBRATE
            b.setDefaults(d)
        }
        return b
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: Notification) {
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: Throwable) { }
    }

    private fun cancel(ctx: Context, id: Int) {
        try { NotificationManagerCompat.from(ctx).cancel(id) } catch (_: Throwable) { }
    }

    private fun nid(base: Int, id: String) = base + (id.hashCode() and 0xFFFF)

    // ------------------------------------------------------------ queue (foreground service) notification
    fun queueNotification(ctx: Context, s: QueueSnapshot): Notification {
        val title = when {
            s.downloading > 0 -> tr("Downloading ${s.downloading} video(s)", "${s.downloading} ویڈیو ڈاؤن لوڈ ہو رہی ہیں")
            s.waiting > 0 -> s.waitReason.ifBlank { tr("Waiting", "انتظار") }
            else -> tr("Preparing downloads", "ڈاؤن لوڈ تیار ہو رہی ہے")
        }
        val text = buildString {
            append(tr("${s.done} of ${s.total} done", "${s.done} از ${s.total} مکمل"))
            if (s.queued > 0) append(" · ").append(tr("${s.queued} waiting", "${s.queued} قطار میں"))
            if (s.speed.isNotBlank()) append(" · ").append(s.speed)
        }
        val b = base(ctx, CH_PROGRESS)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx, null))
            .addAction(0, tr("Pause all", "سب روکیں"), action(ctx, NotifActionReceiver.ACTION_PAUSE_ALL))
            .addAction(0, tr("Cancel all", "سب منسوخ"), action(ctx, NotifActionReceiver.ACTION_CANCEL_ALL))
        if (s.downloading > 0) b.setProgress(100, (s.overall * 100).toInt().coerceIn(0, 100), false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    fun postQueue(ctx: Context, s: QueueSnapshot) = post(ctx, ID_QUEUE, queueNotification(ctx, s))

    // ------------------------------------------------------------ per-video progress
    fun progress(ctx: Context, item: DownloadItem) {
        if (!Settings.value.nProgress) return
        val pct = (item.progress * 100).toInt().coerceIn(0, 100)
        val text = buildString {
            append("$pct%")
            if (item.speedText.isNotBlank()) append(" · ").append(item.speedText)
            if (item.etaSec >= 0) append(" · ETA ").append(Utils.fmtEta(item.etaSec))
        }
        val b = base(ctx, CH_PROGRESS)
            .setContentTitle(item.displayTitle)
            .setContentText(text)
            .setProgress(100, pct, pct <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx, null))
            .addAction(0, tr("Pause", "روکیں"), action(ctx, NotifActionReceiver.ACTION_PAUSE, item.id))
            .addAction(0, tr("Cancel", "منسوخ"), action(ctx, NotifActionReceiver.ACTION_CANCEL, item.id))
        post(ctx, nid(BASE_PROGRESS, item.id), b.build())
    }

    fun clearProgress(ctx: Context, id: String) = cancel(ctx, nid(BASE_PROGRESS, id))

    // ------------------------------------------------------------ results
    fun done(ctx: Context, item: DownloadItem) {
        val p = Settings.value
        if (!p.nDone) return
        val canPlay = item.filePath.isNotBlank() && File(item.filePath).exists()
        val b = alertDefaults(base(ctx, chan("done", p)), p)
            .setContentTitle(tr("Download complete", "ڈاؤن لوڈ مکمل"))
            .setContentText(item.displayTitle)
            .setAutoCancel(true)
            .setContentIntent(if (canPlay) play(ctx, item.filePath) else openApp(ctx, "history"))
        if (canPlay) b.setSubText(tr("Tap to play", "چلانے کے لیے ٹیپ کریں"))
        post(ctx, nid(BASE_DONE, item.id), b.build())
    }

    fun failed(ctx: Context, item: DownloadItem) {
        val p = Settings.value
        if (!p.nFailed) return
        val b = alertDefaults(base(ctx, chan("failed", p)), p)
            .setContentTitle(tr("Download failed", "ڈاؤن لوڈ ناکام"))
            .setContentText(item.displayTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.displayTitle + "\n" + item.error))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "queue"))
            .addAction(0, tr("Retry", "دوبارہ"), action(ctx, NotifActionReceiver.ACTION_RETRY, item.id))
        post(ctx, nid(BASE_FAILED, item.id), b.build())
    }

    fun summary(ctx: Context, done: Int, failed: Int) {
        val p = Settings.value
        if (!p.nSummary) return
        val text = if (failed > 0)
            tr("$done downloaded, $failed failed", "$done ڈاؤن لوڈ ہوئیں، $failed ناکام")
        else tr("$done video(s) downloaded", "$done ویڈیو ڈاؤن لوڈ ہو گئیں")
        val b = alertDefaults(base(ctx, chan("alerts", p)), p)
            .setContentTitle(tr("All downloads finished", "تمام ڈاؤن لوڈز مکمل"))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "history"))
        post(ctx, ID_SUMMARY, b.build())
    }

    fun networkLost(ctx: Context, reason: String) {
        val p = Settings.value
        if (!p.nNetwork) return
        val b = alertDefaults(base(ctx, chan("alerts", p)), p)
            .setContentTitle(reason)
            .setContentText(tr("Downloads are waiting and will resume automatically", "ڈاؤن لوڈز انتظار میں ہیں، خود بخود دوبارہ شروع ہوں گی"))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, null))
        post(ctx, ID_NETWORK, b.build())
    }

    fun clearNetwork(ctx: Context) = cancel(ctx, ID_NETWORK)

    fun lowStorage(ctx: Context, freeBytes: Long) {
        val p = Settings.value
        if (!p.nStorage) return
        val b = alertDefaults(base(ctx, chan("alerts", p)), p)
            .setContentTitle(tr("Low storage", "اسٹوریج کم ہے"))
            .setContentText(tr("Only ${Utils.fmtBytes(freeBytes)} free", "صرف ${Utils.fmtBytes(freeBytes)} خالی ہے"))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "settings"))
        post(ctx, ID_STORAGE, b.build())
    }

    fun engineUpdate(ctx: Context, text: String) {
        val p = Settings.value
        if (!p.nEngine) return
        val b = alertDefaults(base(ctx, chan("engine", p)), p)
            .setContentTitle(tr("Engine update", "انجن اپڈیٹ"))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "settings"))
        post(ctx, ID_ENGINE, b.build())
    }

    fun clipboard(ctx: Context, url: String) {
        if (!Settings.value.nClipboard) return
        val b = base(ctx, CH_CLIPBOARD)
            .setContentTitle(tr("Link found in clipboard", "کلپ بورڈ میں لنک ملا"))
            .setContentText(url)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, null))
            .addAction(0, tr("Add to queue", "قطار میں ڈالیں"), action(ctx, NotifActionReceiver.ACTION_ADD_URL, url = url))
        post(ctx, ID_CLIPBOARD, b.build())
    }

    fun clearClipboard(ctx: Context) = cancel(ctx, ID_CLIPBOARD)

    fun clearItem(ctx: Context, id: String) {
        cancel(ctx, nid(BASE_PROGRESS, id))
        cancel(ctx, nid(BASE_FAILED, id))
    }
}
