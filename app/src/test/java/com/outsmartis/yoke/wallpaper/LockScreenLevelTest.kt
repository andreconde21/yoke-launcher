package com.outsmartis.yoke.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenLevelTest {

    @Test
    fun offLeavesTheTargetAlone() {
        WallpaperTarget.entries.forEach { assertEquals(it, LockScreenLevel.OFF.effectiveTarget(it)) }
    }

    @Test
    fun higherLevelsAddTheLockScreenToHomeOnly() {
        for (level in listOf(LockScreenLevel.WALLPAPER, LockScreenLevel.WALLPAPER_SYSTEM, LockScreenLevel.YOKE)) {
            assertEquals(WallpaperTarget.BOTH, level.effectiveTarget(WallpaperTarget.HOME))
            assertEquals(WallpaperTarget.LOCK, level.effectiveTarget(WallpaperTarget.LOCK))
            assertEquals(WallpaperTarget.BOTH, level.effectiveTarget(WallpaperTarget.BOTH))
        }
    }

    @Test
    fun levelsIncludeTheOnesBelow() {
        assertFalse(LockScreenLevel.OFF.includesWallpaper)
        assertTrue(LockScreenLevel.WALLPAPER.includesWallpaper)
        assertFalse(LockScreenLevel.WALLPAPER.includesSystem)
        assertTrue(LockScreenLevel.WALLPAPER_SYSTEM.includesWallpaper)
        assertTrue(LockScreenLevel.WALLPAPER_SYSTEM.includesSystem)
        assertFalse(LockScreenLevel.YOKE.includesSystem)
    }

    @Test
    fun decodeFallsBackToOff() {
        assertEquals(LockScreenLevel.OFF, LockScreenLevel.decode(null))
        assertEquals(LockScreenLevel.OFF, LockScreenLevel.decode("nonsense"))
        LockScreenLevel.entries.forEach { assertEquals(it, LockScreenLevel.decode(it.name)) }
    }

    private val off = LockScreenLevel.OFF
    private val wp = LockScreenLevel.WALLPAPER
    private val sys = LockScreenLevel.WALLPAPER_SYSTEM

    @Test
    fun systemLevelForcesColoursOnlyWhenOffAndAllowed() {
        assertEquals(LockScreenLogic.ColourAction.FORCE_ON, LockScreenLogic.colourAction(sys, false, false, true))
        assertEquals(LockScreenLogic.ColourAction.NONE, LockScreenLogic.colourAction(sys, true, false, true)) // user's own
        assertEquals(LockScreenLogic.ColourAction.NONE, LockScreenLogic.colourAction(sys, false, false, false)) // no grant
    }

    @Test
    fun leavingSystemLevelRestoresOnlyWhatTheLevelForced() {
        assertEquals(LockScreenLogic.ColourAction.RESTORE, LockScreenLogic.colourAction(wp, true, true, true))
        assertEquals(LockScreenLogic.ColourAction.RESTORE, LockScreenLogic.colourAction(off, true, true, false))
        // The user turned colours on themselves (flag cleared by the manual toggle): leave them alone.
        assertEquals(LockScreenLogic.ColourAction.NONE, LockScreenLogic.colourAction(off, true, false, true))
    }

    @Test
    fun wallpaperIsNeverReappliedWithoutAYokeWallpaper() {
        assertFalse(LockScreenLogic.shouldReapply(off, wp, WallpaperTarget.HOME, applied = false))
        assertFalse(LockScreenLogic.shouldReapply(off, sys, WallpaperTarget.BOTH, applied = false))
        assertTrue(LockScreenLogic.shouldReapply(off, wp, WallpaperTarget.HOME, applied = true))
    }

    @Test
    fun reapplyOnlyWhenTheLockScreenBecomesCovered() {
        assertFalse(LockScreenLogic.shouldReapply(wp, off, WallpaperTarget.HOME, applied = true))
        assertFalse(LockScreenLogic.shouldReapply(wp, sys, WallpaperTarget.HOME, applied = true))
        assertTrue(LockScreenLogic.shouldReapply(off, wp, WallpaperTarget.LOCK, applied = true))
    }

    @Test
    fun needsWallpaperOnlyForWallpaperLevelsWithoutOne() {
        assertTrue(LockScreenLogic.needsWallpaper(wp, applied = false))
        assertFalse(LockScreenLogic.needsWallpaper(wp, applied = true))
        assertFalse(LockScreenLogic.needsWallpaper(off, applied = false))
    }
}
