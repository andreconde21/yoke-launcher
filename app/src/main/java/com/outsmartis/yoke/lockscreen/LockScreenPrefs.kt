package com.outsmartis.yoke.lockscreen

import android.content.Context
import com.outsmartis.yoke.wallpaper.LockScreenLevel
import com.outsmartis.yoke.wallpaper.WallpaperPrefs

/**
 * Yoke lock screen settings. There is no switch of its own: the one source of truth is the
 * "Lock screen" level (YOK-036), saved with the rest of Yoke's prefs so backups carry it.
 */
class LockScreenPrefs(context: Context) {

    private val wallpaper = WallpaperPrefs(context.applicationContext)

    /** On exactly when the level is [LockScreenLevel.YOKE]. Change it with [com.outsmartis.yoke.wallpaper.LockScreenSync.setLevel]. */
    val enabled: Boolean
        get() = wallpaper.lockLevel == LockScreenLevel.YOKE
}
