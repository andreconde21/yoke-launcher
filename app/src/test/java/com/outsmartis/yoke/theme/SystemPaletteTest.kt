package com.outsmartis.yoke.theme

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPaletteTest {

    private val blue = 0xFF7AA2F7.toInt()

    @Test
    fun hexIsOpaqueArgb() {
        assertEquals("FF7AA2F7", SystemPalette.hex(blue))
        assertEquals("FF00000A", SystemPalette.hex(0x0A))
    }

    @Test
    fun buildFromNothingSetsAPresetSeed() {
        val json = JSONObject(SystemPalette.build(null, blue, 42L))
        assertEquals("FF7AA2F7", json.getString(SystemPalette.SYSTEM_PALETTE))
        assertEquals("FF7AA2F7", json.getString(SystemPalette.ACCENT_COLOR))
        assertEquals("preset", json.getString(SystemPalette.COLOR_SOURCE))
        assertEquals("TONAL_SPOT", json.getString(SystemPalette.THEME_STYLE))
        assertEquals(42L, json.getLong(SystemPalette.APPLIED_TIMESTAMP))
    }

    @Test
    fun buildKeepsOtherKeysAndDropsWallpaperIndex() {
        val existing = """{"android.theme.customization.color_source":"home_wallpaper",""" +
            """"android.theme.customization.color_index":"2","android.theme.customization.icon_shape":"x"}"""
        val json = JSONObject(SystemPalette.build(existing, blue, 1L))
        assertEquals("x", json.getString("android.theme.customization.icon_shape"))
        assertFalse(json.has(SystemPalette.COLOR_INDEX))
        assertEquals("preset", json.getString(SystemPalette.COLOR_SOURCE))
    }

    @Test
    fun buildSurvivesGarbage() {
        val json = JSONObject(SystemPalette.build("not json", blue, 1L))
        assertEquals("FF7AA2F7", json.getString(SystemPalette.SYSTEM_PALETTE))
    }

    @Test
    fun seedRoundTrips() {
        assertEquals(blue, SystemPalette.seedOf(SystemPalette.build(null, blue, 1L)))
    }

    @Test
    fun seedOfWallpaperSourceIsNull() {
        assertNull(SystemPalette.seedOf("""{"android.theme.customization.color_source":"home_wallpaper","android.theme.customization.system_palette":"FF112233"}"""))
        assertNull(SystemPalette.seedOf(null))
        assertNull(SystemPalette.seedOf(""))
    }

    @Test
    fun seedAcceptsSixDigitsAndHash() {
        val v = """{"android.theme.customization.color_source":"preset","android.theme.customization.system_palette":"#112233"}"""
        assertEquals(0xFF112233.toInt(), SystemPalette.seedOf(v))
    }
}
