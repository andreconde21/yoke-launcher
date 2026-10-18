package com.outsmartis.yoke.askbar

import android.content.Context

/** The optional "open, type, or ask…" bar at the bottom of home. Off by default. */
class AskBarPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var agent: AskAgent
        get() = AskAgent.fromKey(prefs.getString("agent", null))
        set(value) = prefs.edit().putString("agent", value.key).apply()

    companion object {
        const val FILE = "yoke.askbar"
    }
}
