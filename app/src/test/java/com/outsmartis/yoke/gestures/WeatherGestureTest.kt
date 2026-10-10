package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherGestureTest {

    @Test
    fun tapWeatherIsUnboundByDefault() {
        assertEquals(GestureAction.None, GestureDefaults.create()[Trigger.TAP_WEATHER])
        assertFalse(GestureDefaults.create().isBound(Trigger.TAP_WEATHER))
        assertEquals("Tap weather", Trigger.TAP_WEATHER.label)
    }

    @Test
    fun tapWeatherRoundTripsThroughJson() {
        val app = GestureAction.OpenApp("com.example.weather", "com.example.weather.Main", "UserHandle{0}")
        val config = GestureDefaults.create().with(Trigger.TAP_WEATHER, app)
        val json = config.toJson()
        assertTrue(json.contains("TAP_WEATHER"))
        assertEquals(app, (GestureConfig.parse(json) as GestureParse.Ok).config[Trigger.TAP_WEATHER])
    }

    @Test
    fun oldJsonWithoutTapWeatherStillImports() {
        val old = """{"version":1,"gestures":{"TAP_CLOCK":{"type":"app_drawer"},"TAP_DATE":{"type":"none"}}}"""
        val parsed = GestureConfig.parse(old) as GestureParse.Ok
        assertEquals(GestureAction.AppDrawer, parsed.config[Trigger.TAP_CLOCK])
        assertEquals(GestureAction.None, parsed.config[Trigger.TAP_WEATHER])
    }
}
