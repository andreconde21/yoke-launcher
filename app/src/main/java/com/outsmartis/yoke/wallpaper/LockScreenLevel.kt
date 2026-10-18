package com.outsmartis.yoke.wallpaper

/**
 * The one "Lock screen" setting. Each level includes the one before it.
 * It does not replace the Wallpaper screen's "Apply to": it widens it, so any level above
 * [OFF] makes sure the lock screen is covered too (Home only becomes Both).
 */
enum class LockScreenLevel {
    OFF,

    /** The lock screen gets the same wallpaper as the home screen, re-applied on every theme change. */
    WALLPAPER,

    /** Wallpaper plus the system's own lock screen look (needs WRITE_SECURE_SETTINGS). */
    WALLPAPER_SYSTEM,

    /** Yoke's own lock screen (YOK-037). Not selectable until that lands, see [LockScreenSync.YOKE_AVAILABLE]. */
    YOKE;

    val includesWallpaper: Boolean get() = this != OFF
    val includesSystem: Boolean get() = this == WALLPAPER_SYSTEM

    /** The target to really write to: the saved [target], with the lock screen added when this level wants it. */
    fun effectiveTarget(target: WallpaperTarget): WallpaperTarget =
        if (includesWallpaper && target == WallpaperTarget.HOME) WallpaperTarget.BOTH else target

    companion object {
        /** Unknown or missing values (older backups, a future level) fall back to [OFF]. */
        fun decode(raw: String?): LockScreenLevel = entries.firstOrNull { it.name == raw } ?: OFF
    }
}
