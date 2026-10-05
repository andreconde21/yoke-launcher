package com.outsmartis.yoke.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.outsmartis.yoke.grayscale.PackageSetCodec

/** "Notifications on home" settings, in the same prefs file as the rest of Yoke. */
class NotificationPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("com.outsmartis.yoke", 0)

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit { putBoolean(ENABLED, value) }

    /** The apps the user picked. Empty by default: nothing shows until he chooses. */
    var allowed: Set<String>
        get() = PackageSetCodec.decode(prefs.getString(ALLOWED, null)) ?: emptySet()
        set(value) = prefs.edit { putString(ALLOWED, PackageSetCodec.encode(value)) }

    var hideTextApps: Set<String>
        get() = PackageSetCodec.decode(prefs.getString(HIDE_TEXT_APPS, null)) ?: emptySet()
        set(value) = prefs.edit { putString(HIDE_TEXT_APPS, PackageSetCodec.encode(value)) }

    var maxLines: Int
        get() = NotificationLogic.normalizeMax(prefs.getInt(MAX_LINES, NotificationLogic.MAX_LINES_SMALL))
        set(value) = prefs.edit { putInt(MAX_LINES, NotificationLogic.normalizeMax(value)) }

    var showText: Boolean
        get() = prefs.getBoolean(SHOW_TEXT, true)
        set(value) = prefs.edit { putBoolean(SHOW_TEXT, value) }

    var hideDuringDnd: Boolean
        get() = prefs.getBoolean(HIDE_DND, true)
        set(value) = prefs.edit { putBoolean(HIDE_DND, value) }

    var countsOnApps: Boolean
        get() = prefs.getBoolean(COUNTS, true)
        set(value) = prefs.edit { putBoolean(COUNTS, value) }

    var nowPlaying: Boolean
        get() = prefs.getBoolean(NOW_PLAYING, false)
        set(value) = prefs.edit { putBoolean(NOW_PLAYING, value) }

    companion object {
        private const val ENABLED = "NOTES_HOME_ON"
        private const val ALLOWED = "NOTES_HOME_ALLOWED"
        private const val HIDE_TEXT_APPS = "NOTES_HOME_HIDE_TEXT_APPS"
        private const val MAX_LINES = "NOTES_HOME_MAX_LINES"
        private const val SHOW_TEXT = "NOTES_HOME_SHOW_TEXT"
        private const val HIDE_DND = "NOTES_HOME_HIDE_DND"
        private const val COUNTS = "NOTES_HOME_COUNTS"
        private const val NOW_PLAYING = "NOTES_HOME_NOW_PLAYING"
    }
}
