package com.anas.streamsnap.util

import android.content.Context
import android.net.Uri
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.engine.QueueManager
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Settings + finished-download history as one small JSON file. PIN and cookies are never included. */
object BackupManager {
    private const val VERSION = 1

    fun backup(ctx: Context, uri: Uri): Result<String> = try {
        val root = JSONObject()
        root.put("app", "StreamSnap")
        root.put("version", VERSION)
        root.put("time", System.currentTimeMillis())
        val s = JSONObject()
        Settings.exportMap().forEach { (k, v) -> if (v != null) s.put(k, v) }
        root.put("settings", s)
        val arr = JSONArray()
        val done = QueueManager.items.value.filter { it.status == Status.DONE }
        done.forEach { arr.put(it.toJson()) }
        root.put("history", arr)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
            ?: throw IllegalStateException("Could not open the file for writing")
        LogStore.i("backup", "Backup saved (${done.size} history item(s))")
        Result.success("${done.size}")
    } catch (t: Throwable) {
        LogStore.e("backup", "Backup failed", t = t)
        Result.failure(t)
    }

    fun restore(ctx: Context, uri: Uri): Result<String> = try {
        val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
            ?: throw IllegalStateException("Could not open the file")
        val root = JSONObject(text)
        if (root.optString("app") != "StreamSnap") throw IllegalArgumentException("This is not a StreamSnap backup file")

        root.optJSONObject("settings")?.let { so ->
            val m = LinkedHashMap<String, Any?>()
            val keys = so.keys()
            while (keys.hasNext()) { val k = keys.next(); m[k] = so.get(k) }
            Settings.importMap(m)
        }
        var added = 0
        root.optJSONArray("history")?.let { arr ->
            val known = QueueManager.items.value.map { Utils.normalizeUrl(it.url) }.toHashSet()
            val fresh = ArrayList<DownloadItem>()
            for (i in 0 until arr.length()) {
                val it = DownloadItem.fromJson(arr.getJSONObject(i))
                if (it.id.isBlank() || it.status != Status.DONE) continue
                if (known.add(Utils.normalizeUrl(it.url))) fresh.add(it)
            }
            if (fresh.isNotEmpty()) QueueManager.items.update { l -> l + fresh }
            added = fresh.size
        }
        QueueManager.saveNow()
        LogStore.i("backup", "Backup restored ($added history item(s) added)")
        Result.success("$added")
    } catch (t: Throwable) {
        LogStore.e("backup", "Restore failed", t = t)
        Result.failure(t)
    }
}
