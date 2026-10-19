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

    /** Yoke's own lock screen (YOK-037): the level is the on/off switch of its service. */
    YOKE;

    /** [YOKE] draws its own opaque screen, so it neither needs nor rewrites the system lock-screen wallpaper. */
    val includesWallpaper: Boolean get() = this == WALLPAPER || this == WALLPAPER_SYSTEM
    val includesSystem: Boolean get() = this == WALLPAPER_SYSTEM

    /** The target to really write to: the saved [target], with the lock screen added when this level wants it. */
    fun effectiveTarget(target: WallpaperTarget): WallpaperTarget =
        if (includesWallpaper && target == WallpaperTarget.HOME) WallpaperTarget.BOTH else target

    companion object {
        /** Unknown or missing values (older backups, a future level) fall back to [OFF]. */
        fun decode(raw: String?): LockScreenLevel = entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/** The decisions behind [LockScreenSync], kept free of Android so they can be unit-tested. */
object LockScreenLogic {

    enum class ColourAction { NONE, FORCE_ON, RESTORE }

    /**
     * What the System level does to "System colours follow theme".
     * [forced] is true when this level (not the user) switched the colours on earlier.
     */
    fun colourAction(level: LockScreenLevel, coloursOn: Boolean, forced: Boolean, canWrite: Boolean): ColourAction = when {
        level.includesSystem -> if (canWrite && !coloursOn) ColourAction.FORCE_ON else ColourAction.NONE
        forced -> ColourAction.RESTORE
        else -> ColourAction.NONE
    }

    /**
     * Whether changing the level from [before] to [after] should re-apply the wallpaper now.
     * Never when no Yoke wallpaper was applied: Yoke does not overwrite the system wallpaper by itself.
     */
    fun shouldReapply(before: LockScreenLevel, after: LockScreenLevel, target: WallpaperTarget, applied: Boolean): Boolean {
        if (!applied || !after.includesWallpaper) return false
        return !before.includesWallpaper || after.effectiveTarget(target) != before.effectiveTarget(target)
    }

    /** Picking a level that includes the wallpaper claims the lock screen for Yoke (an explicit user action). */
    fun claimsLock(level: LockScreenLevel): Boolean = level.includesWallpaper

    /** True when [level] wants the wallpaper but there is no Yoke wallpaper applied yet, so nothing re-colours. */
    fun needsWallpaper(level: LockScreenLevel, applied: Boolean) = level.includesWallpaper && !applied
}
