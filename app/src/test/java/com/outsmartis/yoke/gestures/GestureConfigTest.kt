package com.outsmartis.yoke.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureConfigTest {

    private val app = GestureAction.OpenApp("com.example.cam", "com.example.cam.Main", "UserHandle{0}")
    private val shortcut = GestureAction.OpenShortcut("com.example.chat", "new_chat", "UserHandle{0}")

    private fun legacy(
        left: GestureAction? = null, leftOn: Boolean = true,
        right: GestureAction? = null, rightOn: Boolean = true,
        lock: Boolean = true,
        clock: GestureAction.OpenApp? = null, calendar: GestureAction.OpenApp? = null,
    ) = LegacyGestureState(left, leftOn, right, rightOn, lock, clock, calendar)

    @Test
    fun jsonRoundTripKeepsEveryActionType() {
        var config = GestureConfig(emptyMap())
        val actions = GestureAction.simple + listOf(app, shortcut, GestureAction.OpenUri("https://example.com/?a=b"))
        Trigger.entries.forEachIndexed { i, t -> config = config.with(t, actions[i % actions.size]) }
        val parsed = GestureConfig.parse(config.toJson())
        assertTrue(parsed is GestureParse.Ok)
        assertEquals(config, (parsed as GestureParse.Ok).config)
    }

    @Test
    fun openAppWithoutActivityRoundTrips() {
        val config = GestureConfig(emptyMap()).with(Trigger.SWIPE_LEFT, GestureAction.OpenApp("a.b", null, "u"))
        val back = (GestureConfig.parse(config.toJson()) as GestureParse.Ok).config
        assertEquals(GestureAction.OpenApp("a.b", null, "u"), back[Trigger.SWIPE_LEFT])
    }

    @Test
    fun defaultsMatchTheSpec() {
        val d = GestureDefaults.create()
        assertEquals(GestureAction.AppDrawer, d[Trigger.SWIPE_UP])
        assertEquals(GestureAction.Notifications, d[Trigger.SWIPE_DOWN])
        assertEquals(GestureAction.QuickSettings, d[Trigger.TWO_FINGER_SWIPE_DOWN])
        assertEquals(GestureAction.CockpitQuickAdd, d[Trigger.SWIPE_LEFT])
        assertEquals(GestureAction.ConductoreSheet, d[Trigger.SWIPE_RIGHT])
        assertEquals(GestureAction.Lock, d[Trigger.DOUBLE_TAP])
        assertEquals(GestureAction.CommandPalette, d[Trigger.LONG_PRESS_EMPTY])
        assertEquals(GestureAction.CommandPalette, d[Trigger.TWO_FINGER_SWIPE_UP])
        assertEquals(GestureAction.ThemePicker, d[Trigger.PINCH_IN])
        assertEquals(GestureAction.OpenUri(GestureDefaults.CLOCK_URI), d[Trigger.TAP_CLOCK])
        assertEquals(GestureAction.OpenUri(GestureDefaults.CALENDAR_URI), d[Trigger.TAP_DATE])
        val named = setOf(
            Trigger.SWIPE_UP, Trigger.SWIPE_DOWN, Trigger.TWO_FINGER_SWIPE_DOWN, Trigger.SWIPE_LEFT,
            Trigger.SWIPE_RIGHT, Trigger.DOUBLE_TAP, Trigger.LONG_PRESS_EMPTY, Trigger.TWO_FINGER_SWIPE_UP,
            Trigger.PINCH_IN, Trigger.TAP_CLOCK, Trigger.TAP_DATE,
        )
        Trigger.entries.filter { it !in named }.forEach { assertEquals(GestureAction.None, d[it]) }
    }

    @Test
    fun freshInstallUsesDefaults() {
        assertEquals(GestureDefaults.create(), GestureDefaults.migrate(null))
    }

    @Test
    fun migrationKeepsConfiguredSwipeApps() {
        val c = GestureDefaults.migrate(legacy(left = app, right = shortcut))
        assertEquals(app, c[Trigger.SWIPE_LEFT])
        assertEquals(shortcut, c[Trigger.SWIPE_RIGHT])
        assertEquals(GestureAction.AppDrawer, c[Trigger.SWIPE_UP])
    }

    @Test
    fun migrationWithUnconfiguredSwipesUsesNewDefaults() {
        val c = GestureDefaults.migrate(legacy())
        assertEquals(GestureAction.CockpitQuickAdd, c[Trigger.SWIPE_LEFT])
        assertEquals(GestureAction.ConductoreSheet, c[Trigger.SWIPE_RIGHT])
    }

    @Test
    fun migrationKeepsDisabledSwipesAndLockOff() {
        val c = GestureDefaults.migrate(legacy(left = app, leftOn = false, rightOn = false, lock = false))
        assertEquals(GestureAction.None, c[Trigger.SWIPE_LEFT])
        assertEquals(GestureAction.None, c[Trigger.SWIPE_RIGHT])
        assertEquals(GestureAction.None, c[Trigger.DOUBLE_TAP])
    }

    @Test
    fun migrationKeepsLockOnAndClockApp() {
        val clock = GestureAction.OpenApp("com.clock", null, "u")
        val c = GestureDefaults.migrate(legacy(lock = true, clock = clock))
        assertEquals(GestureAction.Lock, c[Trigger.DOUBLE_TAP])
        assertEquals(clock, c[Trigger.TAP_CLOCK])
        assertEquals(GestureAction.OpenUri(GestureDefaults.CALENDAR_URI), c[Trigger.TAP_DATE])
    }

    @Test
    fun settingsIsReachableByDefault() {
        // Long press opens the command palette, whose fallback is Settings
        assertEquals(GestureAction.CommandPalette, GestureDefaults.create()[Trigger.LONG_PRESS_EMPTY])
    }

    private fun assertError(text: String, contains: String) {
        val r = GestureConfig.parse(text)
        assertTrue("expected error for $text but got $r", r is GestureParse.Error)
        assertTrue((r as GestureParse.Error).message, r.message.contains(contains))
    }

    @Test
    fun importRejectsGarbage() {
        assertError("not json", "JSON")
        assertError("", "JSON")
        assertError("{}", "gestures")
        assertError("""{"gestures":{"SWIPE_UPP":{"type":"none"}}}""", "SWIPE_UPP")
        assertError("""{"gestures":{"SWIPE_UP":{"type":"fly"}}}""", "fly")
        assertError("""{"gestures":{"SWIPE_UP":"none"}}""", "SWIPE_UP")
        assertError("""{"gestures":{"SWIPE_UP":{"type":"open_app"}}}""", "package")
        assertError("""{"gestures":{"SWIPE_UP":{"type":"open_shortcut","package":"a"}}}""", "shortcutId")
        assertError("""{"gestures":{"SWIPE_UP":{"type":"open_uri","uri":"  "}}}""", "uri")
    }

    @Test
    fun importAppliesNothingWhenOneEntryIsBad() {
        val r = GestureConfig.parse("""{"gestures":{"SWIPE_UP":{"type":"torch"},"SWIPE_DOWN":{"type":"bogus"}}}""")
        assertTrue(r is GestureParse.Error)
    }

    @Test
    fun importLeavesMissingTriggersUnbound() {
        val r = GestureConfig.parse("""{"gestures":{"SWIPE_UP":{"type":"torch"}}}""") as GestureParse.Ok
        assertEquals(GestureAction.Torch, r.config[Trigger.SWIPE_UP])
        assertEquals(GestureAction.None, r.config[Trigger.SWIPE_DOWN])
        assertEquals(listOf(Trigger.SWIPE_UP to GestureAction.Torch), r.config.bound())
    }

    private val w = 1080f
    private val edge = 24f
    private fun fling(x0: Float, y0: Float, x1: Float, y1: Float) =
        GestureGeometry.classifyFling(x0, y0, x1, y1, (x1 - x0) * 5, (y1 - y0) * 5, w, edge, 100f)

    @Test
    fun flingClassification() {
        assertEquals(Trigger.SWIPE_UP, fling(500f, 1500f, 520f, 900f))
        assertEquals(Trigger.SWIPE_DOWN, fling(500f, 300f, 510f, 900f))
        assertEquals(Trigger.SWIPE_LEFT, fling(900f, 800f, 300f, 820f))
        assertEquals(Trigger.SWIPE_RIGHT, fling(300f, 800f, 900f, 820f))
        assertNull(fling(500f, 800f, 540f, 830f))
    }

    @Test
    fun edgeAlongSwipes() {
        assertEquals(Trigger.EDGE_LEFT_SWIPE_UP, fling(10f, 1500f, 30f, 900f))
        assertEquals(Trigger.EDGE_LEFT_SWIPE_DOWN, fling(10f, 500f, 30f, 1100f))
        assertEquals(Trigger.EDGE_RIGHT_SWIPE_UP, fling(1070f, 1500f, 1050f, 900f))
        assertEquals(Trigger.EDGE_RIGHT_SWIPE_DOWN, fling(1070f, 500f, 1050f, 1100f))
        // Inward from the edge stays a plain horizontal swipe, never an edge trigger
        assertEquals(Trigger.SWIPE_RIGHT, fling(10f, 800f, 600f, 820f))
        assertEquals(Trigger.SWIPE_LEFT, fling(1070f, 800f, 400f, 820f))
        assertEquals(Trigger.SWIPE_UP, GestureGeometry.plainSwipeFor(Trigger.EDGE_RIGHT_SWIPE_UP))
        assertNull(GestureGeometry.plainSwipeFor(Trigger.SWIPE_UP))
    }

    @Test
    fun twoFingerAndPinch() {
        assertEquals(Trigger.TWO_FINGER_SWIPE_UP, GestureGeometry.classifyTwoFinger(10f, -300f, 100f))
        assertEquals(Trigger.TWO_FINGER_SWIPE_DOWN, GestureGeometry.classifyTwoFinger(-10f, 300f, 100f))
        assertEquals(Trigger.TWO_FINGER_SWIPE_LEFT, GestureGeometry.classifyTwoFinger(-300f, 20f, 100f))
        assertEquals(Trigger.TWO_FINGER_SWIPE_RIGHT, GestureGeometry.classifyTwoFinger(300f, 20f, 100f))
        assertNull(GestureGeometry.classifyTwoFinger(20f, 30f, 100f))
        assertEquals(Trigger.PINCH_IN, GestureGeometry.classifyPinch(0.6f))
        assertEquals(Trigger.PINCH_OUT, GestureGeometry.classifyPinch(1.5f))
        assertNull(GestureGeometry.classifyPinch(1.05f))
    }

    @Test
    fun widgetPageActionRoundTripsAndIsUnboundByDefault() {
        val config = GestureConfig(emptyMap()).with(Trigger.SWIPE_RIGHT, GestureAction.WidgetPage)
        val parsed = GestureConfig.parse(config.toJson()) as GestureParse.Ok
        assertEquals(GestureAction.WidgetPage, parsed.config[Trigger.SWIPE_RIGHT])
        assertTrue(GestureAction.simple.contains(GestureAction.WidgetPage))
        assertTrue(GestureDefaults.create().actions.values.none { it == GestureAction.WidgetPage })
    }

    @Test
    fun olderJsonWithoutWidgetPageStillParses() {
        val old = """{"version":1,"gestures":{"SWIPE_UP":{"type":"app_drawer"}}}"""
        val parsed = GestureConfig.parse(old) as GestureParse.Ok
        assertEquals(GestureAction.AppDrawer, parsed.config[Trigger.SWIPE_UP])
    }

    @Test
    fun grayscaleActionsRoundTripAndAreUnboundByDefault() {
        val config = GestureConfig(emptyMap())
            .with(Trigger.SWIPE_LEFT, GestureAction.ToggleGrayscale)
            .with(Trigger.SWIPE_RIGHT, GestureAction.PauseGrayscale)
        val back = (GestureConfig.parse(config.toJson()) as GestureParse.Ok).config
        assertEquals(GestureAction.ToggleGrayscale, back[Trigger.SWIPE_LEFT])
        assertEquals(GestureAction.PauseGrayscale, back[Trigger.SWIPE_RIGHT])
        assertTrue(GestureAction.simple.containsAll(listOf(GestureAction.ToggleGrayscale, GestureAction.PauseGrayscale)))
        assertTrue(GestureDefaults.create().actions.values.none {
            it == GestureAction.ToggleGrayscale || it == GestureAction.PauseGrayscale
        })
    }

    @Test
    fun jsonWrittenBeforeGrayscaleStillImports() {
        val old = """{"version":1,"gestures":{"SWIPE_UP":{"type":"app_drawer"},"DOUBLE_TAP":{"type":"lock"},"PINCH_IN":{"type":"widget_page"}}}"""
        val parsed = GestureConfig.parse(old)
        assertTrue(parsed is GestureParse.Ok)
        assertEquals(GestureAction.Lock, (parsed as GestureParse.Ok).config[Trigger.DOUBLE_TAP])
    }
}
