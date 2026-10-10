package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitOpenGestureTest {

    @Test
    fun newActionsRoundTrip() {
        for (a in listOf(GestureAction.CockpitBoard, GestureAction.CockpitCalendar)) {
            assertEquals(a, GestureAction.fromJson(a.toJson()))
            assertTrue(a in GestureAction.simple)
        }
        val cfg = GestureDefaults.create().with(Trigger.SWIPE_UP, GestureAction.CockpitBoard)
            .with(Trigger.PINCH_IN, GestureAction.CockpitCalendar)
        val back = (GestureConfig.parse(cfg.toJson()) as GestureParse.Ok).config
        assertEquals(GestureAction.CockpitBoard, back[Trigger.SWIPE_UP])
        assertEquals(GestureAction.CockpitCalendar, back[Trigger.PINCH_IN])
    }

    @Test
    fun unboundByDefaultAndOldJsonStillImports() {
        val d = GestureDefaults.create()
        assertFalse(d.bound().any { it.second == GestureAction.CockpitBoard || it.second == GestureAction.CockpitCalendar })
        val old = """{"version":1,"gestures":{"SWIPE_LEFT":{"type":"cockpit_quick_add"},"PINCH_IN":{"type":"theme_picker"}}}"""
        val parsed = GestureConfig.parse(old)
        assertTrue(parsed is GestureParse.Ok)
        assertEquals(GestureAction.CockpitQuickAdd, (parsed as GestureParse.Ok).config[Trigger.SWIPE_LEFT])
    }

    @Test
    fun typeIdsAreDistinct() {
        assertEquals(GestureAction.simple.size, GestureAction.simple.map { it.type }.toSet().size)
    }
}
