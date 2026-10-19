package com.outsmartis.yoke.wallpaper

import com.outsmartis.yoke.theme.OMARCHY_THEMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLogicTest {

    @Test
    fun urlPinsCommitAndEncodesFileName() {
        assertEquals(
            "https://raw.githubusercontent.com/basecamp/omarchy/${WallpaperLogic.OMARCHY_COMMIT}/themes/tokyo-night/backgrounds/1-a%20b%2Bc.jpg",
            WallpaperLogic.backgroundUrl("tokyo-night", "1-a b+c.jpg"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/basecamp/omarchy/abc/themes/x/backgrounds/%C3%A9%2F..%2Fy.png",
            WallpaperLogic.backgroundUrl("x", "é/../y.png", "abc"),
        )
    }

    @Test
    fun backgroundListComesFromGeneratedData() {
        val files = WallpaperLogic.backgroundsFor("catppuccin")
        assertTrue(files.isNotEmpty())
        assertTrue(files.all { it.isNotBlank() })
        assertEquals(emptyList<String>(), WallpaperLogic.backgroundsFor("no-such-theme"))
        assertEquals(emptyList<String>(), WallpaperLogic.backgroundsFor("system"))
    }

    @Test
    fun cacheNameCannotEscapeTheCacheDirectory() {
        assertEquals(".._.._x.jpg".trimStart('.'), WallpaperLogic.cacheName("../../x.jpg"))
        assertTrue(!WallpaperLogic.cacheName("../a/b").contains('/'))
        assertTrue(!WallpaperLogic.cacheName("..").startsWith("."))
    }

    @Test
    fun cropWiderSourceKeepsFullHeightAndCentres() {
        val r = WallpaperLogic.cropRect(4000, 2000, 1080, 2400)
        assertEquals(2000, r.height)
        assertEquals(900, r.width) // 2000 * 1080 / 2400
        assertEquals(1550, r.left)
        assertEquals(0, r.top)
    }

    @Test
    fun cropTallerSourceKeepsFullWidthAndCentres() {
        val r = WallpaperLogic.cropRect(1000, 3000, 1080, 1920)
        assertEquals(1000, r.width)
        assertEquals(1777, r.height) // 1000 * 1920 / 1080
        assertEquals(0, r.left)
        assertEquals(611, r.top)
    }

    @Test
    fun cropSameAspectIsWholeImage() {
        val r = WallpaperLogic.cropRect(540, 960, 1080, 1920)
        assertEquals(CropRect(0, 0, 540, 960), r)
    }

    @Test
    fun sampleSizeStaysAtOrAboveRequest() {
        assertEquals(1, WallpaperLogic.sampleSize(1080, 1920, 1080, 1920))
        assertEquals(2, WallpaperLogic.sampleSize(2200, 3900, 1080, 1920))
        assertEquals(4, WallpaperLogic.sampleSize(4400, 7800, 1080, 1920))
    }

    @Test
    fun prefsCodecRoundTrips() {
        val c = WallpaperChoice(WallpaperSource.OMARCHY, "tokyo-night", "1-a b.jpg", WallpaperTarget.LOCK, DimLevel.STRONG)
        assertEquals(c, WallpaperChoice.decode(c.encode()))
        val d = WallpaperChoice()
        assertEquals(d, WallpaperChoice.decode(d.encode()))
    }

    @Test
    fun prefsCodecDefaultsOnGarbage() {
        assertEquals(WallpaperChoice(), WallpaperChoice.decode(null))
        assertEquals(WallpaperChoice(), WallpaperChoice.decode(""))
        assertEquals(WallpaperChoice(), WallpaperChoice.decode("NOPE\t\t\tWHAT\tHUH"))
        assertEquals(WallpaperSource.IMAGE, WallpaperChoice.decode("IMAGE").source)
    }

    @Test
    fun onlyImageSourcesShowTheSystemWallpaper() {
        assertTrue(!WallpaperChoice(source = WallpaperSource.THEME_COLOUR).showsImage)
        assertTrue(WallpaperChoice(source = WallpaperSource.OMARCHY).showsImage)
        assertTrue(WallpaperChoice(source = WallpaperSource.IMAGE).showsImage)
    }

    private val omarchy = WallpaperChoice(WallpaperSource.OMARCHY, "catppuccin", "x.png")

    @Test
    fun themeChangeSwitchesAnAppliedOmarchyBackground() {
        val other = OMARCHY_THEMES.first { it.id != "catppuccin" && it.backgrounds.isNotEmpty() }
        val r = WallpaperLogic.backgroundForTheme(omarchy, true, other.id)
        assertEquals(ThemeBackground.Switch(other.id, other.backgrounds.first()), r)
    }

    @Test
    fun themeChangeFallsBackToColourWithoutBackgrounds() {
        assertEquals(ThemeBackground.Colour, WallpaperLogic.backgroundForTheme(omarchy, true, "no-such-theme"))
        assertEquals(ThemeBackground.Colour, WallpaperLogic.backgroundForTheme(omarchy, true, null))
    }

    @Test
    fun themeChangeNeverTouchesWallpapersYokeDidNotApply() {
        assertEquals(ThemeBackground.Keep, WallpaperLogic.backgroundForTheme(omarchy, false, "no-such-theme"))
        assertEquals(ThemeBackground.Keep, WallpaperLogic.backgroundForTheme(WallpaperChoice(), true, "no-such-theme"))
        assertEquals(ThemeBackground.Keep, WallpaperLogic.backgroundForTheme(omarchy.copy(source = WallpaperSource.IMAGE), true, "x"))
        assertEquals(ThemeBackground.Keep, WallpaperLogic.backgroundForTheme(omarchy, true, "catppuccin"))
    }

    @Test
    fun onlyAnUnchangedWallpaperMayBeOverwritten() {
        assertTrue(WallpaperLogic.mayOverwrite(29, 29))
        assertTrue(!WallpaperLogic.mayOverwrite(29, 30)) // set by someone else since
        assertTrue(!WallpaperLogic.mayOverwrite(29, 28))
    }

    @Test
    fun unknownCurrentIdIsNeverOverwritten() {
        assertTrue(!WallpaperLogic.mayOverwrite(29, -1))
        assertTrue(!WallpaperLogic.mayOverwrite(29, 0))
        assertTrue(!WallpaperLogic.mayOverwrite(0, -1))
    }

    @Test
    fun noRecordedIdIsTrustedOnceForOlderInstalls() {
        assertTrue(WallpaperLogic.mayOverwrite(0, 12))
    }

    @Test
    fun sharedWallpaperForBothScreensIsOwned() {
        assertTrue(WallpaperLogic.ownsTarget(true, true, 29, 29, -1, -1))
    }

    @Test
    fun separateLockWallpaperYokeSetIsOwned() {
        assertTrue(WallpaperLogic.ownsTarget(true, true, 29, 29, 30, 30))
        assertTrue(WallpaperLogic.ownsTarget(false, true, 0, 0, 30, 30))
        assertTrue(WallpaperLogic.ownsTarget(true, false, 29, 29, 0, 0))
    }

    @Test
    fun outsideAppSettingBothScreensIsNotOwned() {
        assertTrue(!WallpaperLogic.ownsTarget(true, true, 29, 31, -1, -1))
        assertTrue(!WallpaperLogic.ownsTarget(true, true, 29, 31, 30, -1))
    }

    @Test
    fun outsideAppSettingOnlyTheLockIsNotOwned() {
        assertTrue(!WallpaperLogic.ownsTarget(true, true, 29, 29, 30, 33))
        assertTrue(!WallpaperLogic.ownsTarget(false, true, 0, 0, 30, -1))
    }

    @Test
    fun sharedLockWallpaperThenALockOnlyOneFromAnotherAppIsNotOwned() {
        assertTrue(!WallpaperLogic.ownsTarget(true, true, 29, 29, -1, 33))
        assertTrue(!WallpaperLogic.ownsTarget(false, true, 0, 0, -1, 33))
    }

    @Test
    fun neverRecordedLockIdIsTrustedOnlyViaAdoption() {
        assertTrue(WallpaperLogic.ownsTarget(true, true, 29, 29, 0, 33))
        assertEquals(33, WallpaperLogic.adoptId(0, 33, true))
        assertEquals(-1, WallpaperLogic.adoptId(0, -1, true))
        assertEquals(0, WallpaperLogic.adoptId(0, -1, false))
        assertEquals(0, WallpaperLogic.adoptId(0, 0, true))
        assertEquals(29, WallpaperLogic.adoptId(29, 31, true)) // recorded ids are never replaced
        assertEquals(-1, WallpaperLogic.adoptId(-1, 33, true))
    }

    @Test
    fun onlyTheLockReplacedKeepsTheHomeScreenManaged() {
        val o = WallpaperLogic.ownership(true, true, 29, 29, -1, 33) // BOTH was shared, a lock-only one came from outside
        assertTrue(o.system)
        assertTrue(!o.lock)
        assertEquals(WallpaperTarget.HOME, WallpaperLogic.screensToWrite(WallpaperTarget.BOTH, false, true))
    }

    @Test
    fun onlyTheHomeReplacedKeepsTheLockScreenManaged() {
        // another app set the home wallpaper only: the lock screen keeps the formerly shared image (id 29)
        val o = WallpaperLogic.ownership(true, true, 29, 31, -1, 29)
        assertTrue(!o.system)
        assertTrue(o.lock)
        assertEquals(WallpaperTarget.LOCK, WallpaperLogic.screensToWrite(WallpaperTarget.BOTH, true, false))
    }

    @Test
    fun bothReplacedLeavesNothingToWrite() {
        assertTrue(!WallpaperLogic.ownership(true, true, 29, 31, -1, -1).lock)
        assertEquals(null, WallpaperLogic.screensToWrite(WallpaperTarget.BOTH, true, true))
        assertEquals(null, WallpaperLogic.screensToWrite(WallpaperTarget.HOME, true, false))
    }

    @Test
    fun releasedScreensNeverWidenTheTarget() {
        assertEquals(WallpaperTarget.BOTH, WallpaperLogic.screensToWrite(WallpaperTarget.BOTH, false, false))
        assertEquals(WallpaperTarget.HOME, WallpaperLogic.screensToWrite(WallpaperTarget.HOME, false, true))
        assertEquals(WallpaperTarget.LOCK, WallpaperLogic.screensToWrite(WallpaperTarget.LOCK, true, false))
    }

    @Test
    fun anInterruptedRetryEndsQuietlyAndKeepsTheFlag() {
        var calls = 0
        val r = WallpaperLogic.retryQuietly(2, 800, sleeper = { throw InterruptedException() }) { calls++; null }
        assertEquals(null, r)
        assertEquals(1, calls)
        assertTrue(Thread.interrupted()) // the flag was restored; clear it for other tests
    }

    @Test
    fun aRetrySucceedsOnTheSecondTry() {
        var calls = 0
        assertEquals("ok", WallpaperLogic.retryQuietly(2, 0, sleeper = {}) { if (++calls == 2) "ok" else null })
    }
}
