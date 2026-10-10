package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitTodayGestureTest {

    @Test
    fun roundTripsAndIsUnboundByDefault() {
        val a = GestureAction.CockpitToday
        assertEquals(a, GestureAction.fromJson(a.toJson()))
        assertTrue(a in GestureAction.simple)
        val cfg = GestureDefaults.create().with(Trigger.SWIPE_UP, a)
        assertEquals(a, (GestureConfig.parse(cfg.toJson()) as GestureParse.Ok).config[Trigger.SWIPE_UP])
        assertFalse(GestureDefaults.create().bound().any { it.second == a })
    }

    @Test
    fun oldJsonStillImports() {
        val old = """{"version":1,"gestures":{"SWIPE_LEFT":{"type":"cockpit_quick_add"}}}"""
        assertTrue(GestureConfig.parse(old) is GestureParse.Ok)
    }
}
