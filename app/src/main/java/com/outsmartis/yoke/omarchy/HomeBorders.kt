package com.outsmartis.yoke.omarchy

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewGroup
import android.widget.TextView
import com.outsmartis.yoke.R

/**
 * Draws home app labels as bordered tiles (YOK-020). The labels' own padding and margins are
 * remembered on first use so switching the option off puts them back exactly.
 */
object HomeBorders {

    /** A label as the layout made it, restored when the option goes off. */
    private class Base(val padding: IntArray, val topMargin: Int, val bottomMargin: Int, val background: Drawable?)

    fun apply(labels: List<TextView>) {
        val first = labels.firstOrNull() ?: return
        val p = OmarchyStylePrefs(first.context)
        for (tv in labels) {
            val lp = tv.layoutParams as? ViewGroup.MarginLayoutParams
            val saved = tv.getTag(R.id.omarchy_base) as? Base
            if (p.borderMode == BorderMode.OFF) {
                // Never styled: leave the label untouched.
                saved ?: continue
                tv.background = saved.background
                tv.setPadding(saved.padding[0], saved.padding[1], saved.padding[2], saved.padding[3])
                lp?.let { it.topMargin = saved.topMargin; it.bottomMargin = saved.bottomMargin; tv.layoutParams = it }
                tv.setTag(R.id.omarchy_base, null)
                continue
            }
            val base = saved ?: Base(
                intArrayOf(tv.paddingLeft, tv.paddingTop, tv.paddingRight, tv.paddingBottom),
                lp?.topMargin ?: 0, lp?.bottomMargin ?: 0, tv.background,
            ).also { tv.setTag(R.id.omarchy_base, it) }
            style(tv, base, lp, p)
        }
    }

    private fun style(tv: TextView, base: Base, lp: ViewGroup.MarginLayoutParams?, p: OmarchyStylePrefs) {
        val c = OmarchyColors.of(tv.context)
        val d = tv.resources.displayMetrics.density
        val width = (p.borderWidthDp * d).toInt().coerceAtLeast(1)
        fun shape(color: BorderColor?): Drawable = GradientDrawable().apply {
            cornerRadius = p.borderRadiusDp * d
            setColor(0)
            // A transparent stroke keeps the tile the same size when its border appears.
            setStroke(width, when (color) { BorderColor.ACCENT -> c.accent; BorderColor.SECONDARY -> c.secondary; null -> 0 })
        }
        tv.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(HomeTiles.border(p.borderMode, true)))
            addState(intArrayOf(android.R.attr.state_focused), shape(HomeTiles.border(p.borderMode, true)))
            addState(intArrayOf(), shape(HomeTiles.border(p.borderMode, false)))
        }
        val side = (12 * d).toInt()
        tv.setPadding(base.padding[0] + side, base.padding[1], base.padding[2] + side, base.padding[3])
        val half = (p.borderGapDp * d / 2).toInt()
        lp?.let { it.topMargin = base.topMargin + half; it.bottomMargin = base.bottomMargin + half; tv.layoutParams = it }
    }
}
