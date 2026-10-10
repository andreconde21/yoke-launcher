package com.outsmartis.yoke.omarchy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Settings > Look > Omarchy style. Its own file; listed in YokeBackup.FILES. Everything is off by default. */
class OmarchyStylePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var toastEnabled: Boolean
        get() = prefs.getBoolean(TOAST, false)
        set(value) = prefs.edit { putBoolean(TOAST, value) }

    var toastLong: Boolean
        get() = prefs.getBoolean(TOAST_LONG, false)
        set(value) = prefs.edit { putBoolean(TOAST_LONG, value) }

    var dotsEnabled: Boolean
        get() = prefs.getBoolean(DOTS, false)
        set(value) = prefs.edit { putBoolean(DOTS, value) }

    var dotsStyle: DotStyle
        get() = DotStyle.fromId(prefs.getString(DOTS_STYLE, null))
        set(value) = prefs.edit { putString(DOTS_STYLE, value.id) }

    var dotsPosition: Edge
        get() = Edge.fromId(prefs.getString(DOTS_POSITION, null))
        set(value) = prefs.edit { putString(DOTS_POSITION, value.id) }

    /** How many of the features are switched on, for the Settings row. */
    fun enabledCount(): Int = listOf(toastEnabled, dotsEnabled).count { it }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "yoke.omarchy"
        private const val TOAST = "toast"
        private const val TOAST_LONG = "toast_long"
        private const val DOTS = "dots"
        private const val DOTS_STYLE = "dots_style"
        private const val DOTS_POSITION = "dots_position"
    }
}
