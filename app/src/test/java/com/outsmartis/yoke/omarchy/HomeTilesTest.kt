package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTilesTest {
    @Test fun offHasNoBorderEvenWhenPressed() {
        assertNull(HomeTiles.border(BorderMode.OFF, highlighted = true))
        assertNull(HomeTiles.border(BorderMode.OFF, highlighted = false))
    }

    @Test fun pressOnlyBordersThePressedTile() {
        assertEquals(BorderColor.ACCENT, HomeTiles.border(BorderMode.PRESS, highlighted = true))
        assertNull(HomeTiles.border(BorderMode.PRESS, highlighted = false))
    }

    @Test fun alwaysUsesSecondaryAndAccentForThePressedOne() {
        assertEquals(BorderColor.ACCENT, HomeTiles.border(BorderMode.ALWAYS, highlighted = true))
        assertEquals(BorderColor.SECONDARY, HomeTiles.border(BorderMode.ALWAYS, highlighted = false))
    }

    @Test fun cardRanges() {
        assertEquals(listOf(1, 2, 3), HomeTiles.WIDTHS_DP)
        assertTrue(HomeTiles.RADII_DP.first() == 0 && HomeTiles.RADII_DP.last() == 12)
    }

    @Test fun nextWraps() {
        assertEquals(3, HomeTiles.next(2, HomeTiles.WIDTHS_DP))
        assertEquals(1, HomeTiles.next(3, HomeTiles.WIDTHS_DP))
        assertEquals(1, HomeTiles.next(7, HomeTiles.WIDTHS_DP))
    }

    @Test fun invalidStoredValuesFallBack() {
        assertEquals(HomeTiles.DEFAULT_WIDTH_DP, HomeTiles.valid(9, HomeTiles.WIDTHS_DP, HomeTiles.DEFAULT_WIDTH_DP))
        assertEquals(8, HomeTiles.valid(8, HomeTiles.RADII_DP, HomeTiles.DEFAULT_RADIUS_DP))
    }

    @Test fun modeDefaultsToOff() = assertEquals(BorderMode.OFF, BorderMode.fromId(null))
}
