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

internal open class ViewSwipeTouchListener(c: Context?, v: View) : OnTouchListener {
    private var longPressOn = false
    private val gestureDetector: GestureDetector
    private val multiTouch: MultiTouchDetector?
    private val screenWidthPx = (c?.resources?.displayMetrics?.widthPixels ?: 0).toFloat()
    private val edgePx = GestureGeometry.EDGE_DP * (c?.resources?.displayMetrics?.density ?: 1f)

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> view.isPressed = true
            MotionEvent.ACTION_UP -> view.isPressed = false
        }
        if (multiTouch != null) {
            val wasActive = multiTouch.active
            if (multiTouch.onTouchEvent(motionEvent)) {
                if (!wasActive) {
                    view.isPressed = false
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

    private inner class GestureListener(private val view: View) : SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick(view)
            return super.onSingleTapUp(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            GlobalScope.launch {
                delay(Constants.LONG_PRESS_DELAY_MS)
                withContext(Dispatchers.Main) {
                    if (isActive && longPressOn)
                        onLongClick(view)
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
    open fun onLongClick(view: View) {}
    open fun onClick(view: View) {}

    private companion object {
        const val SWIPE_THRESHOLD = 100f
    }

    init {
        gestureDetector = GestureDetector(c, GestureListener(v))
        // Labels never use double tap; do not make the detector wait for one
        gestureDetector.setOnDoubleTapListener(null)
        multiTouch = c?.let { MultiTouchDetector(it) { trigger -> onGesture(trigger) } }
    }
}
