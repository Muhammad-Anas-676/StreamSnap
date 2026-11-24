package com.anas.streamsnap.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.session.Session
import java.util.concurrent.TimeUnit

/**
 * Fallback for phones where FCM is restricted (battery savers of some brands): every 15 minutes, while an
 * account is signed in, it looks for admin messages that were not shown yet and posts them.
 */
class NotifCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val uid = Cloud.uid() ?: return Result.success()
            if (!Session.consented.value) return Result.success()
            Cloud.fetchPendingNotifications(uid).forEach {
                if (PushDedupe.markIfNew(applicationContext, it.id)) {
                    Notifier.adminMessage(applicationContext, it.id, it.title, it.message)
                }
            }
        } catch (_: Throwable) { }
        return Result.success()
    }

    companion object {
        private const val NAME = "admin_notice_check"

        fun schedule(ctx: Context) {
            try {
                val req = PeriodicWorkRequestBuilder<NotifCheckWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } catch (_: Throwable) { }
        }

        fun cancel(ctx: Context) {
            try { WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME) } catch (_: Throwable) { }
        }
    }
}
