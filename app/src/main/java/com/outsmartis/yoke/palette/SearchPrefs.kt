package com.outsmartis.yoke.palette

import android.content.Context

/** Which extra sources the palette's plain-text search draws on. */
class SearchPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yoke.search", Context.MODE_PRIVATE)

    /** Cockpit cards; effective only when a vault is picked. */
    var cards: Boolean
        get() = prefs.getBoolean("cards", true)
        set(value) = prefs.edit().putBoolean("cards", value).apply()

    var notes: Boolean
        get() = prefs.getBoolean("notes", false)
        set(value) = prefs.edit().putBoolean("notes", value).apply()

    /** Needs READ_CONTACTS, asked for when this is switched on. */
    var contacts: Boolean
        get() = prefs.getBoolean("contacts", false)
        set(value) = prefs.edit().putBoolean("contacts", value).apply()

    var settings: Boolean
        get() = prefs.getBoolean("settings", true)
        set(value) = prefs.edit().putBoolean("settings", value).apply()
}
