package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRowGestureTest {

    @Test
    fun toggleIconRowIsUnboundByDefaultAndListed() {
        assertTrue(GestureAction.simple.contains(GestureAction.ToggleIconRow))
        assertEquals("toggle_icon_row", GestureAction.ToggleIconRow.type)
        Trigger.values().forEach { assertFalse(GestureDefaults.create()[it] == GestureAction.ToggleIconRow) }
    }

    @Test
    fun toggleIconRowRoundTripsThroughJson() {
        val config = GestureDefaults.create().with(Trigger.SWIPE_LEFT, GestureAction.ToggleIconRow)
        val back = (GestureConfig.parse(config.toJson()) as GestureParse.Ok).config
        assertEquals(GestureAction.ToggleIconRow, back[Trigger.SWIPE_LEFT])
    }

    @Test
    fun oldJsonWithoutItStillImports() {
        val old = """{"version":1,"gestures":{"TAP_CLOCK":{"type":"app_drawer"}}}"""
        val parsed = GestureConfig.parse(old) as GestureParse.Ok
        assertEquals(GestureAction.AppDrawer, parsed.config[Trigger.TAP_CLOCK])
    }
}
