package com.anas.streamsnap.core.cloud

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Measures how long the user stays on each screen.
 *  - [enter] is called when a screen becomes visible.
 *  - [pause] is called when the app goes to the background: the timer stops and the visit is saved.
 *  - [resume] is called when the app comes back: a new visit of the same screen starts.
 * Visits shorter than one second are ignored. They are sent in small batches (Spark-plan friendly).
 */
object ScreenTracker {
    private val lock = Any()
    private var screen: String? = null
    private var running = false
    private var startWall = 0L
    private var startElapsed = 0L
    private val pending = ArrayList<ScreenEvent>()
    private var loopStarted = false

    fun enter(name: String) {
        synchronized(lock) {
            if (screen == name && running) return
            closeLocked()
            screen = name
            beginLocked()
        }
        startLoop()
    }

    fun pause() {
        synchronized(lock) { closeLocked() }
        flush()
    }

    fun resume() {
        synchronized(lock) {
            if (screen != null && !running) beginLocked()
        }
    }

    /** Called on logout: drops the current visit without sending it. */
    fun reset() {
        synchronized(lock) {
            running = false
            screen = null
            pending.clear()
        }
    }

    fun flush() {
        val out: List<ScreenEvent>
        synchronized(lock) {
            if (pending.isEmpty()) return
            out = ArrayList(pending)
            pending.clear()
        }
        Cloud.logScreens(out)
    }

    private fun beginLocked() {
        running = true
        startWall = System.currentTimeMillis()
        startElapsed = SystemClock.elapsedRealtime()
    }

    private fun closeLocked() {
        if (!running) return
        running = false
        val name = screen ?: return
        val dur = SystemClock.elapsedRealtime() - startElapsed
        val uid = Cloud.uid() ?: return
        if (dur >= 1000) pending.add(ScreenEvent(uid = uid, screen = name, startAt = startWall, durationMs = dur))
    }

    private fun startLoop() {
        if (loopStarted) return
        loopStarted = true
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (true) {
                delay(60_000)
                // Save the visit that is running right now, then start a fresh one on the same screen.
                synchronized(lock) {
                    if (running) {
                        val name = screen
                        closeLocked()
                        screen = name
                        beginLocked()
                    }
                }
                flush()
            }
        }
    }
}
