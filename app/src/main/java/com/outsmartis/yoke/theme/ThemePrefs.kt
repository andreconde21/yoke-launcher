package com.outsmartis.yoke.theme

import android.content.Context

/** Which theme and font are selected. Own file, apart from the launcher's Prefs. */
class ThemePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yoke.theme", Context.MODE_PRIVATE)

    var themeId: String
        get() = prefs.getString("theme_id", YokeTheme.SYSTEM_ID) ?: YokeTheme.SYSTEM_ID
        set(value) = prefs.edit().putString("theme_id", value).apply()

    /** The last theme row Conductore gave us, kept for when it is not reachable. */
    var conductoreLastKnown: String?
        get() = prefs.getString("conductore_last_known", null)
        set(value) = prefs.edit().putString("conductore_last_known", value).apply()

    /** The chosen font. Before the picker this was the on/off key `font_jetbrains_mono`, still honoured. */
    var font: FontChoice // YOK-034
        get() = FontChoice.resolve(prefs.getString("font_id", null), prefs.getBoolean("font_jetbrains_mono", false))
        set(value) = prefs.edit().putString("font_id", value.id).apply()

    /** Android's system colours follow the theme (see SystemPaletteSync). */
    var systemColours: Boolean
        get() = prefs.getBoolean("system_colours", false)
        set(value) = prefs.edit().putBoolean("system_colours", value).apply()

    /** Whether the system palette is currently ours, and the user's value to give back. */
    var systemColoursTookOver: Boolean
        get() = prefs.getBoolean("system_colours_took_over", false)
        set(value) = prefs.edit().putBoolean("system_colours_took_over", value).apply()

    var systemColoursPrevious: String?
        get() = prefs.getString("system_colours_previous", null)
        set(value) = prefs.edit().putString("system_colours_previous", value).apply()
}
