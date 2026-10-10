package com.outsmartis.yoke.omarchy

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.theme.ThemeApplier

/** The row of page indicators; tapping one calls back with its page. Theme colours, mono font when on. */
class PageDotsView(context: Context) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun bind(pages: List<Page>, current: Page, style: DotStyle, onTap: (Page) -> Unit) {
        removeAllViews()
        val c = OmarchyColors.of(context)
        val d = resources.displayMetrics.density
        val font = ThemeApplier.fonts(context)
        for ((i, page) in pages.withIndex()) {
            val on = page == current
            addView(TextView(context).apply {
                text = PageDots.label(i, on, style)
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = (28 * d).toInt()
                setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
                setTextColor(if (on) c.accent else c.secondary)
                font?.let { typeface = if (on) it.bold else it.regular }
                if (on && style == DotStyle.NUMBERS) background = GradientDrawable().apply {
                    cornerRadius = 4 * d
                    setStroke((1 * d).toInt().coerceAtLeast(1), c.accent)
                }
                contentDescription = "${page.name.lowercase()} ${i + 1}"
                setOnClickListener { if (!on) onTap(page) }
            })
        }
    }

    companion object {
        /** The pages to show for this device's settings. */
        fun pages(context: Context): List<Page> =
            PageDots.pages(Prefs(context).loadGestures().actions.values, CockpitPrefs(context).agendaEnabled)
    }
}
