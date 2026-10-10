package com.outsmartis.yoke.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDismissTest {
    private val fling = 400f

    @Test fun shortSlowDragSnapsBack() = assertFalse(SwipeDismiss.shouldDismiss(50f, 800f, 0f, fling))
    @Test fun dragPastAQuarterCloses() = assertTrue(SwipeDismiss.shouldDismiss(250f, 800f, 0f, fling))
    @Test fun downwardFlingClosesEvenWhenShort() = assertTrue(SwipeDismiss.shouldDismiss(30f, 800f, 1500f, fling))
    @Test fun upwardFlingKeepsItOpenEvenWhenFar() = assertFalse(SwipeDismiss.shouldDismiss(500f, 800f, -1500f, fling))
    @Test fun unmeasuredSheetNeedsAFling() = assertFalse(SwipeDismiss.shouldDismiss(100f, 0f, 0f, fling))
}
