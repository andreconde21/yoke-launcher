package com.outsmartis.yoke.gestures

import kotlin.math.abs

/** Pure classification of raw touch data into triggers, so it can be unit tested. */
object GestureGeometry {

    const val SWIPE_VELOCITY = 100f
    const val EDGE_DP = 24f
    const val TWO_FINGER_DISTANCE_DP = 64f
    const val PINCH_IN_RATIO = 0.75f
    const val PINCH_OUT_RATIO = 1.33f

    /**
     * Single-finger fling. A mostly vertical swipe that starts within [edgePx] of the left or right
     * screen edge is an edge-along swipe; everything else maps to the four plain swipes.
     */
    fun classifyFling(
        startX: Float, startY: Float, endX: Float, endY: Float,
        velocityX: Float, velocityY: Float,
        screenWidthPx: Float, edgePx: Float, thresholdPx: Float,
    ): Trigger? {
        val dx = endX - startX
        val dy = endY - startY
        if (abs(dx) > abs(dy)) {
            if (abs(dx) > thresholdPx && abs(velocityX) > SWIPE_VELOCITY)
                return if (dx > 0) Trigger.SWIPE_RIGHT else Trigger.SWIPE_LEFT
        } else if (abs(dy) > thresholdPx && abs(velocityY) > SWIPE_VELOCITY) {
            val up = dy < 0
            return when {
                startX <= edgePx -> if (up) Trigger.EDGE_LEFT_SWIPE_UP else Trigger.EDGE_LEFT_SWIPE_DOWN
                startX >= screenWidthPx - edgePx -> if (up) Trigger.EDGE_RIGHT_SWIPE_UP else Trigger.EDGE_RIGHT_SWIPE_DOWN
                else -> if (up) Trigger.SWIPE_UP else Trigger.SWIPE_DOWN
            }
        }
        return null
    }

    /** Movement of the two-finger centroid between touch-down and the first finger lifting. */
    fun classifyTwoFinger(dx: Float, dy: Float, thresholdPx: Float): Trigger? {
        if (abs(dx) > abs(dy)) {
            if (abs(dx) > thresholdPx) return if (dx > 0) Trigger.TWO_FINGER_SWIPE_RIGHT else Trigger.TWO_FINGER_SWIPE_LEFT
        } else if (abs(dy) > thresholdPx) {
            return if (dy < 0) Trigger.TWO_FINGER_SWIPE_UP else Trigger.TWO_FINGER_SWIPE_DOWN
        }
        return null
    }

    /** [ratio] is final finger span divided by initial span. */
    fun classifyPinch(ratio: Float): Trigger? = when {
        ratio <= PINCH_IN_RATIO -> Trigger.PINCH_IN
        ratio >= PINCH_OUT_RATIO -> Trigger.PINCH_OUT
        else -> null
    }

    /** Edge swipes fall back to the plain swipe when the edge trigger is not bound. */
    fun plainSwipeFor(trigger: Trigger): Trigger? = when (trigger) {
        Trigger.EDGE_LEFT_SWIPE_UP, Trigger.EDGE_RIGHT_SWIPE_UP -> Trigger.SWIPE_UP
        Trigger.EDGE_LEFT_SWIPE_DOWN, Trigger.EDGE_RIGHT_SWIPE_DOWN -> Trigger.SWIPE_DOWN
        else -> null
    }
}
