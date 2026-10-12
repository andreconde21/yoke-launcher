package com.outsmartis.yoke.limits

import android.content.Context

/** Changes to the limited set, keeping hard pause consistent with it. */
object LimitedApps {

    fun setLimited(context: Context, pkg: String, on: Boolean) {
        val prefs = LimitPrefs(context)
        if (on) {
            prefs.limited = prefs.limited + pkg
            return
        }
        prefs.limited = prefs.limited - pkg
        if (pkg in prefs.hard) setHard(context, pkg, false)
        if (prefs.passPackage == pkg) prefs.passPackage = null
    }

    fun setHard(context: Context, pkg: String, on: Boolean) {
        val prefs = LimitPrefs(context)
        if (on) {
            prefs.hard = prefs.hard + pkg
            HardPause.applyAll(context)
        } else {
            prefs.hard = prefs.hard - pkg
            HardPause.release(context, listOf(pkg))
        }
    }
}
