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

    enum class Status { Off, Wallpaper, WallpaperSystem, Yoke, NeedsPermission }

    fun level(context: Context) = WallpaperPrefs(context).lockLevel

    /** Whether the picker may select [level] right now. */
    fun available(context: Context, level: LockScreenLevel): Boolean = when (level) {
        LockScreenLevel.OFF, LockScreenLevel.WALLPAPER -> true
        LockScreenLevel.WALLPAPER_SYSTEM -> systemAvailable(context)
        LockScreenLevel.YOKE -> true // needs accessibility, but is chosen first and then asks for it
    }

    fun systemAvailable(context: Context) =
        SystemPaletteSync.supported() && GrayscaleController.hasPermission(context)

    fun status(context: Context): Status = when (level(context)) {
        LockScreenLevel.OFF -> Status.Off
        LockScreenLevel.WALLPAPER -> Status.Wallpaper
        LockScreenLevel.WALLPAPER_SYSTEM -> if (systemAvailable(context)) Status.WallpaperSystem else Status.NeedsPermission
        LockScreenLevel.YOKE -> Status.Yoke
    }

    /**
     * Saves [level] and applies it. Returns true when the level wants a wallpaper but the user has not
     * applied a Yoke wallpaper yet (nothing is written then; the caller should say so).
     */
    fun setLevel(context: Context, level: LockScreenLevel): Boolean {
        val app = context.applicationContext
        val prefs = WallpaperPrefs(app)
        val before = prefs.lockLevel
        prefs.lockLevel = level
        syncSystem(app, level)
        com.outsmartis.yoke.lockscreen.LockScreenController.sync(app) // Yoke level starts the service, any other stops it
        if (LockScreenLogic.shouldReapply(before, level, prefs.choice.target, prefs.applied)) {
            // Choosing a level that includes the wallpaper is the user's own act: the lock screen is Yoke's again
            val claims = LockScreenLogic.claimsLock(level)
            if (claims) prefs.lockReleased = false
            if (WallpaperApplier.settleOwnership(app, prefs, claimLock = claims)) WallpaperApplier.reapply(app, auto = true)
            else prefs.applied = false // set outside Yoke since: do not overwrite it
        }
        return LockScreenLogic.needsWallpaper(level, prefs.applied)
    }

    /** Call when the user flips "System colours follow theme" themselves: the level no longer owns that switch. */
    fun userChangedColours(context: Context) {
        WallpaperPrefs(context.applicationContext).lockForcedColours = false
    }

    /** Call when the theme or the permission may have changed (the theme path already runs SystemPaletteSync). */
    fun sync(context: Context) {
        val app = context.applicationContext
        syncSystem(app, level(app))
        com.outsmartis.yoke.lockscreen.LockScreenController.sync(app)
    }

    private fun syncSystem(app: Context, level: LockScreenLevel) {
        val prefs = WallpaperPrefs(app)
        val themePrefs = ThemePrefs(app)
        val canWrite = SystemPaletteSync.supported() && GrayscaleController.hasPermission(app)
        when (LockScreenLogic.colourAction(level, themePrefs.systemColours, prefs.lockForcedColours, canWrite)) {
            LockScreenLogic.ColourAction.FORCE_ON -> {
                prefs.lockForcedColours = true
                SystemPaletteSync.setEnabled(app, true)
            }
            LockScreenLogic.ColourAction.RESTORE -> {
                prefs.lockForcedColours = false
                SystemPaletteSync.setEnabled(app, false)
            }
            LockScreenLogic.ColourAction.NONE -> Unit
        }
    }
}
