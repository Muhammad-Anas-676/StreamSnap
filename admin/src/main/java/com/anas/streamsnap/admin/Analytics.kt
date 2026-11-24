package com.anas.streamsnap.admin

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DAY_MS = 86_400_000L

object Fmt {
    private val dt = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
    private val d = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val short = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun dateTime(ms: Long): String = if (ms <= 0) "-" else dt.format(Date(ms))
    fun date(ms: Long): String = if (ms <= 0) "-" else d.format(Date(ms))
    fun shortDate(ms: Long): String = short.format(Date(ms))

    /** 3725000 ms -> "1h 02m 05s" */
    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "%dh %02dm %02ds".format(h, m, sec)
            m > 0 -> "%dm %02ds".format(m, sec)
            else -> "%ds".format(sec)
        }
    }

    private fun plural(n: Long, unit: String) = "$n $unit" + if (n == 1L) "" else "s"

    /** Live status text: "Online now · Browsing Web" or "Active 5 minutes ago" / "Offline for 3 days". */
    fun presence(u: com.anas.streamsnap.core.cloud.CloudUser, now: Long): Pair<Boolean, String> {
        if (u.onlineNow(now)) return true to ("Online now · " + u.currentActivity.ifBlank { "Using the app" })
        if (u.lastActive <= 0) return false to "Offline · not seen yet"
        val diff = (now - u.lastActive).coerceAtLeast(0)
        return false to when {
            diff < 60_000 -> "Active just now"
            diff < 3_600_000 -> "Active " + plural(diff / 60_000, "minute") + " ago"
            diff < DAY_MS -> "Active " + plural(diff / 3_600_000, "hour") + " ago"
            else -> "Offline for " + plural(diff / DAY_MS, "day")
        }
    }

    fun ago(ms: Long): String {
        if (ms <= 0) return "never"
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < DAY_MS -> "${diff / 3_600_000} h ago"
            diff < 30 * DAY_MS -> "${diff / DAY_MS} d ago"
            else -> date(ms)
        }
    }
}

/** Start of the local day for [ms]. */
fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** The last [n] local days, oldest first (today is the last one). */
fun lastDays(n: Int): List<Long> {
    val today = dayStart(System.currentTimeMillis())
    val c = Calendar.getInstance()
    return (n - 1 downTo 0).map { back ->
        c.timeInMillis = today
        c.add(Calendar.DAY_OF_YEAR, -back)
        c.timeInMillis
    }
}

/** How many of [times] fall on each of [days]. */
fun countPerDay(times: List<Long>, days: List<Long>): List<Float> {
    val out = FloatArray(days.size)
    for (t in times) {
        if (t <= 0) continue
        val ds = dayStart(t)
        val i = days.indexOf(ds)
        if (i >= 0) out[i] += 1f
    }
    return out.toList()
}

fun hourOf(ms: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return c.get(Calendar.HOUR_OF_DAY)
}

fun <T> topCounts(items: List<T>, key: (T) -> String, max: Int): List<Pair<String, Float>> =
    items.groupBy(key).map { it.key to it.value.size.toFloat() }.sortedByDescending { it.second }.take(max)
