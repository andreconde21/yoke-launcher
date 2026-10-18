package com.outsmartis.yoke.lockscreen

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Yoke lock screen settings, in the same prefs file as the rest of Yoke (so backups carry them). */
class LockScreenPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("com.outsmartis.yoke", 0)

    /** Off by default; [LockScreenController.setEnabled] is the way to change it (it also starts/stops the service). */
    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit { putBoolean(ENABLED, value) }

    companion object {
        private const val ENABLED = "LOCK_SCREEN_YOKE_ON"
    }
}
