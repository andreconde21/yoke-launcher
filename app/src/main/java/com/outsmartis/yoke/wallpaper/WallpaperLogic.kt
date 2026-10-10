package com.outsmartis.yoke.wallpaper

import com.outsmartis.yoke.theme.OMARCHY_THEMES
import java.net.URLEncoder

/** Integer rectangle (right and bottom exclusive); android.graphics.Rect is not usable in unit tests. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Pure helpers for the Wallpaper feature. */
object WallpaperLogic {

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
