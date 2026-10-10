package com.outsmartis.yoke.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    private fun fake(vararg pairs: Pair<String, String>): ThemeRow {
        val map = pairs.toMap()
        return ThemeRow { map[it] }
    }

    private val fullRow = arrayOf(
        "name" to "midnight", "machine" to "dev", "updated_at" to "1700000000000", "label" to "Midnight", "mode" to "dark", "accent" to "#89B4FA", "background" to "#1E1E2E",
        "foreground" to "#CDD6F4", "muted" to "#585B70", "selection" to "#45475A",
        "lighter_background" to "#313244", "red" to "#F38BA8", "green" to "#A6E3A1",
        "yellow" to "#F9E2AF", "blue" to "#89B4FA", "magenta" to "#F5C2E7", "cyan" to "#94E2D5",
        "orange" to "#F6B6AB",
    )

    @Test
    fun bundledThemesAreAllThereAndUnique() {
        assertEquals(22, OMARCHY_THEMES.size)
        assertEquals(OMARCHY_THEMES.size, OMARCHY_THEMES.map { it.id }.toSet().size)
        val latte = OMARCHY_THEMES.first { it.id == "catppuccin-latte" }
        assertFalse(latte.dark)
        assertEquals(0xFFEFF1F5.toInt(), latte.background)
        assertTrue(OMARCHY_THEMES.first { it.id == "catppuccin" }.dark)
    }

    @Test
    fun conductoreRowMapsToTheme() {
        val theme = ConductoreTheme.parse(fake(*fullRow))!!
        assertEquals("Midnight", theme.name)
        assertTrue(theme.dark)
        assertEquals(0xFF89B4FA.toInt(), theme.accent)
        assertEquals(0xFF1E1E2E.toInt(), theme.background)
        assertEquals(0xFFCDD6F4.toInt(), theme.foreground)
        assertEquals(0xFF313244.toInt(), theme.lighterBackground)
        assertEquals(0xFFF6B6AB.toInt(), theme.orange)
        assertEquals(YokeTheme.PC_ID, theme.id)
    }

    @Test
    fun pcThemeMatchingABundledNameUsesTheBundledTheme() {
        val theme = ConductoreTheme.parse(fake("name" to "tokyo-night", "machine" to "dev", "accent" to "#000000"))!!
        assertEquals("tokyo-night", theme.id)
        assertEquals(OMARCHY_THEMES.first { it.id == "tokyo-night" }.background, theme.background)
    }

    @Test
    fun pcThemeWithNothingFollowedIsUnavailable() {
        // Provider answers with a row of nulls and updated_at 0.
        assertNull(ConductoreTheme.parse(fake("updated_at" to "0")))
    }

    @Test
    fun conductoreLightModeAndMissingOptionalsFallBack() {
        val theme = ConductoreTheme.parse(
            fake("mode" to "light", "accent" to "1e66f5", "background" to "#EFF1F5", "foreground" to "#4C4F69"),
        )!!
        assertFalse(theme.dark)
        assertEquals("Omarchy PC", theme.name)
        assertEquals(theme.accent, theme.red)
    }

    @Test
    fun conductoreInfersModeWhenUnknown() {
        val row = fake("mode" to "sepia", "accent" to "#000000", "background" to "#FFFFFF", "foreground" to "#111111")
        assertFalse(ConductoreTheme.parse(row)!!.dark)
    }

    @Test
    fun conductoreRejectsMissingOrMalformedEssentials() {
        assertNull(ConductoreTheme.parse(fake()))
        assertNull(ConductoreTheme.parse(fake("accent" to "#zzzzzz", "background" to "#000000", "foreground" to "#ffffff")))
        assertNull(ConductoreTheme.parse(fake("accent" to "#fff", "background" to "#000000", "foreground" to "#ffffff")))
        assertNull(ConductoreTheme.parse(fake("accent" to "#112233", "background" to "#000000")))
    }

    @Test
    fun conductoreLastKnownSurvivesJsonRoundTrip() {
        val json = ConductoreTheme.toJson(fake(*fullRow))
        val again = ConductoreTheme.parse(ConductoreTheme.fromJson(json)!!)!!
        assertEquals("Midnight", again.name)
        assertEquals(0xFF1E1E2E.toInt(), again.background)
        assertNull(ConductoreTheme.fromJson(null))
        assertNull(ConductoreTheme.fromJson("not json"))
    }

    @Test
    fun cycleWrapsAndRecoversFromUnknownIds() {
        val ids = ThemeCatalog.ids()
        assertEquals("system", ids.first())
        assertEquals("omarchy-pc", ids.last())
        assertEquals(OMARCHY_THEMES.first().id, ThemeCatalog.next("system", ids))
        assertEquals("system", ThemeCatalog.next("omarchy-pc", ids))
        assertEquals("system", ThemeCatalog.next("gone", ids))
        assertFalse("omarchy-pc" in ThemeCatalog.ids(conductore = false))
        assertEquals("system", ThemeCatalog.next("catppuccin", listOf("system")))
    }

    @Test
    fun everyBundledThemeIsReadable() {
        // Light and dark alike: text and accents must be legible on the background.
        for (p in OMARCHY_THEMES) {
            val t = YokeTheme.from(p)
            assertTrue("${p.id} text", ThemeContrast.ratio(t.foreground, t.background) >= 4.5)
            assertTrue("${p.id} secondary", ThemeContrast.ratio(t.secondary, t.background) >= 3.0)
            assertTrue("${p.id} accentText", ThemeContrast.ratio(t.accentText, t.background) >= 3.0)
            assertTrue("${p.id} onAccent", ThemeContrast.ratio(t.onAccent, t.accent) >= 4.5)
        }
    }

    @Test
    fun lightThemeKeepsDarkTextAndLowContrastMutedIsLifted() {
        val latte = YokeTheme.from(OMARCHY_THEMES.first { it.id == "catppuccin-latte" })
        assertFalse(latte.dark)
        assertTrue(ThemeContrast.luminance(latte.foreground) < ThemeContrast.luminance(latte.background))
        // Latte's own muted (#ACB0BE on #EFF1F5) is under 3:1, so secondary text must differ from it.
        assertTrue(latte.secondary != latte.muted)
        assertNotNull(latte)
    }

    @Test
    fun contrastHelpers() {
        assertEquals(21.0, ThemeContrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(0xFF000000.toInt(), ThemeContrast.onColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ThemeContrast.onColor(0xFF101010.toInt()))
        val fixed = ThemeContrast.ensure(0xFF777777.toInt(), 0xFF808080.toInt(), 3.0)
        assertTrue(ThemeContrast.ratio(fixed, 0xFF808080.toInt()) >= 3.0)
    }
}
