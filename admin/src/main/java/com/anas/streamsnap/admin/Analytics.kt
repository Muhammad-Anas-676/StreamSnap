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
