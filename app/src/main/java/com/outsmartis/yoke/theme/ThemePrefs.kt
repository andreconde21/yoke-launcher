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

    var jetBrainsMono: Boolean
        get() = prefs.getBoolean("font_jetbrains_mono", false)
        set(value) = prefs.edit().putBoolean("font_jetbrains_mono", value).apply()
}
