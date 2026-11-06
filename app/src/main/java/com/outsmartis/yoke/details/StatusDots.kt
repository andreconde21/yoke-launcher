package com.outsmartis.yoke.details

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Status dots on home: one small "● 2" per picked app that serves the launcher details contract
 * (Vigia for Uptime Kuma, Conductore for agents). Tap opens that app's details sheet.
 * Off until the user picks an app: an optional integration, nothing changes for anyone else.
 */
class StatusDotPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("com.outsmartis.yoke", 0)

    /** The apps that get a dot, in the order they were picked. */
    var apps: List<String>
        get() = prefs.getString(APPS, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit { putString(APPS, value.distinct().joinToString(SEP)) }

    /** Hide a dot while its app reports nothing wrong. */
    var onlyWhenSomethingIsWrong: Boolean
        get() = prefs.getBoolean(QUIET, false)
        set(value) = prefs.edit { putBoolean(QUIET, value) }

    companion object {
        private const val APPS = "STATUS_DOTS_APPS"
        private const val QUIET = "STATUS_DOTS_QUIET"
        private const val SEP = "|"
    }
}

enum class DotLevel { Ok, Warn, Alert, Stale }

/** What one dot shows: the colour and the number (null = no number). */
data class Dot(val level: DotLevel, val count: Int?)

object StatusDotLogic {

    /**
     * The provider's own `badge_level` / `badge_count` (contract v4) when it sends them; otherwise
     * Conductore-style: a number and the alert colour when something needs you, a calm dot when not.
     * A provider that is not monitoring anything shows the stale dot.
     */
    fun dot(summary: DetailsSummary?): Dot {
        if (summary == null) return Dot(DotLevel.Stale, null)
        val count = summary.badgeCount ?: summary.attentionCount
        val level = when (summary.badgeLevel) {
            "alert" -> DotLevel.Alert
            "warn" -> DotLevel.Warn
            "ok" -> DotLevel.Ok
            "stale" -> DotLevel.Stale
            else -> when {
                !summary.monitoring -> DotLevel.Stale
                count > 0 -> DotLevel.Alert
                else -> DotLevel.Ok
            }
        }
        return Dot(level, count.takeIf { it > 0 })
    }

    /** "● 2" or "●". */
    fun text(dot: Dot): String = if (dot.count == null) DOT else "$DOT ${dot.count}"

    /** Quiet mode hides calm dots; stale stays visible, it means "I can't tell you". */
    fun visible(dot: Dot, quiet: Boolean): Boolean = !quiet || dot.level != DotLevel.Ok

    const val DOT = "●"
}
