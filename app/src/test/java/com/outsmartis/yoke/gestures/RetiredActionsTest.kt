package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredActionsTest {
    @Test fun aSavedSetupWithARemovedActionStillLoads() {
        val json = """{"version":1,"gestures":{"DOUBLE_TAP":{"type":"lock"},"SWIPE_LEFT":{"type":"next_theme"},"SWIPE_RIGHT":{"type":"next_background"}}}"""
        val parsed = GestureConfig.parse(json)
        assertTrue(parsed is GestureParse.Ok)
        val config = (parsed as GestureParse.Ok).config
        assertEquals(GestureAction.Lock, config[Trigger.DOUBLE_TAP])
        assertEquals(GestureAction.None, config[Trigger.SWIPE_LEFT])
        assertEquals(GestureAction.None, config[Trigger.SWIPE_RIGHT])
    }

    @Test fun removedActionsAreNotOffered() =
        assertTrue(GestureAction.simple.none { it.type in GestureAction.RETIRED })
}
