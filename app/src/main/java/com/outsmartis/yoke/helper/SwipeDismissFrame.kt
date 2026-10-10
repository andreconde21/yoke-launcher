package com.outsmartis.yoke.helper

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A bottom sheet's container that follows a downward drag and closes the sheet when dragged
 * far or flung down. The drag only starts while [scrollTarget] (if any) is scrolled to the top,
 * so scrolling a long sheet back up still works; taps and drags on buttons and fields inside
 * behave as before until the finger has clearly moved down.
 */
class SwipeDismissFrame(context: Context) : FrameLayout(context) {

    var scrollTarget: View? = null
    var onDismiss: () -> Unit = {}

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val flingVelocity = config.scaledMinimumFlingVelocity * 8f
    private var startX = 0f
    private var startY = 0f
    private var armed = false
    private var dragging = false
    private var closing = false
    private var tracker: VelocityTracker? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (closing) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                dragging = false
                armed = scrollTarget?.canScrollVertically(-1) != true
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                animate().cancel()
            }
        }
        // Raw coordinates: the view itself moves while dragged, so local ones would jitter.
        tracker?.let { t ->
            val raw = MotionEvent.obtain(ev)
            raw.setLocation(ev.rawX, ev.rawY)
            t.addMovement(raw)
            raw.recycle()
        }
        if (!dragging && armed && ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val dy = ev.rawY - startY
            if (dy > slop && dy > abs(ev.rawX - startX) && scrollTarget?.canScrollVertically(-1) != true) {
                dragging = true
                startY = ev.rawY
                // The children stop tracking this gesture (no stray click or scroll).
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        if (!dragging) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) release()
            return super.dispatchTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> translationY = (ev.rawY - startY).coerceAtLeast(0f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val velocity = tracker?.run { computeCurrentVelocity(1000); yVelocity } ?: 0f
                dragging = false
                release()
                if (SwipeDismiss.shouldDismiss(translationY, height.toFloat(), velocity, flingVelocity)) close()
                else animate().translationY(0f).setDuration(150).start()
            }
        }
        return true
    }

    private fun release() {
        tracker?.recycle()
        tracker = null
    }

    private fun close() {
        closing = true
        animate().translationY(height.toFloat().coerceAtLeast(1f)).setDuration(150)
            .withEndAction { onDismiss() }.start()
    }
}

object SwipeDismiss {
    /** Closes past a quarter of the sheet's height, or on a clear downward fling (an upward one keeps it). */
    fun shouldDismiss(dragged: Float, height: Float, velocityY: Float, flingVelocity: Float): Boolean {
        if (velocityY < -flingVelocity) return false
        return velocityY > flingVelocity || (height > 0f && dragged > height / 4f)
    }
}

/**
 * Puts [scroll] in a [SwipeDismissFrame] as this dialog's content: the frame carries the rounded
 * sheet background and a small grab handle, so the whole sheet moves with the finger.
 */
fun android.app.Dialog.setSwipeDismissContent(scroll: View, surfaceColor: Int, handleColor: Int) {
    val density = context.resources.displayMetrics.density
    val r = 24 * density
    val surface = android.graphics.drawable.GradientDrawable().apply {
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        setColor(surfaceColor)
    }
    val handle = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = 2 * density
        setColor(handleColor)
        setSize((32 * density).toInt(), (4 * density).toInt())
    }
    val frame = SwipeDismissFrame(context).apply {
        background = android.graphics.drawable.LayerDrawable(arrayOf(surface, handle)).apply {
            setLayerGravity(1, android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
            setLayerInsetTop(1, (8 * density).toInt())
        }
        scrollTarget = scroll
        onDismiss = { dismiss() }
        addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }
    setContentView(frame)
    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
}
