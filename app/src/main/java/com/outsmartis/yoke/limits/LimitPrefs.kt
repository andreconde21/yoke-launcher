package com.outsmartis.yoke.limits

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/** Limited apps, their hard-pause choice and today's open counts. Counts stay on the phone. */
class LimitPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var limited: Set<String>
        get() = prefs.getStringSet(LIMITED, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(LIMITED, value) }

    /** Limited apps kept suspended by Android between opens (level C, needs Shizuku). */
    var hard: Set<String>
        get() = prefs.getStringSet(HARD, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(HARD, value) }

    fun isLimited(pkg: String) = pkg in limited

    fun opensToday(pkg: String, now: Long = System.currentTimeMillis()): Int {
        if (prefs.getString(DAY, null) != LimitPolicy.dayKey(now)) return 0
        return counts().optInt(pkg, 0)
    }

    fun recordOpen(pkg: String, now: Long = System.currentTimeMillis()) {
        val today = LimitPolicy.dayKey(now)
        val counts = if (prefs.getString(DAY, null) == today) counts() else JSONObject()
        counts.put(pkg, counts.optInt(pkg, 0) + 1)
        prefs.edit {
            putString(DAY, today)
            putString(OPENS, counts.toString())
        }
    }

    /** The app the user let through after the pause, until they leave it. */
    var passPackage: String?
        get() = prefs.getString(PASS, null)
        set(value) = prefs.edit { putString(PASS, value); putLong(PASS_AT, System.currentTimeMillis()) }

    val passAt: Long get() = prefs.getLong(PASS_AT, 0L)

    private fun counts(): JSONObject =
        prefs.getString(OPENS, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    companion object {
        const val FILE = "yoke.limits"
        private const val LIMITED = "limited"
        private const val HARD = "hard"
        const val DAY = "day"
        const val OPENS = "opens"
        const val PASS = "pass_pkg"
        const val PASS_AT = "pass_at"
    }
}
