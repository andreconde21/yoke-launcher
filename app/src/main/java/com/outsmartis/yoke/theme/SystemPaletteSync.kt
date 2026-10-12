package com.outsmartis.yoke.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.outsmartis.yoke.grayscale.GrayscaleController

/**
 * Optional: Android's own colours (Quick Settings, Settings, Material You apps) follow the
 * Yoke theme's accent. Writing the secure setting needs WRITE_SECURE_SETTINGS, granted once
 * with Shizuku or adb (the same grant as Smart grayscale); Shizuku need not keep running.
 * The user's own value is kept and written back when this is turned off or System default is picked.
 */
object SystemPaletteSync {

    enum class Status { Off, On, NeedsPermission, Unsupported }

    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun status(context: Context): Status = when {
        !supported() -> Status.Unsupported
        !ThemePrefs(context).systemColours -> Status.Off
        !GrayscaleController.hasPermission(context) -> Status.NeedsPermission
        else -> Status.On
    }

    fun setEnabled(context: Context, on: Boolean) {
        ThemePrefs(context).systemColours = on
        if (on) sync(context) else restore(context)
    }

    /** Call whenever the theme may have changed. Writes only when the seed differs. */
    fun sync(context: Context) {
        val app = context.applicationContext
        if (status(app) != Status.On) return
        val prefs = ThemePrefs(app)
        val theme = ThemeStore.current(app)
        if (theme == null) {
            restore(app)
            return
        }
        try {
            val current = Settings.Secure.getString(app.contentResolver, SystemPalette.KEY)
            if (!prefs.systemColoursTookOver) {
                prefs.systemColoursPrevious = current
                prefs.systemColoursTookOver = true
            }
            if (SystemPalette.seedOf(current) == theme.accent or (0xFF shl 24)) return
            Settings.Secure.putString(
                app.contentResolver, SystemPalette.KEY,
                SystemPalette.build(current, theme.accent, System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Gives the user's own palette back, if we replaced it. */
    fun restore(context: Context) {
        val app = context.applicationContext
        val prefs = ThemePrefs(app)
        if (!prefs.systemColoursTookOver || !GrayscaleController.hasPermission(app)) return
        try {
            Settings.Secure.putString(app.contentResolver, SystemPalette.KEY, prefs.systemColoursPrevious)
            prefs.systemColoursTookOver = false
            prefs.systemColoursPrevious = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
