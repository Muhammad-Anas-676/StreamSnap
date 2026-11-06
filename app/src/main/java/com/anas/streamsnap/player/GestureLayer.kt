package com.anas.streamsnap.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

/**
 * Transparent view on top of the video. Turns touches into player gestures:
 * tap, double tap (left / middle / right), long press (hold for 2x), horizontal drag (seek),
 * vertical drag (left side = brightness, right side = volume) and pinch (zoom).
 */
class GestureLayer(ctx: Context) : View(ctx) {
    var locked = false

    var cbSingleTap: () -> Unit = {}
    /** zone: -1 left, 0 middle, 1 right */
    var cbDoubleTap: (Int) -> Unit = {}
    var cbLongPress: (Boolean) -> Unit = {}
    var cbSeekStart: () -> Unit = {}
    /** Signed fraction of the view width dragged since the gesture began. */
    var cbSeekDrag: (Float) -> Unit = {}
    var cbSeekEnd: (Boolean) -> Unit = {}
    /** left = true for the left half of the screen. delta > 0 means the finger moved up. */
    var cbVertical: (Boolean, Float) -> Unit = { _, _ -> }
    var cbScale: (Float) -> Unit = {}

    private val edge = 24f * ctx.resources.displayMetrics.density
    private var mode = 0 // 0 undecided, 1 horizontal, 2 vertical, 3 ignored
    private var startLeft = true
    private var totalX = 0f
    private var longPressing = false
    private var scaling = false

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            mode = 0
            totalX = 0f
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            cbSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (locked) return true
            val third = width / 3f
            cbDoubleTap(
                when {
                    e.x < third -> -1
                    e.x > 2 * third -> 1
                    else -> 0
                }
            )
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (locked || mode != 0 || scaling) return
            longPressing = true
            cbLongPress(true)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (locked || longPressing || scaling) return true
            if (mode == 0) {
                val sx = e1?.x ?: e2.x
                val sy = e1?.y ?: e2.y
                // Leave the screen edges to the system (back gesture, status bar).
                if (sx < edge || sx > width - edge || sy < edge) {
                    mode = 3
                    return true
                }
                mode = if (abs(distanceX) > abs(distanceY)) 1 else 2
                startLeft = sx < width / 2f
                if (mode == 1) cbSeekStart()
            }
            when (mode) {
                1 -> {
                    totalX += -distanceX
                    cbSeekDrag(totalX / width.coerceAtLeast(1))
                }
                2 -> cbVertical(startLeft, distanceY / height.coerceAtLeast(1))
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            return !locked
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cbScale(detector.scaleFactor)
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!locked) scaleDetector.onTouchEvent(e)
        if (!scaleDetector.isInProgress && !scaling) detector.onTouchEvent(e)

        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (longPressing) {
                longPressing = false
                cbLongPress(false)
            }
            if (mode == 1) cbSeekEnd(e.actionMasked == MotionEvent.ACTION_UP)
            mode = 0
            scaling = false
        }
        return true
    }
}
