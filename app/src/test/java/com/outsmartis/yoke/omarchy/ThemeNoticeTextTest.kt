package com.outsmartis.yoke.omarchy

import com.outsmartis.yoke.backup.YokeBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeNoticeTextTest {
    private val files = listOf("1-a.jpg", "2-b.png", "3-c.jpg", "4-d.jpg")

    @Test fun themeNoticeUsesTheId() = assertEquals("Theme: tokyo-night", ThemeNoticeText.theme("tokyo-night"))
    @Test fun backgroundCountsFromOne() = assertEquals("Background: 2/4", ThemeNoticeText.background(files, "2-b.png"))
    @Test fun lastBackground() = assertEquals("Background: 4/4", ThemeNoticeText.background(files, "4-d.jpg"))
    @Test fun unknownBackgroundHasNoNotice() = assertNull(ThemeNoticeText.background(files, "x.jpg"))
    @Test fun noBackgroundsHasNoNotice() = assertNull(ThemeNoticeText.background(emptyList(), null))
    @Test fun shortAndLong() {
        assertEquals(ThemeNoticeText.SHORT_MS, ThemeNoticeText.durationMs(false))
        assertTrue(ThemeNoticeText.durationMs(true) > ThemeNoticeText.durationMs(false))
    }
    @Test fun omarchyStyleIsBackedUp() = assertTrue(OmarchyStylePrefs.FILE in YokeBackup.FILES)
}
