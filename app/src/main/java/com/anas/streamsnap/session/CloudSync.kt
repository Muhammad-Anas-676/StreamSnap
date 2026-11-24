package com.anas.streamsnap.session

import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.LinkEvent
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches the download list and records, for the admin, each link that was added and how it ended.
 * Only the link, its site, time, quality and result are sent (never cookies, pages or file contents).
 * Also keeps the "Total downloads" counter of the profile.
 */
object CloudSync {
    /** What was already sent to the cloud, per item. Only filled when an item was really handed to the cloud. */
    private val seen = HashMap<String, String>()
    /** Items already counted in "Total downloads" (separate from [seen], which waits for sign-in and consent). */
    private val counted = HashSet<String>()
    private var primed = false
    private var lastDownloading = false

    fun start() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            QueueManager.items.collect { list ->
                try { handle(list) } catch (_: Throwable) { }
            }
        }
    }

    /** Re-checks the current list, e.g. right after sign-in / consent, so items added before that are sent now. */
    fun flush() {
        try { handle(QueueManager.items.value) } catch (_: Throwable) { }
    }

    @Synchronized
    private fun handle(list: List<com.anas.streamsnap.data.DownloadItem>) {
        if (!primed) {
            // The first list is what was saved on the phone earlier: remember it, do not send it again.
            list.forEach {
                seen[it.id] = keyOf(it.status)
                if (it.status == Status.DONE) counted.add(it.id)
            }
            if (ProfileStore.totalDownloads() == 0L) {
                ProfileStore.setTotalDownloads(list.count { it.status == Status.DONE }.toLong())
            }
            primed = true
            return
        }
        val downloading = list.any { it.status == Status.DOWNLOADING }
        if (downloading != lastDownloading) {
            lastDownloading = downloading
            Session.refreshPresence() // "Downloading Media" on / off in the live status
        }
        val uid = Cloud.uid()
        // Nothing is sent (and nothing is marked as sent) while signed out or before the documents were accepted;
        // the items are picked up by the next call after sign-in / consent.
        val canSend = uid != null && Session.consented.value
        var newlyDone = 0
        for (it in list) {
            val key = keyOf(it.status)
            if (key == "done" && counted.add(it.id)) newlyDone++
            if (!canSend || uid == null) continue
            if (seen[it.id] == key) continue
            seen[it.id] = key
            Cloud.logLink(
                LinkEvent(
                    id = uid + "_" + it.id, uid = uid, url = it.url,
                    site = it.site.ifBlank { Utils.siteName(it.url) },
                    status = key, quality = it.quality, addedAt = it.addedAt,
                    finishedAt = if (key == "added") 0 else System.currentTimeMillis(),
                    error = if (key == "failed") it.error else "",
                ),
            )
        }
        if (newlyDone > 0) {
            val total = ProfileStore.totalDownloads() + newlyDone
            ProfileStore.setTotalDownloads(total)
            // Pushed only after the cloud profile was merged: a fresh install must never overwrite the cloud counter with a small number.
            if (uid != null && Session.profileMerged(uid)) Cloud.updateUserFields(uid, mapOf("totalDownloads" to total))
        }
    }

    private fun keyOf(s: Status): String = when (s) {
        Status.DONE -> "done"
        Status.FAILED -> "failed"
        else -> "added"
    }
}
