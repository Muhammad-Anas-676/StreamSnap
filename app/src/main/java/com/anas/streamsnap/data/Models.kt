package com.anas.streamsnap.data

import org.json.JSONArray
import org.json.JSONObject

/** READY = info is loaded, waiting for the user (download icon in Manual mode, or a quality chip in per-video mode). */
enum class Status { FETCHING, READY, QUEUED, DOWNLOADING, PAUSED, WAITING, SCHEDULED, FAILED, DONE }

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String = "",
    val thumbnail: String = "",
    val durationSec: Long = 0,
    val site: String = "",
    val uploader: String = "",
    val status: Status = Status.FETCHING,
    val progress: Float = 0f,          // 0..1
    val speedText: String = "",
    val etaSec: Long = -1,
    val quality: String = "best",      // best | 1080 | 720 | 480 | 360 | audio
    val heights: List<Int> = emptyList(),
    val sizes: Map<Int, Long> = emptyMap(),   // video-only size per height (0 = unknown)
    val audioSize: Long = 0,                  // best audio-only size
    val sizeEstimate: Long = 0,
    val filePath: String = "",
    val fileSize: Long = 0,
    val error: String = "",
    val waitReason: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
) {
    val isAudio: Boolean get() = quality == "audio" || quality == "m4a"
    val displayTitle: String get() = title.ifBlank { url }
    val isActive: Boolean get() = status != Status.DONE

    /** Rough size for a quality choice (0 = unknown). */
    fun sizeFor(q: String): Long {
        if (q == "audio" || q == "m4a") return audioSize
        if (sizes.isEmpty()) return 0
        if (q == "smallest") {
            val v = sizes.values.filter { it > 0 }.minOrNull() ?: return 0
            return v + audioSize
        }
        val target = if (q == "best") Int.MAX_VALUE else (q.toIntOrNull() ?: Int.MAX_VALUE)
        val hs = sizes.keys.sorted()
        val h = hs.lastOrNull { it <= target } ?: hs.firstOrNull() ?: return 0
        val v = sizes[h] ?: 0L
        return if (v <= 0) 0 else v + audioSize
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("title", title); put("thumbnail", thumbnail)
        put("durationSec", durationSec); put("site", site); put("uploader", uploader)
        put("status", status.name); put("progress", progress.toDouble())
        put("quality", quality)
        put("heights", JSONArray(heights))
        put("sizes", JSONObject().also { o -> sizes.forEach { (h, s) -> o.put(h.toString(), s) } })
        put("audioSize", audioSize)
        put("sizeEstimate", sizeEstimate); put("filePath", filePath); put("fileSize", fileSize)
        put("error", error); put("waitReason", waitReason)
        put("addedAt", addedAt); put("finishedAt", finishedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem {
            val hs = ArrayList<Int>()
            o.optJSONArray("heights")?.let { a -> for (i in 0 until a.length()) hs.add(a.optInt(i)) }
            val szMap = HashMap<Int, Long>()
            o.optJSONObject("sizes")?.let { so ->
                val keys = so.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    k.toIntOrNull()?.let { h -> szMap[h] = so.optLong(k) }
                }
            }
            val st = try { Status.valueOf(o.optString("status", "QUEUED")) } catch (e: Exception) { Status.QUEUED }
            return DownloadItem(
                id = o.optString("id"),
                url = o.optString("url"),
                title = o.optString("title"),
                thumbnail = o.optString("thumbnail"),
                durationSec = o.optLong("durationSec"),
                site = o.optString("site"),
                uploader = o.optString("uploader"),
                status = st,
                progress = o.optDouble("progress", 0.0).toFloat(),
                quality = o.optString("quality", "best"),
                heights = hs,
                sizes = szMap,
                audioSize = o.optLong("audioSize"),
                sizeEstimate = o.optLong("sizeEstimate"),
                filePath = o.optString("filePath"),
                fileSize = o.optLong("fileSize"),
                error = o.optString("error"),
                waitReason = o.optString("waitReason"),
                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                finishedAt = o.optLong("finishedAt"),
            )
        }
    }
}

object Accents {
    // name, color (ARGB)
    val list: List<Pair<String, Long>> = listOf(
        "Ocean" to 0xFF1C68FA,
        "Sunset" to 0xFFFF7A2E,
        "Emerald" to 0xFF1FA971,
        "Violet" to 0xFF7C4DFF,
        "Rose" to 0xFFE91E63,
        "Teal" to 0xFF00A8A8,
        "Amber" to 0xFFF5A623,
        "Slate" to 0xFF5C6B7A,
    )
    const val DYNAMIC = 99
}

/** Every quality the user can choose (Settings list, and chips on each card in per-video mode). */
val QUALITY_OPTIONS: List<Pair<String, String>> = listOf(
    "best" to "Best",
    "2160" to "4K",
    "1440" to "1440p",
    "1080" to "1080p",
    "720" to "720p",
    "480" to "480p",
    "360" to "360p",
    "240" to "240p",
    "144" to "144p",
    "audio" to "MP3",
    "m4a" to "M4A",
    "smallest" to "Smallest",
)

/** Playlists / channels with more videos than this ask for confirmation before adding. */
const val PLAYLIST_CONFIRM_THRESHOLD = 20

fun qualityLabel(q: String): String = QUALITY_OPTIONS.firstOrNull { it.first == q }?.second ?: q

/** Chips to show on a card: only heights the video really has (when known). */
fun chipQualitiesFor(item: DownloadItem): List<String> {
    val maxH = item.heights.maxOrNull() ?: 0
    val video = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144).filter { b ->
        if (maxH <= 0) b in setOf(1080, 720, 480, 360) else b <= maxH || (b == 144 && maxH < 144)
    }.map { it.toString() }
    return listOf("best") + video + listOf("audio", "m4a", "smallest")
}
