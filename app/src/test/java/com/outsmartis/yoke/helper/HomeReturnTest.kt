package com.outsmartis.yoke.helper

import com.outsmartis.yoke.ui.SettingsScroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReturnTest {

    @Test
    fun popsToHomeUnlessPrivateSpaceIsToggling() {
        assertTrue(HomeReturn.shouldPop(false))
        assertFalse(HomeReturn.shouldPop(true))
    }

    @Test
    fun onlyAStopWithTheScreenOffPopsToHome() {
        assertTrue(HomeReturn.popOnStop(false))
        assertFalse(HomeReturn.popOnStop(true))
    }

    @Test
    fun scrollIsRestoredOnlyOnceTheContentIsTallEnough() {
        assertNull(HomeReturn.scrollTarget(900, 1500, 1000)) // only 500 scrollable: rows still filling in
        assertEquals(900, HomeReturn.scrollTarget(900, 2000, 1000))
        assertEquals(1000, HomeReturn.scrollTarget(1000, 2000, 1000))
        assertNull(HomeReturn.scrollTarget(0, 2000, 1000))
    }
}

class SettingsScrollTest {

    @Test
    fun positionSurvivesTwoRecreatesInARow() {
        SettingsScroll.y = 900
        SettingsScroll.onViewDestroyed(changingConfigurations = true)
        assertEquals(900, SettingsScroll.takeWanted()) // first new view, not yet laid out
        SettingsScroll.onViewDestroyed(changingConfigurations = true) // recreated again before the restore
        assertEquals(900, SettingsScroll.takeWanted())
    }

    @Test
    fun leavingSettingsForRealForgetsThePosition() {
        SettingsScroll.y = 900
        SettingsScroll.onViewDestroyed(changingConfigurations = false)
        assertEquals(0, SettingsScroll.takeWanted())
    }
}
