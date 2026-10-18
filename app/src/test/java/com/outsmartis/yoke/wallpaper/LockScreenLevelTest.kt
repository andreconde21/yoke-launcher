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
}
