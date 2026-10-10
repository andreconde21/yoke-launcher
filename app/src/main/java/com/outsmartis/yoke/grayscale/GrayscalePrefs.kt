package com.outsmartis.yoke.grayscale

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Smart grayscale settings, in the same prefs file as the rest of Yoke. */
class GrayscalePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("com.outsmartis.yoke", 0)

    var featureOn: Boolean
        get() = prefs.getBoolean(FEATURE_ON, false)
        set(value) = prefs.edit { putBoolean(FEATURE_ON, value) }

    var pausedUntil: Long
        get() = prefs.getLong(PAUSED_UNTIL, 0L)
        set(value) = prefs.edit { putLong(PAUSED_UNTIL, value) }

    var lastPackage: String?
        get() = prefs.getString(LAST_PACKAGE, null)
        set(value) = prefs.edit { putString(LAST_PACKAGE, value) }

    /** Apps most recently seen in front, newest first, for exempting helpers that have no launcher icon. */
    var recent: List<String>
        get() = prefs.getString(RECENT, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit { putString(RECENT, value.joinToString("\n")) }

    /** Exceptions; the defaults (Yoke itself) until the user saves a list. */
    var exceptions: Set<String>
        get() = PackageSetCodec.decode(prefs.getString(EXCEPTIONS, null)) ?: DEFAULT_EXCEPTIONS
        set(value) = prefs.edit { putString(EXCEPTIONS, PackageSetCodec.encode(value)) }

    /** The user's colour-correction state from before Yoke took over; null when Yoke has not. */
    var previous: DaltonizerState?
        get() {
            if (!prefs.getBoolean(TAKEN_OVER, false)) return null
            val enabled = prefs.getBoolean(PREV_ENABLED, false)
            val mode = prefs.getInt(PREV_MODE, NO_MODE).takeIf { it != NO_MODE }
            return DaltonizerState(enabled, mode)
        }
        set(value) = prefs.edit {
            if (value == null) {
                putBoolean(TAKEN_OVER, false)
            } else {
                putBoolean(TAKEN_OVER, true)
                putBoolean(PREV_ENABLED, value.enabled)
                putInt(PREV_MODE, value.mode ?: NO_MODE)
            }
        }

    companion object {
        private const val FEATURE_ON = "GRAYSCALE_ON"
        private const val PAUSED_UNTIL = "GRAYSCALE_PAUSED_UNTIL"
        private const val LAST_PACKAGE = "GRAYSCALE_LAST_PACKAGE"
        private const val EXCEPTIONS = "GRAYSCALE_EXCEPTIONS"
        private const val RECENT = "GRAYSCALE_RECENT"
        private const val TAKEN_OVER = "GRAYSCALE_TAKEN_OVER"
        private const val PREV_ENABLED = "GRAYSCALE_PREV_ENABLED"
        private const val PREV_MODE = "GRAYSCALE_PREV_MODE"
        private const val NO_MODE = Int.MIN_VALUE

        val DEFAULT_EXCEPTIONS = setOf("com.outsmartis.yoke", "com.outsmartis.yoke.debug")
    }
}
