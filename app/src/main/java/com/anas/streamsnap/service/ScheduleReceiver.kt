package com.anas.streamsnap.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.engine.QueueManager
import java.util.Calendar

/** Fires at the time chosen in Settings > Scheduled download and starts the held queue. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        QueueManager.releaseScheduled()
        arm(context) // next day
    }

    companion object {
        private fun pending(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 7001, Intent(ctx, ScheduleReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** (Re)arms the daily alarm, or cancels it when the schedule is off. */
        fun arm(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(ctx)
            am.cancel(pi)
            val p = Settings.value
            if (!p.scheduleEnabled) return
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, p.scheduleHour)
                set(Calendar.MINUTE, p.scheduleMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis() + 5000) add(Calendar.DAY_OF_YEAR, 1)
            }
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
            } catch (_: Throwable) { }
        }
    }
}
