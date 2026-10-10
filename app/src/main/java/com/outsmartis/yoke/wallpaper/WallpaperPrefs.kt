package com.outsmartis.yoke.wallpaper

import android.content.Context

/** The wallpaper choice, in its own file apart from the launcher's Prefs. */
class WallpaperPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yoke.wallpaper", Context.MODE_PRIVATE)

    var choice: WallpaperChoice
        get() = WallpaperChoice.decode(prefs.getString("choice", null))
        set(value) = prefs.edit().putString("choice", value.encode()).apply()

    /**
     * False until the user applied a wallpaper from the Wallpaper screen. Until then the
     * default "Theme colour" is only a label: Yoke never overwrites the system wallpaper by itself.
     */
    var applied: Boolean
        get() = prefs.getBoolean("applied", false)
        set(value) = prefs.edit().putBoolean("applied", value).apply()

    /** "colour:target" last pushed to WallpaperManager, so an unchanged theme is not re-applied. */
    var lastSolid: String?
        get() = prefs.getString("last_solid", null)
        set(value) = prefs.edit().putString("last_solid", value).apply()

    /** The choice when the home screen should show the system wallpaper (an image was applied), else null. */
    val imageChoice: WallpaperChoice? get() = choice.takeIf { applied && it.showsImage }

    /** Part of the theme signature: a change recreates the home screen. */
    val windowSignature: String
        get() {
            return imageChoice?.let { "wp:${it.dim.name}" } ?: "wp:none"
        }
}
