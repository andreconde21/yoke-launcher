package com.outsmartis.yoke.theme

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.wallpaper.WallpaperPrefs

/**
 * Paints the selected [YokeTheme] and font onto views at runtime. The XML
 * layouts keep their attr-based colours (they are the System default look);
 * for an Omarchy / Conductore theme this walks a view tree and overrides them,
 * so themes stay data and no per-theme styles are needed.
 *
 * Call [apply] on a fragment or activity root after inflating, on every
 * RecyclerView holder, and on dialog content. With System default and the
 * system font it does nothing.
 */
object ThemeApplier {

    private const val ROLE_PRIMARY = 1
    private const val ROLE_SECONDARY = 2

    /** Text at or below this alpha in the XML was a dimmed (secondary) text. */
    private const val SECONDARY_ALPHA_BELOW = 0xF0

    /**
     * [surface]: also paint [root]'s background (fragment roots, dialogs'
     * content is left alone because the window carries it).
     */
    fun apply(root: View, surface: Boolean = false) {
        val context = root.context
        val theme = ThemeStore.current(context)
        val typeface = fonts(context)
        if (theme == null && typeface == null) return
        // With an image wallpaper the window shows it; a painted root would hide it again.
        if (theme != null && surface && WallpaperPrefs(context).imageChoice == null) root.setBackgroundColor(theme.background)
        walk(root, theme, typeface)
    }

    private fun walk(view: View, theme: YokeTheme?, fonts: Fonts?) {
        if (view is TextView) paintText(view, theme, fonts)
        else if (view is ImageView && theme != null) view.imageTintList = ColorStateList.valueOf(theme.foreground)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), theme, fonts)
    }

    private fun paintText(tv: TextView, theme: YokeTheme?, fonts: Fonts?) {
        if (fonts != null) tv.typeface = if (fonts.allBold || tv.typeface?.isBold == true) fonts.bold else fonts.regular
        if (theme == null) return
        val role = (tv.getTag(R.id.theme_text_role) as? Int) ?: run {
            val alpha = (tv.textColors.defaultColor ushr 24) and 0xFF
            (if (alpha < SECONDARY_ALPHA_BELOW) ROLE_SECONDARY else ROLE_PRIMARY).also { tv.setTag(R.id.theme_text_role, it) }
        }
        tv.setTextColor(textColors(theme, role == ROLE_SECONDARY))
        // Over an image wallpaper with no dimming, a soft shadow keeps text readable
        val image = WallpaperPrefs(tv.context).imageChoice
        if (image != null && image.dim.alpha == 0f) {
            val d = tv.resources.displayMetrics.density
            tv.setShadowLayer(4 * d, 0f, 1 * d, 0xAA000000.toInt())
        } else tv.setShadowLayer(0f, 0f, 0f, 0)
        tv.highlightColor = (theme.accent and 0x00FFFFFF) or (0x55 shl 24)
        if (tv is EditText || tv.hint != null) tv.setHintTextColor(theme.secondary)
        if (tv is EditText && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tv.textCursorDrawable = GradientDrawable().apply {
                setColor(theme.accentText)
                setSize(tv.resources.displayMetrics.density.times(2).toInt().coerceAtLeast(2), 0)
            }
        }
    }

    private fun textColors(theme: YokeTheme, secondary: Boolean): ColorStateList {
        val highlighted = if (secondary) theme.foreground else theme.accentText
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_selected),
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(),
            ),
            intArrayOf(highlighted, highlighted, highlighted, if (secondary) theme.secondary else theme.foreground),
        )
    }

    /** Text colour for code that sets colours itself: the theme's, else the XML attr colour. */
    fun textColor(context: Context, secondary: Boolean): Int {
        val theme = ThemeStore.current(context)
        return when {
            theme == null -> context.getColorFromAttr(
                if (secondary) R.attr.primaryColorTrans50 else R.attr.primaryColor,
            )
            secondary -> theme.secondary
            else -> theme.foreground
        }
    }

    /**
     * Window-level theming. With the Wallpaper source "Theme colour" a solid
     * background is drawn instead of the wallpaper (the system wallpaper is set
     * to the same colour); with an image source the system wallpaper stays
     * visible under an optional dimming scrim. System default keeps the wallpaper.
     */
    fun applyWindow(activity: Activity) {
        val theme = ThemeStore.current(activity)
        val window = activity.window
        val image = WallpaperPrefs(activity).imageChoice
        if (image != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(image.dim.scrimColor))
        } else if (theme != null) {
            window.setBackgroundDrawable(ColorDrawable(theme.background))
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
        if (theme != null) applyBars(window, theme)
    }

    private fun applyBars(window: Window, theme: YokeTheme) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !theme.dark
        controller.isAppearanceLightNavigationBars = !theme.dark
    }

    /** Dialog window: a raised, rounded surface in the theme's colours. */
    fun applyDialogWindow(window: Window?, context: Context) {
        val theme = ThemeStore.current(context) ?: return
        val density = context.resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            cornerRadius = 28 * density
            setColor(theme.surface)
            setStroke((1 * density).toInt().coerceAtLeast(1), theme.muted)
        }
        window?.setBackgroundDrawable(InsetDrawable(shape, (20 * density).toInt()))
    }

    /** A rounded rectangle for chips and cards in the theme's colours. */
    fun roundedRect(context: Context, radiusDp: Float, fill: Int, stroke: Int? = null): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            cornerRadius = radiusDp * density
            setColor(fill)
            if (stroke != null) setStroke((1 * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    class Fonts(val regular: Typeface, val bold: Typeface, val allBold: Boolean)

    /** The bundled font to apply, or null for the system font. Honours the bold toggle. */
    fun fonts(context: Context): Fonts? {
        if (!ThemePrefs(context).jetBrainsMono) return null
        val regular = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: return null
        val bold = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold) ?: regular
        return Fonts(regular, bold, Prefs(context).boldFont)
    }
}
