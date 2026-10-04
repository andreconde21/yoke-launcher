package com.outsmartis.yoke.theme

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.helper.isEinkDisplay

/**
 * The selected theme, resolved. System default is represented by null so the
 * existing OLauncher light/dark/wallpaper look is untouched.
 */
object ThemeStore {

    @Volatile
    private var cached: Resolved? = null

    /** [theme] is null for System default. [conductoreAvailable] says whether the provider answered. */
    class Resolved(val id: String, val theme: YokeTheme?, val conductoreAvailable: Boolean)

    fun resolved(context: Context): Resolved = cached ?: resolve(context.applicationContext).also { cached = it }

    /** The active theme, or null when System default (or an e-ink display) is in charge. */
    fun current(context: Context): YokeTheme? = resolved(context).theme

    fun invalidate() {
        cached = null
    }

    fun setThemeId(context: Context, id: String) {
        ThemePrefs(context).themeId = id
        invalidate()
    }

    /** Changes whenever the painted result changes; compare to know when to recreate. */
    fun signature(context: Context): String {
        val r = resolved(context)
        val t = r.theme ?: return r.id
        return "${r.id}:${t.background}:${t.foreground}:${t.accent}:${t.secondary}:${ThemePrefs(context).jetBrainsMono}"
    }

    /** Night mode the activity should run in: the theme's own mode, else the launcher's light/dark setting. */
    fun nightMode(context: Context, systemMode: Int): Int {
        val t = current(context) ?: return systemMode
        return if (t.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }

    /** Ids offered by the picker and the cycle; Conductore only while its provider answers. */
    fun cycleIds(context: Context): List<String> =
        ThemeCatalog.ids(conductore = resolved(context).conductoreAvailable)

    /** Selects the following theme (wrapping) and returns it; call [cycleNext] with an Activity to also repaint. */
    fun selectNext(context: Context): String {
        val next = ThemeCatalog.next(ThemePrefs(context).themeId, cycleIds(context))
        setThemeId(context, next)
        return next
    }

    /** For a "Next theme" gesture: select the next theme and repaint [activity]. */
    fun cycleNext(activity: Activity) {
        selectNext(activity)
        activity.recreate()
    }

    /**
     * Calls [onChange] on the main thread when Conductore's theme row changes,
     * for as long as the returned handle is not [Registration.unregister]ed.
     */
    fun observeConductore(context: Context, onChange: () -> Unit): Registration {
        val app = context.applicationContext
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val before = signature(app)
                invalidate()
                if (signature(app) != before) onChange()
            }
        }
        val registered = try {
            app.contentResolver.registerContentObserver(ConductoreTheme.URI, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        return Registration { if (registered) app.contentResolver.unregisterContentObserver(observer) }
    }

    fun interface Registration {
        fun unregister()
    }

    private fun resolve(context: Context): Resolved {
        val prefs = ThemePrefs(context)
        val read = ConductoreTheme.read(context)
        if (read is ConductoreTheme.Read.Available) prefs.conductoreLastKnown = read.raw
        val available = read is ConductoreTheme.Read.Available
        val id = prefs.themeId
        if (context.isEinkDisplay()) return Resolved(id, null, available)
        val theme = when (id) {
            YokeTheme.SYSTEM_ID -> null
            YokeTheme.PC_ID -> when (read) {
                is ConductoreTheme.Read.Available -> YokeTheme.from(read.theme)
                else -> ConductoreTheme.fromJson(prefs.conductoreLastKnown)
                    ?.let(ConductoreTheme::parse)?.let(YokeTheme::from)
            }
            else -> ThemeCatalog.omarchyTheme(id)
        }
        return Resolved(id, theme, available)
    }
}
