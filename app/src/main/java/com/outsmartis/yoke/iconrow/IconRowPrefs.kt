package com.outsmartis.yoke.iconrow

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Icon row settings. Its own file; listed in YokeBackup.FILES. */
class IconRowPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit { putBoolean(ENABLED, value) }

    var slots: List<IconSlot>
        get() = IconSlots.decode(prefs.getString(SLOTS, null))
        set(value) = prefs.edit { putString(SLOTS, IconSlots.encode(value)) }

    var style: IconStyle
        get() = IconStyle.fromId(prefs.getString(STYLE, null))
        set(value) = prefs.edit { putString(STYLE, value.id) }

    var labels: Boolean
        get() = prefs.getBoolean(LABELS, false)
        set(value) = prefs.edit { putBoolean(LABELS, value) }

    var size: IconSize
        get() = IconSize.fromId(prefs.getString(SIZE, null))
        set(value) = prefs.edit { putString(SIZE, value.id) }

    var position: IconPosition
        get() = IconPosition.fromId(prefs.getString(POSITION, null))
        set(value) = prefs.edit { putString(POSITION, value.id) }

    fun toggle() { enabled = !enabled }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "yoke.iconrow"
        private const val ENABLED = "enabled"
        private const val SLOTS = "slots"
        private const val STYLE = "style"
        private const val LABELS = "labels"
        private const val SIZE = "size"
        private const val POSITION = "position"
    }
}
