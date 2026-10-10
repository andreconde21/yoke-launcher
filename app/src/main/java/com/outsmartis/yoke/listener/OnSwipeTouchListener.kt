package com.outsmartis.yoke.listener

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.gestures.GestureGeometry
import com.outsmartis.yoke.gestures.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
Swipe, edge swipe, two-finger swipe, pinch, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : OnTouchListener {
    private var longPressOn = false

    private val gestureListener = GestureListener()
    private val gestureDetector: GestureDetector
    private val multiTouch: MultiTouchDetector?
    private val screenWidthPx = (c?.resources?.displayMetrics?.widthPixels ?: 0).toFloat()
    private val edgePx = GestureGeometry.EDGE_DP * (c?.resources?.displayMetrics?.density ?: 1f)

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_UP)
            longPressOn = false
        if (multiTouch != null) {
            val wasActive = multiTouch.active
            if (multiTouch.onTouchEvent(motionEvent)) {
                if (!wasActive) {
                    longPressOn = false
                    val cancel = MotionEvent.obtain(motionEvent)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    gestureDetector.onTouchEvent(cancel)
                    cancel.recycle()
                }
                return true
            }
        }
        return gestureDetector.onTouchEvent(motionEvent)
    }

    /**
     * When no double tap is bound the detector is told not to look for one, so it does not
     * hold back its single-tap handling waiting for a second tap.
     */
    fun setDoubleTapEnabled(enabled: Boolean) {
        gestureDetector.setOnDoubleTapListener(if (enabled) gestureListener else null)
    }

    private inner class GestureListener : SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onGesture(Trigger.DOUBLE_TAP)
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            GlobalScope.launch {
                delay(Constants.LONG_PRESS_DELAY_MS)
                withContext(Dispatchers.Main) {
                    if (isActive && longPressOn)
                        onGesture(Trigger.LONG_PRESS_EMPTY)
                }
            }
            super.onLongPress(e)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            try {
                event1 ?: return false
                GestureGeometry.classifyFling(
                    event1.rawX, event1.rawY, event2.rawX, event2.rawY, velocityX, velocityY,
                    screenWidthPx, edgePx, SWIPE_THRESHOLD,
                )?.let { onGesture(it) }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return false
        }
    }

    open fun onGesture(trigger: Trigger) {}
    open fun onClick() {}

    private companion object {
        const val SWIPE_THRESHOLD = 100f
    }

    init {
        gestureDetector = GestureDetector(c, gestureListener)
        multiTouch = c?.let { MultiTouchDetector(it) { trigger -> onGesture(trigger) } }
    }
}
