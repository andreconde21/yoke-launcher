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

    var topLineEnabled: Boolean
        get() = prefs.getBoolean(TOP_LINE, false)
        set(value) = prefs.edit { putBoolean(TOP_LINE, value) }

    var topLinePosition: Edge
        get() = Edge.fromId(prefs.getString(TOP_LINE_POSITION, null))
        set(value) = prefs.edit { putString(TOP_LINE_POSITION, value.id) }

    var topLineModules: List<TopModule>
        get() = TopLine.decode(prefs.getString(TOP_LINE_MODULES, null))
        set(value) = prefs.edit { putString(TOP_LINE_MODULES, TopLine.encode(value)) }

    var topLineClock: TopLineClock
        get() = TopLineClock.fromId(prefs.getString(TOP_LINE_CLOCK, null))
        set(value) = prefs.edit { putString(TOP_LINE_CLOCK, value.id) }

    /** Terminal mode; its parts below only count while it is on. */
    var terminalEnabled: Boolean
        get() = prefs.getBoolean(TERMINAL, false)
        set(value) = prefs.edit { putBoolean(TERMINAL, value) }

    var terminalPrompt: Boolean
        get() = prefs.getBoolean(TERMINAL_PROMPT, true)
        set(value) = prefs.edit { putBoolean(TERMINAL_PROMPT, value) }

    var terminalPromptText: String
        get() = TerminalLabel.prompt(prefs.getString(TERMINAL_PROMPT_TEXT, null))
        set(value) = prefs.edit { putString(TERMINAL_PROMPT_TEXT, TerminalLabel.prompt(value)) }

    var terminalLowercase: Boolean
        get() = prefs.getBoolean(TERMINAL_LOWERCASE, true)
        set(value) = prefs.edit { putBoolean(TERMINAL_LOWERCASE, value) }

    var terminalCursor: Boolean
        get() = prefs.getBoolean(TERMINAL_CURSOR, true)
        set(value) = prefs.edit { putBoolean(TERMINAL_CURSOR, value) }

    /** How many of the features are switched on, for the Settings row. */
    fun enabledCount(): Int = listOf(toastEnabled, dotsEnabled, topLineEnabled, terminalEnabled).count { it }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "yoke.omarchy"
        private const val TOAST = "toast"
        private const val TOAST_LONG = "toast_long"
        private const val DOTS = "dots"
        private const val DOTS_STYLE = "dots_style"
        private const val DOTS_POSITION = "dots_position"
        private const val TOP_LINE = "top_line"
        private const val TOP_LINE_POSITION = "top_line_position"
        private const val TOP_LINE_MODULES = "top_line_modules"
        private const val TOP_LINE_CLOCK = "top_line_clock"
        private const val TERMINAL = "terminal"
        private const val TERMINAL_PROMPT = "terminal_prompt"
        private const val TERMINAL_PROMPT_TEXT = "terminal_prompt_text"
        private const val TERMINAL_LOWERCASE = "terminal_lowercase"
        private const val TERMINAL_CURSOR = "terminal_cursor"
    }
}
