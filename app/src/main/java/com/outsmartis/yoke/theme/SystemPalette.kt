package com.outsmartis.yoke.theme

import org.json.JSONObject

/**
 * The JSON Android 12+ reads from Settings.Secure [KEY] to pick the Material You palette
 * (SystemUI's ThemeOverlayController). A "preset" source with a seed colour makes the whole
 * system palette follow that colour, and wallpaper changes leave it alone. Pure, so it is unit tested.
 */
object SystemPalette {

    const val KEY = "theme_customization_overlay_packages"
    const val SYSTEM_PALETTE = "android.theme.customization.system_palette"
    const val ACCENT_COLOR = "android.theme.customization.accent_color"
    const val COLOR_SOURCE = "android.theme.customization.color_source"
    const val THEME_STYLE = "android.theme.customization.theme_style"
    const val COLOR_INDEX = "android.theme.customization.color_index"
    const val APPLIED_TIMESTAMP = "_applied_timestamp"

    /** "FF" + RRGGBB, the form SystemUI parses (it prepends '#'). */
    fun hex(color: Int): String = String.format("FF%06X", color and 0xFFFFFF)

    /**
     * [existing] with the palette set to [seed]. Other keys (icon shapes, fonts a theme app set)
     * are kept; the wallpaper colour index is dropped because the source is no longer the wallpaper.
     */
    fun build(existing: String?, seed: Int, now: Long): String {
        val json = parse(existing)
        json.remove(COLOR_INDEX)
        json.put(SYSTEM_PALETTE, hex(seed))
        json.put(ACCENT_COLOR, hex(seed))
        json.put(COLOR_SOURCE, "preset")
        json.put(THEME_STYLE, "TONAL_SPOT")
        json.put(APPLIED_TIMESTAMP, now)
        return json.toString()
    }

    /** The seed colour [value] asks for, or null when it follows the wallpaper or is unreadable. */
    fun seedOf(value: String?): Int? {
        val json = parse(value)
        if (json.optString(COLOR_SOURCE) != "preset") return null
        val hex = json.optString(SYSTEM_PALETTE).removePrefix("#").takeIf { it.length == 6 || it.length == 8 } ?: return null
        return hex.toLongOrNull(16)?.toInt()?.let { it or (0xFF shl 24) }
    }

    private fun parse(value: String?): JSONObject =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
}
