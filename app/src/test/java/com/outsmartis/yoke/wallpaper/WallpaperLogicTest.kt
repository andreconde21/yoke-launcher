package com.outsmartis.yoke.wallpaper

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
}
