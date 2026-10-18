package com.outsmartis.yoke.wallpaper

import android.content.Context
import com.outsmartis.yoke.grayscale.GrayscaleController
import com.outsmartis.yoke.theme.SystemPaletteSync
import com.outsmartis.yoke.theme.ThemePrefs

/**
 * Applies the "Lock screen" level (see [LockScreenLevel]).
 *
 * Wallpaper: [WallpaperApplier] reads the level when it picks the target, so every theme change
 * that re-colours the wallpaper also re-colours the lock screen.
 *
 * System: the lock clock and the rest of the system lock screen take their colours from the
 * system palette (Monet), which SystemPaletteSync already writes from the theme's accent.
 * This level switches that on and switches it off again only if this level was what turned it on,
 * so the user's own "System colours follow theme" choice and their previous palette survive.
 * No other lock-screen secure setting is written: none is well enough understood across ROMs
 * (see the Decisions on YOK-036).
 */
object LockScreenSync {

    /** Flip to true when the Yoke lock screen of YOK-037 exists. */
    const val YOKE_AVAILABLE = false

    enum class Status { Off, Wallpaper, WallpaperSystem, Yoke, NeedsPermission }

    fun level(context: Context) = WallpaperPrefs(context).lockLevel

    /** Whether the picker may select [level] right now. */
    fun available(context: Context, level: LockScreenLevel): Boolean = when (level) {
        LockScreenLevel.OFF, LockScreenLevel.WALLPAPER -> true
        LockScreenLevel.WALLPAPER_SYSTEM -> systemAvailable(context)
        LockScreenLevel.YOKE -> YOKE_AVAILABLE
    }

    fun systemAvailable(context: Context) =
        SystemPaletteSync.supported() && GrayscaleController.hasPermission(context)

    fun status(context: Context): Status = when (level(context)) {
        LockScreenLevel.OFF -> Status.Off
        LockScreenLevel.WALLPAPER -> Status.Wallpaper
        LockScreenLevel.WALLPAPER_SYSTEM -> if (systemAvailable(context)) Status.WallpaperSystem else Status.NeedsPermission
        LockScreenLevel.YOKE -> Status.Yoke
    }

    fun setLevel(context: Context, level: LockScreenLevel) {
        val app = context.applicationContext
        val prefs = WallpaperPrefs(app)
        val before = prefs.lockLevel
        prefs.lockLevel = level
        syncSystem(app, level)
        // Needs a (re)apply when the lock screen was not covered before or the target just widened.
        val widened = level.effectiveTarget(prefs.choice.target) != before.effectiveTarget(prefs.choice.target)
        if (level.includesWallpaper && (!before.includesWallpaper || widened)) WallpaperApplier.reapply(app)
    }

    /** Call when the theme or the permission may have changed (the theme path already runs SystemPaletteSync). */
    fun sync(context: Context) {
        val app = context.applicationContext
        syncSystem(app, level(app))
    }

    private fun syncSystem(app: Context, level: LockScreenLevel) {
        val prefs = WallpaperPrefs(app)
        val themePrefs = ThemePrefs(app)
        if (level.includesSystem) {
            if (!SystemPaletteSync.supported() || !GrayscaleController.hasPermission(app)) return
            if (!themePrefs.systemColours) {
                prefs.lockForcedColours = true
                SystemPaletteSync.setEnabled(app, true)
            }
        } else if (prefs.lockForcedColours) {
            prefs.lockForcedColours = false
            SystemPaletteSync.setEnabled(app, false)
        }
    }
}
