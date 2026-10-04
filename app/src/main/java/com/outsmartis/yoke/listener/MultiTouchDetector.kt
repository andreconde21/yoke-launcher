package com.outsmartis.yoke.listener

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.outsmartis.yoke.gestures.GestureGeometry
import com.outsmartis.yoke.gestures.Trigger

/**
 * Two-finger swipes and pinch. Fed every touch event of a view; reports one trigger when the first
 * of the two fingers lifts. A pinch (span changed enough) wins over a two-finger swipe.
 * While [active] the single-finger detector must be ignored for the rest of the touch sequence.
 */
internal class MultiTouchDetector(context: Context, private val onTrigger: (Trigger) -> Unit) {

    var active = false
        private set

    private var handled = false
    private var startX = 0f
    private var startY = 0f
    private var initialSpan = 0f
    private var spanRatio = 1f
    private val swipeThresholdPx = GestureGeometry.TWO_FINGER_DISTANCE_DP * context.resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            initialSpan = detector.currentSpan
            spanRatio = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (initialSpan > 0f) spanRatio = detector.currentSpan / initialSpan
            return true
        }
    })

    /** Returns true while a multi-touch sequence owns the touch. */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            active = false
            handled = false
            spanRatio = 1f
            initialSpan = 0f
        }
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (!active && ev.pointerCount == 2) {
                active = true
                handled = false
                startX = (ev.getX(0) + ev.getX(1)) / 2f
                startY = (ev.getY(0) + ev.getY(1)) / 2f
            }

            MotionEvent.ACTION_POINTER_UP -> if (active && !handled && ev.pointerCount == 2) {
                handled = true
                val dx = (ev.getX(0) + ev.getX(1)) / 2f - startX
                val dy = (ev.getY(0) + ev.getY(1)) / 2f - startY
                val trigger = GestureGeometry.classifyPinch(spanRatio)
                    ?: GestureGeometry.classifyTwoFinger(dx, dy, swipeThresholdPx)
                if (trigger != null) onTrigger(trigger)
            }
        }
        return active
    }
}
