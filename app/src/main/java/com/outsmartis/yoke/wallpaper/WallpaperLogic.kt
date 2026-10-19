package com.outsmartis.yoke.wallpaper

import com.outsmartis.yoke.theme.OMARCHY_THEMES
import java.net.URLEncoder

/** Integer rectangle (right and bottom exclusive); android.graphics.Rect is not usable in unit tests. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** What a theme change does to a wallpaper Yoke set from an Omarchy background. */
sealed class ThemeBackground {
    /** Nothing to do: not an Omarchy wallpaper Yoke applied, or already that theme's. */
    object Keep : ThemeBackground()

    /** The new theme ships no background: fall back to its colour. */
    object Colour : ThemeBackground()

    data class Switch(val themeId: String, val file: String) : ThemeBackground()
}

/** Pure helpers for the Wallpaper feature. */
object WallpaperLogic {

    /**
     * May Yoke re-apply by itself over the wallpaper whose id is [current], given the id [stored] after
     * its own last write? Ids are positive; WallpaperManager reports -1 for "no such wallpaper" and
     * also when it cannot say, and that unknown is treated as "not ours": never overwrite on doubt.
     * A [stored] id of 0 comes from before ids were recorded; it is trusted once and adopted.
     */
    fun mayOverwrite(stored: Int, current: Int): Boolean = when {
        current <= 0 -> false
        stored <= 0 -> true
        else -> stored == current
    }

    /**
     * The id to remember for a wallpaper whose [stored] id is 0 (recorded before ids existed): trust what
     * is there now, once, right away; [current] unknown (0) or a system id of -1 is not trusted.
     */
    fun adoptId(stored: Int, current: Int, allowShared: Boolean): Int = when {
        stored != 0 -> stored
        current > 0 -> current
        current < 0 && allowShared -> current
        else -> 0
    }

    /** Which of the two screens still carry what Yoke wrote; a screen Yoke does not write counts as owned. */
    data class Ownership(val system: Boolean, val lock: Boolean)

    /**
     * Per screen: does the wallpaper Yoke writes there ([writesSystem], [writesLock]) still carry the id it
     * stored? When one wallpaper serves both screens Android reports lock id -1: the lock then follows
     * the system wallpaper, so it is Yoke's exactly while the system one is. A lock-only write has no
     * system id to lean on, so an unknown current lock id there means someone else replaced it.
     *
     * Stored ids: 0 is "never recorded" (an install from before ids; [adoptId] settles it), -1 is
     * "shared with the system wallpaper" and is NOT legacy: a lock wallpaper that has since appeared
     * (current > 0) is Yoke's only when it is the very wallpaper that was shared (the home screen was
     * replaced by another app, the lock kept the old image); otherwise someone else set it.
     */
    fun ownership(
        writesSystem: Boolean, writesLock: Boolean,
        storedSystem: Int, currentSystem: Int, storedLock: Int, currentLock: Int,
    ): Ownership {
        val system = !writesSystem || mayOverwrite(storedSystem, currentSystem)
        val lock = when {
            !writesLock -> true
            currentLock > 0 -> storedLock == 0 || storedLock == currentLock ||
                (storedLock < 0 && storedSystem > 0 && currentLock == storedSystem)
            else -> writesSystem && system // follows the system wallpaper
        }
        return Ownership(system, lock)
    }

    /**
     * What an automatic re-apply may treat as Yoke's, per screen: [ownership] plus "never recorded" (id 0)
     * is NOT trusted any more (only the one-off [adoptLegacy] migration trusts, once). [claimLock] is the
     * user's own choice of a lock level that includes the wallpaper: the lock screen is Yoke's to write.
     */
    fun settleOwned(
        writesSystem: Boolean, writesLock: Boolean,
        storedSystem: Int, currentSystem: Int, storedLock: Int, currentLock: Int, claimLock: Boolean = false,
    ): Ownership {
        val o = ownership(writesSystem, writesLock, storedSystem, currentSystem, storedLock, currentLock)
        return Ownership(
            system = !writesSystem || (storedSystem != 0 && o.system),
            lock = !writesLock || claimLock || (storedLock != 0 && o.lock),
        )
    }

    /**
     * The one-off upgrade migration: a wallpaper applied before ids were recorded is adopted only when it is
     * provably Yoke's own solid theme colour (the colour Yoke last wrote, [expected], matches what the
     * system reports, [actual], within quantisation). An Omarchy or picked image cannot be verified: released.
     */
    fun adoptLegacy(source: WallpaperSource, expected: Int?, actual: Int?): Boolean =
        source == WallpaperSource.THEME_COLOUR && expected != null && actual != null && colourClose(expected, actual)

    fun colourClose(a: Int, b: Int, tolerance: Int = 24): Boolean =
        listOf(16, 8, 0).all { shift -> Math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= tolerance }

    /** Both screens Yoke writes are still its own. */
    fun ownsTarget(
        writesSystem: Boolean, writesLock: Boolean,
        storedSystem: Int, currentSystem: Int, storedLock: Int, currentLock: Int,
    ): Boolean = ownership(writesSystem, writesLock, storedSystem, currentSystem, storedLock, currentLock)
        .let { it.system && it.lock }

    /** The screens Yoke may still write: [target] minus a screen that was replaced by someone else; null for none. */
    fun screensToWrite(target: WallpaperTarget, systemReleased: Boolean, lockReleased: Boolean): WallpaperTarget? {
        val system = target != WallpaperTarget.LOCK && !systemReleased
        val lock = target != WallpaperTarget.HOME && !lockReleased
        return when {
            system && lock -> WallpaperTarget.BOTH
            system -> WallpaperTarget.HOME
            lock -> WallpaperTarget.LOCK
            else -> null
        }
    }

    /**
     * Runs [block] up to [attempts] times, waiting [delayMs] between failures (null means failed). An
     * interrupt (the executor being shut down) ends it quietly with null and keeps the interrupt flag set.
     */
    fun <T : Any> retryQuietly(attempts: Int, delayMs: Long, sleeper: (Long) -> Unit = { Thread.sleep(it) }, block: () -> T?): T? {
        repeat(attempts) { attempt ->
            block()?.let { return it }
            if (attempt < attempts - 1) {
                try {
                    sleeper(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }

    /**
     * Only a wallpaper Yoke itself applied from an Omarchy background follows the theme; anything the
     * user set outside Yoke ([applied] false) is never touched. The new theme's first background is used
     * (file names like omarchy.webp repeat across themes with different images).
     */
    fun backgroundForTheme(choice: WallpaperChoice, applied: Boolean, newThemeId: String?): ThemeBackground {
        if (!applied || choice.source != WallpaperSource.OMARCHY) return ThemeBackground.Keep
        if (newThemeId != null && newThemeId == choice.omarchyTheme) return ThemeBackground.Keep
        val files = newThemeId?.let(::backgroundsFor).orEmpty()
        val file = files.firstOrNull() ?: return ThemeBackground.Colour
        return ThemeBackground.Switch(newThemeId!!, file)
    }

    /** The Omarchy commit OmarchyThemes.kt was generated from; background files are fetched at the same one. */
    const val OMARCHY_COMMIT = "c3e67f5d405047765d47a9336a3df115a7beecfb"
    private const val RAW_BASE = "https://raw.githubusercontent.com/basecamp/omarchy"

    private fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun backgroundUrl(themeId: String, file: String, commit: String = OMARCHY_COMMIT): String =
        "$RAW_BASE/$commit/themes/${segment(themeId)}/backgrounds/${segment(file)}"

    /** Background file names Omarchy ships for [themeId] (empty for unknown themes and ones without any). */
    fun backgroundsFor(themeId: String): List<String> =
        OMARCHY_THEMES.firstOrNull { it.id == themeId }?.backgrounds.orEmpty()

    /** A file name that is safe to use inside the cache directory. */
    fun cacheName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "_" }

    /** The largest centred rectangle of the source with the destination's aspect ratio. */
    fun cropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): CropRect {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0) { "sizes must be positive" }
        // srcW/srcH > dstW/dstH without floating point
        return if (srcW.toLong() * dstH > dstW.toLong() * srcH) {
            val w = (srcH.toLong() * dstW / dstH).toInt().coerceIn(1, srcW)
            val left = (srcW - w) / 2
            CropRect(left, 0, left + w, srcH)
        } else {
            val h = (srcW.toLong() * dstH / dstW).toInt().coerceIn(1, srcH)
            val top = (srcH - h) / 2
            CropRect(0, top, srcW, top + h)
        }
    }

    /** Power-of-two BitmapFactory sample size that keeps the decoded image at least [reqW] x [reqH]. */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var size = 1
        while (srcW / (size * 2) >= reqW && srcH / (size * 2) >= reqH) size *= 2
        return size
    }
}
