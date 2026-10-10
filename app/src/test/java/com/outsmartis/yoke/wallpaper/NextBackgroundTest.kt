package com.outsmartis.yoke.wallpaper

import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.theme.OMARCHY_THEMES
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextBackgroundTest {
    private val theme = OMARCHY_THEMES.first { it.backgrounds.size >= 2 }
    private val files = theme.backgrounds
    private fun showing(file: String, themeId: String = theme.id) =
        WallpaperChoice(source = WallpaperSource.OMARCHY, omarchyTheme = themeId, omarchyFile = file, target = WallpaperTarget.HOME)

    @Test fun startsAtTheFirstBackgroundFromTheThemeColour() =
        assertEquals(files.first(), WallpaperLogic.nextBackground(theme.id, WallpaperChoice(), applied = true))

    @Test fun stepsToTheFollowingBackground() =
        assertEquals(files[1], WallpaperLogic.nextBackground(theme.id, showing(files[0]), applied = true))

    @Test fun wrapsAfterTheLast() =
        assertEquals(files.first(), WallpaperLogic.nextBackground(theme.id, showing(files.last()), applied = true))

    @Test fun anotherThemesBackgroundStartsOver() =
        assertEquals(files.first(), WallpaperLogic.nextBackground(theme.id, showing("x.png", "other"), applied = true))

    @Test fun neverAppliedStartsOver() =
        assertEquals(files.first(), WallpaperLogic.nextBackground(theme.id, showing(files[0]), applied = false))

    @Test fun themeWithoutBackgroundsGivesNothing() =
        assertNull(WallpaperLogic.nextBackground("no-such-theme", WallpaperChoice(), applied = true))

    @Test fun backgroundThemeSkipsIdsWithoutBackgrounds() =
        assertEquals(theme.id, WallpaperLogic.backgroundTheme("omarchy-pc", null, theme.id))

    @Test fun gesturesRoundTripThroughJson() {
        for (a in listOf(GestureAction.NextTheme, GestureAction.NextBackground)) {
            assertEquals(a, GestureAction.fromJson(JSONObject(a.toJson().toString())))
        }
    }
}
