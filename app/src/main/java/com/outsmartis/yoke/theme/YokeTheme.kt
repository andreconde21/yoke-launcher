package com.outsmartis.yoke.theme

import kotlin.math.pow

/** Contrast maths (WCAG 2.x) on ARGB ints. Pure, so it is unit tested. */
object ThemeContrast {

    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [from] moved [t] (0..1) of the way to [to], opaque. */
    fun blend(from: Int, to: Int, t: Double): Int {
        fun mix(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Int): Int =
        if (ratio(0xFF000000.toInt(), background) >= ratio(0xFFFFFFFF.toInt(), background)) 0xFF000000.toInt()
        else 0xFFFFFFFF.toInt()

    /**
     * [color] unchanged if it already has [min] contrast on [background];
     * otherwise nudged towards [towards] in 5% steps until it does.
     */
    fun ensure(color: Int, background: Int, min: Double, towards: Int = onColor(background)): Int {
        var t = 0.0
        var c = color or (0xFF shl 24)
        while (ratio(c, background) < min && t < 1.0) {
            t += 0.05
            c = blend(color, towards, t.coerceAtMost(1.0))
        }
        return c
    }
}

/**
 * What the UI actually paints with, resolved from a palette so every role is
 * readable on the background (light themes included).
 */
class YokeTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Int,
    /** Primary text. */
    val foreground: Int,
    /** Secondary text and hints: the palette's muted if readable, else a softened foreground. */
    val secondary: Int,
    /** Borders and dividers: the palette's muted as given. */
    val muted: Int,
    /** Fill for selected things (chips, highlights), as given. */
    val accent: Int,
    /** Accent used as text or a thin stroke: nudged until readable on the background. */
    val accentText: Int,
    /** Text on top of [accent]. */
    val onAccent: Int,
    val selection: Int,
    /** Raised surfaces: dialogs, menus, cards. */
    val surface: Int,
) {
    companion object {
        const val SYSTEM_ID = "system"
        const val PC_ID = "omarchy-pc"

        fun from(p: OmarchyTheme): YokeTheme {
            val bg = p.background or (0xFF shl 24)
            val fg = ThemeContrast.ensure(p.foreground, bg, 4.5)
            val secondary = when {
                ThemeContrast.ratio(p.muted, bg) >= 3.0 -> p.muted or (0xFF shl 24)
                else -> ThemeContrast.ensure(ThemeContrast.blend(fg, bg, 0.35), bg, 3.0, fg)
            }
            val accent = p.accent or (0xFF shl 24)
            return YokeTheme(
                id = p.id,
                name = p.name,
                dark = p.dark,
                background = bg,
                foreground = fg,
                secondary = secondary,
                muted = p.muted or (0xFF shl 24),
                accent = accent,
                accentText = ThemeContrast.ensure(accent, bg, 3.0, fg),
                onAccent = ThemeContrast.onColor(accent),
                selection = p.selection or (0xFF shl 24),
                surface = p.lighterBackground.takeIf { it != p.background } ?: ThemeContrast.blend(bg, fg, 0.08),
            )
        }
    }
}
