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

    /** How many of the features are switched on, for the Settings row. */
    fun enabledCount(): Int = listOf(toastEnabled).count { it }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "yoke.omarchy"
        private const val TOAST = "toast"
        private const val TOAST_LONG = "toast_long"
    }
}
