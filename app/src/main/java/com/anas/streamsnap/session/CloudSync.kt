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
    private val seen = HashMap<String, String>()
    private var primed = false

    fun start() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            QueueManager.items.collect { list ->
                try { handle(list) } catch (_: Throwable) { }
            }
        }
    }

    @Synchronized
    private fun handle(list: List<com.anas.streamsnap.data.DownloadItem>) {
        if (!primed) {
            // The first list is what was saved on the phone earlier: remember it, do not send it again.
            list.forEach { seen[it.id] = keyOf(it.status) }
            if (ProfileStore.totalDownloads() == 0L) {
                ProfileStore.setTotalDownloads(list.count { it.status == Status.DONE }.toLong())
            }
            primed = true
            return
        }
        val uid = Cloud.uid()
        var newlyDone = 0
        for (it in list) {
            val key = keyOf(it.status)
            val prev = seen[it.id]
            if (prev == key) continue
            seen[it.id] = key
            if (key == "done") newlyDone++
            if (uid == null || !Session.consented.value) continue
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
            if (uid != null) Cloud.updateUserFields(uid, mapOf("totalDownloads" to total))
        }
    }

    private fun keyOf(s: Status): String = when (s) {
        Status.DONE -> "done"
        Status.FAILED -> "failed"
        else -> "added"
    }
}
