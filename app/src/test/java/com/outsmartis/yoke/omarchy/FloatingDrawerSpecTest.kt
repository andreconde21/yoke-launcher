package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingDrawerSpecTest {
    @Test fun styleDefaultsToTheFullList() = assertEquals(DrawerStyle.LIST, DrawerStyle.fromId(null))
    @Test fun positionDefaultsToCentre() = assertEquals(FloatPosition.CENTRE, FloatPosition.fromId("nope"))

    @Test fun widthIsAShareOfTheScreen() = assertEquals(918, FloatingDrawerSpec.widthPx(1080, 85, 2000))
    @Test fun widthIsCappedOnWideScreens() = assertEquals(1280, FloatingDrawerSpec.widthPx(2400, 95, 1280))

    @Test fun listGrowsUpToMaxResultsRows() {
        assertEquals(400, FloatingDrawerSpec.listMaxHeightPx(50, 8))
        assertEquals(5, FloatingDrawerSpec.listMaxHeightPx(0, 5))
    }

    @Test fun optionsCycleAndFallBack() {
        assertEquals(95, FloatingDrawerSpec.next(85, FloatingDrawerSpec.WIDTHS_PCT))
        assertEquals(5, FloatingDrawerSpec.next(12, FloatingDrawerSpec.MAX_RESULTS))
        assertEquals(FloatingDrawerSpec.DEFAULT_MAX_RESULTS,
            FloatingDrawerSpec.valid(3, FloatingDrawerSpec.MAX_RESULTS, FloatingDrawerSpec.DEFAULT_MAX_RESULTS))
    }
}
