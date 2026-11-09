package com.anas.streamsnap.data

import android.content.Context
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedLink(val url: String, val title: String, val savedAt: Long)

/** Links the user kept "for later": no download, no page reading, just the link until they want it. */
object SavedLinks {
    private lateinit var file: File
    private var inited = false
    private val _items = MutableStateFlow<List<SavedLink>>(emptyList())
    val items: StateFlow<List<SavedLink>> = _items.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (inited) return
        inited = true
        file = File(ctx.applicationContext.filesDir, "saved_links.json")
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val list = ArrayList<SavedLink>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val u = o.optString("url")
                if (u.isNotBlank()) list.add(SavedLink(u, o.optString("title"), o.optLong("savedAt")))
            }
            _items.value = list
        } catch (_: Throwable) { }
    }

    /** Adds links (duplicates are skipped). Returns how many were really added. */
    @Synchronized
    fun add(urls: List<String>, title: String = ""): Int {
        val known = _items.value.map { Utils.normalizeUrl(it.url) }.toHashSet()
        val fresh = ArrayList<SavedLink>()
        for (u in urls) {
            if (!known.add(Utils.normalizeUrl(u))) continue
            fresh.add(SavedLink(u, if (urls.size == 1) title else "", System.currentTimeMillis()))
        }
        if (fresh.isEmpty()) return 0
        _items.value = _items.value + fresh
        save()
        return fresh.size
    }

    @Synchronized
    fun remove(urls: Collection<String>) {
        val set = urls.map { Utils.normalizeUrl(it) }.toHashSet()
        _items.value = _items.value.filter { Utils.normalizeUrl(it.url) !in set }
        save()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun save() {
        try {
            val arr = JSONArray()
            _items.value.forEach { x ->
                arr.put(JSONObject().put("url", x.url).put("title", x.title).put("savedAt", x.savedAt))
            }
            file.writeText(arr.toString())
        } catch (_: Throwable) { }
    }
}
