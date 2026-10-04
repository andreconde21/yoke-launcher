package com.outsmartis.yoke.theme

import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONObject

/** Column access for the Conductore theme row; a fake in tests, a Cursor on device. */
fun interface ThemeRow {
    fun getString(column: String): String?
}

/**
 * Conductore publishes the theme of the Omarchy PC it follows as one row at
 * [URI] (pc_theme), guarded by [PERMISSION]: name (Omarchy folder), machine,
 * updated_at (epoch ms, 0 when unknown), label, mode (dark|light) and #RRGGBB
 * strings for accent, background, foreground, muted, selection,
 * lighter_background and the hues. Columns are null while nothing is followed.
 */
object ConductoreTheme {
    const val AUTHORITY = "com.outsmartis.conductore.launcherdetails"
    const val PERMISSION = "com.outsmartis.permission.READ_LAUNCHER_DETAILS"
    val URI: Uri by lazy { Uri.parse("content://$AUTHORITY/pc_theme") }

    private val COLUMNS = listOf(
        "name", "machine", "updated_at", "label", "mode", "accent", "background", "foreground", "muted", "selection",
        "lighter_background", "red", "green", "yellow", "blue", "magenta", "cyan", "orange",
    )

    /** "#RRGGBB" (or "RRGGBB") to opaque ARGB, null if malformed. */
    fun parseHex(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6) return null
        val rgb = hex.toLongOrNull(16) ?: return null
        return (0xFF000000 or rgb).toInt()
    }

    /**
     * The palette in [row], or null when the essentials (accent, background,
     * foreground) are missing or malformed. Optional colours fall back to
     * something derived so a partial row still yields a usable theme.
     */
    fun parse(row: ThemeRow): OmarchyTheme? {
        val folder = row.getString("name")?.trim()
        OMARCHY_THEMES.firstOrNull { it.id == folder }?.let { return it }
        val accent = parseHex(row.getString("accent")) ?: return null
        val background = parseHex(row.getString("background")) ?: return null
        val foreground = parseHex(row.getString("foreground")) ?: return null
        val dark = when (row.getString("mode")?.trim()?.lowercase()) {
            "dark" -> true
            "light" -> false
            else -> ThemeContrast.luminance(background) < 0.5
        }
        val lighter = parseHex(row.getString("lighter_background"))
            ?: ThemeContrast.blend(background, foreground, 0.08)
        fun hue(column: String) = parseHex(row.getString(column)) ?: accent
        return OmarchyTheme(
            id = YokeTheme.PC_ID,
            name = row.getString("label")?.trim()?.takeIf { it.isNotEmpty() }
                ?: folder?.takeIf { it.isNotEmpty() } ?: "Omarchy PC",
            dark = dark,
            accent = accent,
            background = background,
            foreground = foreground,
            selection = parseHex(row.getString("selection")) ?: lighter,
            muted = parseHex(row.getString("muted")) ?: ThemeContrast.blend(foreground, background, 0.4),
            darkBackground = background,
            lighterBackground = lighter,
            red = hue("red"), green = hue("green"), yellow = hue("yellow"), blue = hue("blue"),
            magenta = hue("magenta"), cyan = hue("cyan"), orange = hue("orange"),
        )
    }

    /** The row as JSON, to remember the last known theme. */
    fun toJson(row: ThemeRow): String =
        JSONObject().also { json -> COLUMNS.forEach { c -> row.getString(c)?.let { json.put(c, it) } } }.toString()

    fun fromJson(json: String?): ThemeRow? {
        val obj = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
        return ThemeRow { column -> if (obj.has(column)) obj.optString(column) else null }
    }

    /** Result of asking the provider. */
    sealed class Read {
        class Available(val row: ThemeRow, val raw: String, val theme: OmarchyTheme) : Read() {
            val machine: String? get() = row.getString("machine")?.takeIf { it.isNotBlank() }
            val updatedAt: Long get() = row.getString("updated_at")?.toLongOrNull() ?: 0L
        }
        object Unavailable : Read()
    }

    /** Queries the provider; never throws (not installed, permission denied, no row, bad data). */
    fun read(context: Context): Read {
        val cursor: Cursor = try {
            context.contentResolver.query(URI, null, null, null, null)
        } catch (_: Exception) {
            null
        } ?: return Read.Unavailable
        cursor.use {
            if (!it.moveToFirst()) return Read.Unavailable
            val row = ThemeRow { column ->
                val i = it.getColumnIndex(column)
                if (i < 0 || it.isNull(i)) null else runCatching { it.getString(i) }.getOrNull()
            }
            val theme = parse(row) ?: return Read.Unavailable
            return Read.Available(row, toJson(row), theme)
        }
    }
}
