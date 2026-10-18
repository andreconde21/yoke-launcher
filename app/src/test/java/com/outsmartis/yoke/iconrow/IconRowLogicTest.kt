package com.outsmartis.yoke.iconrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRowLogicTest {

    private fun slot(n: Int) = IconSlot("com.example.app$n", if (n % 2 == 0) "com.example.app$n.Main" else null, "UserHandle{0}")

    @Test
    fun slotsRoundTripThroughJson() {
        val slots = (1..4).map(::slot)
        assertEquals(slots, IconSlots.decode(IconSlots.encode(slots)))
    }

    @Test
    fun decodeIsTolerantAndCapsAtSix() {
        assertEquals(emptyList<IconSlot>(), IconSlots.decode(null))
        assertEquals(emptyList<IconSlot>(), IconSlots.decode("not json"))
        assertEquals(emptyList<IconSlot>(), IconSlots.decode("""[{"user":"x"}]"""))
        assertEquals(6, IconSlots.decode("[" + (1..9).joinToString(",") { """{"package":"p$it"}""" } + "]").size)
        assertEquals(6, IconSlots.decode(IconSlots.encode((1..9).map(::slot))).size)
    }

    @Test
    fun moveSwapsNeighboursAndIgnoresEdges() {
        val s = (1..3).map(::slot)
        assertEquals(listOf(s[1], s[0], s[2]), IconSlots.move(s, 0, 1))
        assertEquals(listOf(s[0], s[2], s[1]), IconSlots.move(s, 2, -1))
        assertEquals(s, IconSlots.move(s, 0, -1))
        assertEquals(s, IconSlots.move(s, 2, 1))
    }

    @Test
    fun removeAndSet() {
        val s = (1..3).map(::slot)
        assertEquals(listOf(s[0], s[2]), IconSlots.remove(s, 1))
        assertEquals(s, IconSlots.remove(s, 5))
        assertEquals(listOf(s[0], slot(9), s[2]), IconSlots.set(s, 1, slot(9)))
        assertEquals(s + slot(9), IconSlots.set(s, 3, slot(9)))
        val full = (1..6).map(::slot)
        assertEquals(full, IconSlots.set(full, 6, slot(9)))
    }

    @Test
    fun unknownEnumIdsFallBackToDefaults() {
        assertEquals(IconStyle.MONOCHROME, IconStyle.fromId(null))
        assertEquals(IconSize.MEDIUM, IconSize.fromId("huge"))
        assertEquals(32, IconSize.MEDIUM.dp)
        assertEquals(IconPosition.BOTTOM, IconPosition.fromId("x"))
        assertEquals(IconPosition.UNDER_CLOCK, IconPosition.fromId("under_clock"))
    }

    @Test
    fun luminanceBecomesAlpha() {
        assertEquals(255, IconMask.alphaOf(0xFFFFFFFF.toInt()))
        assertEquals(0, IconMask.alphaOf(0xFF000000.toInt()))
        assertEquals(0, IconMask.alphaOf(0x00FFFFFF))
        assertEquals(0, IconMask.alphaOf(0xFFFFFFFF.toInt(), invert = true))
        assertEquals(255, IconMask.alphaOf(0xFF000000.toInt(), invert = true))
        assertEquals(128, IconMask.alphaOf(0x80FFFFFF.toInt()))
    }

    private fun square(size: Int, glyph: Int, bg: Int, glyphSize: Int): IntArray {
        val lo = (size - glyphSize) / 2
        return IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (x in lo until lo + glyphSize && y in lo until lo + glyphSize) glyph else bg
        }
    }

    @Test
    fun whiteGlyphOnDarkTileGivesTintedMask() {
        val px = square(20, 0xFFFFFFFF.toInt(), 0xFF202020.toInt(), 10)
        val mask = IconMask.toMask(px, 0xFF336699.toInt())
        assertNotNull(mask)
        val centre = mask!![10 * 20 + 10]
        assertEquals(255, centre ushr 24)
        assertEquals(0x336699, centre and 0xFFFFFF)
        assertTrue((mask[0] ushr 24) < 40)
    }

    @Test
    fun darkGlyphOnLightTileIsInverted() {
        val px = square(20, 0xFF000000.toInt(), 0xFFF0F0F0.toInt(), 10)
        val mask = IconMask.toMask(px, 0xFFFFFFFF.toInt())!!
        assertEquals(255, mask[10 * 20 + 10] ushr 24)
        assertTrue((mask[0] ushr 24) < 40)
    }

    @Test
    fun emptyOrFullMasksAreUnreadable() {
        assertNull(IconMask.toMask(IntArray(100), 0xFFFFFFFF.toInt()))
        assertNull(IconMask.toMask(IntArray(0), 0xFFFFFFFF.toInt()))
        // a uniform black square: nothing to draw
        assertNull(IconMask.toMask(IntArray(100) { 0xFF000000.toInt() }, 0xFFFFFFFF.toInt()))
        // a uniform white square: nearly full mask
        assertNull(IconMask.toMask(IntArray(100) { 0xFFFFFFFF.toInt() }, 0xFFFFFFFF.toInt()))
    }

    @Test
    fun coverageBounds() {
        assertFalse(IconMask.isReadable(0.01))
        assertTrue(IconMask.isReadable(0.3))
        assertFalse(IconMask.isReadable(0.95))
        // YOK-031: a full round icon (pi/4 of the square) is a disc, not a glyph
        assertFalse(IconMask.isReadable(Math.PI / 4))
        assertTrue(IconMask.isReadable(0.74))
    }

    // YOK-031
    @Test
    fun filledDiscIconFallsBackToLetterTile() {
        val size = 40
        val c = size / 2.0
        val disc = IntArray(size * size) { i ->
            val dx = i % size + 0.5 - c
            val dy = i / size + 0.5 - c
            if (dx * dx + dy * dy <= c * c) 0xFF808080.toInt() else 0
        }
        assertNull(IconMask.toMask(disc, 0xFFFFFFFF.toInt(), sparseOnly = true))
        // a glyph on transparent still passes
        val glyph = square(20, 0xFFFFFFFF.toInt(), 0, 10)
        assertNotNull(IconMask.toMask(glyph, 0xFFFFFFFF.toInt(), sparseOnly = true))
    }

    // YOK-031
    @Test
    fun homeAppStyleDefaultsToTextAndRoundTrips() {
        assertEquals(HomeAppStyle.TEXT, HomeAppStyle.fromId(null))
        assertEquals(HomeAppStyle.TEXT, HomeAppStyle.fromId("nonsense"))
        HomeAppStyle.entries.forEach { assertEquals(it, HomeAppStyle.fromId(it.id)) }
    }

    @Test
    fun homeAppStyleSaysWhatItShows() {
        assertFalse(HomeAppStyle.TEXT.showsIcon)
        assertTrue(HomeAppStyle.TEXT.showsText)
        assertTrue(HomeAppStyle.ICON_TEXT.showsIcon && HomeAppStyle.ICON_TEXT.showsText)
        assertTrue(HomeAppStyle.ICON.showsIcon)
        assertFalse(HomeAppStyle.ICON.showsText)
    }

    private fun plan(
        style: HomeAppStyle, pin: Boolean = false, shortcut: Boolean = false, other: Boolean = false, n: Int = 0,
    ) = HomeAppPlan.plan(style, "Gmail", pin, shortcut, other, "Work", n, "$n notifications")

    @Test
    fun textStyleShowsOnlyText() {
        val p = plan(HomeAppStyle.TEXT, other = true, n = 3)
        assertTrue(p.showsText)
        assertEquals(HomeIconSource.NONE, p.icon)
        assertFalse(p.badge)
        assertFalse(p.dot)
        assertNull(p.contentDescription)
    }

    @Test
    fun iconSourceFollowsPinShortcutOrApp() {
        assertEquals(HomeIconSource.APP, plan(HomeAppStyle.ICON_TEXT).icon)
        assertEquals(HomeIconSource.SHORTCUT, plan(HomeAppStyle.ICON_TEXT, shortcut = true).icon)
        assertEquals(HomeIconSource.LETTER, plan(HomeAppStyle.ICON, pin = true, shortcut = true).icon)
    }

    @Test
    fun otherProfileIsBadgedInEveryIconStyleAndNamedInTheDescription() {
        val withText = plan(HomeAppStyle.ICON_TEXT, other = true)
        assertTrue(withText.badge)
        assertEquals("Gmail, Work", withText.contentDescription)
        assertFalse(plan(HomeAppStyle.ICON_TEXT).badge)
        assertNull(plan(HomeAppStyle.ICON_TEXT).contentDescription)
        assertFalse(plan(HomeAppStyle.ICON, pin = true, other = true).badge)
    }

    @Test
    fun iconOnlyDescribesNameProfileAndNotifications() {
        assertEquals("Gmail", plan(HomeAppStyle.ICON).contentDescription)
        assertFalse(plan(HomeAppStyle.ICON).dot)
        val p = plan(HomeAppStyle.ICON, other = true, n = 2)
        assertEquals("Gmail, Work, 2 notifications", p.contentDescription)
        assertTrue(p.dot)
        assertFalse(p.showsText)
        // With the name on screen the count already shows as text, so no dot and no count in the description
        assertFalse(plan(HomeAppStyle.ICON_TEXT, n = 2).dot)
    }

    @Test
    fun onlyTheRightPrefKeysRedrawTheHomeApps() {
        assertTrue(HomeAppPlan.affectsHomeApps(IconRowPrefs.KEY_HOME_APP_STYLE, HomeAppStyle.TEXT))
        assertFalse(HomeAppPlan.affectsHomeApps("slots", HomeAppStyle.ICON))
        assertFalse(HomeAppPlan.affectsHomeApps(IconRowPrefs.KEY_STYLE, HomeAppStyle.TEXT))
        assertTrue(HomeAppPlan.affectsHomeApps(IconRowPrefs.KEY_STYLE, HomeAppStyle.ICON_TEXT))
        assertFalse(HomeAppPlan.affectsHomeApps(null, HomeAppStyle.ICON))
    }

    @Test
    fun iconOnlyIsCentredOtherwiseTheAlignmentWins() {
        assertEquals(1, HomeAppPlan.gravityFor(HomeAppStyle.ICON, 8388613, 1))
        assertEquals(8388613, HomeAppPlan.gravityFor(HomeAppStyle.ICON_TEXT, 8388613, 1))
        assertEquals(8388613, HomeAppPlan.gravityFor(HomeAppStyle.TEXT, 8388613, 1))
    }
}
