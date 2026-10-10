package com.outsmartis.yoke.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.omarchy.DotStyle
import com.outsmartis.yoke.omarchy.Edge
import com.outsmartis.yoke.omarchy.OmarchyStylePrefs
import com.outsmartis.yoke.omarchy.TopLine
import com.outsmartis.yoke.omarchy.TopLineClock
import com.outsmartis.yoke.omarchy.TopModule
import com.outsmartis.yoke.theme.ThemeApplier

/** Settings > Look > Omarchy style: every option of the Omarchy-like pieces, built in code. All off by default. */
class OmarchyStyleFragment : BaseFragment() {

    private lateinit var prefs: OmarchyStylePrefs
    private lateinit var column: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        prefs = OmarchyStylePrefs(ctx)
        val d = resources.displayMetrics.density
        column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.rounded_rect_shade_color)
            setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
        }
        val outer = LinearLayout(ctx).apply {
            setPadding((12 * d).toInt(), (64 * d).toInt(), (12 * d).toInt(), (64 * d).toInt())
            addView(column, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryShadeDarkColor))
            isVerticalScrollBarEnabled = false
            addView(outer)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun <T> next(all: List<T>, current: T): T = all[(all.indexOf(current) + 1) % all.size]

    private fun edge(e: Edge) = getString(if (e == Edge.TOP) R.string.omarchy_top else R.string.omarchy_bottom)

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun text(bold: Boolean, s: String, onClick: (() -> Unit)? = null): TextView {
        val d = resources.displayMetrics.density
        return TextView(requireContext(), null, 0, if (bold) R.style.TextSmallBold else R.style.TextSmall).apply {
            text = s
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            onClick?.let { setOnClickListener { _ -> it() } }
        }
    }

    /** A section title with some space above it. */
    private fun heading(s: String) {
        column.addView(text(false, s).apply {
            alpha = 0.7f
            setPadding(paddingLeft, (24 * resources.displayMetrics.density).toInt(), paddingRight, paddingBottom)
        })
    }

    /** "Label: value"; a tap runs [onClick] and redraws. */
    private fun row(label: Int, value: String, onClick: () -> Unit) {
        column.addView(text(true, "${getString(label)}: $value") { onClick(); render() })
    }

    private fun moduleName(m: TopModule) = getString(when (m) {
        TopModule.CLOCK -> R.string.omarchy_module_clock
        TopModule.DATE -> R.string.omarchy_module_date
        TopModule.BATTERY -> R.string.omarchy_module_battery
        TopModule.WEATHER -> R.string.omarchy_module_weather
        TopModule.AGENDA -> R.string.omarchy_module_agenda
        TopModule.CONDUCTORE -> R.string.omarchy_module_conductore
    })

    /** The shown modules in order (move up / down, remove), then the ones that can be added. */
    private fun moduleEditor() {
        val ctx = requireContext()
        val pad = (14 * resources.displayMetrics.density).toInt()
        val shown = prefs.topLineModules
        column.addView(text(false, getString(R.string.omarchy_top_line_modules_hint)).apply { alpha = 0.7f })
        for ((i, m) in shown.withIndex()) {
            val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(text(false, "${i + 1}. ${moduleName(m)}"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun btn(s: String, enabled: Boolean, f: () -> Unit) = text(true, s) { if (enabled) { f(); render() } }.apply {
                setPadding(pad, paddingTop, pad, paddingBottom)
                alpha = if (enabled) 1f else 0.3f
            }
            line.addView(btn("↑", i > 0) { prefs.topLineModules = TopLine.move(prefs.topLineModules, i, -1) })
            line.addView(btn("↓", i < shown.size - 1) { prefs.topLineModules = TopLine.move(prefs.topLineModules, i, 1) })
            line.addView(btn("✕", true) { prefs.topLineModules = TopLine.toggle(prefs.topLineModules, m) })
            column.addView(line)
        }
        for (m in TopModule.entries - shown.toSet()) {
            column.addView(text(true, "+ ${moduleName(m)}") { prefs.topLineModules = TopLine.toggle(prefs.topLineModules, m); render() })
        }
    }

    private fun render() {
        column.removeAllViews()
        column.addView(text(true, getString(R.string.omarchy_title)).apply { textSize = 22f })
        column.addView(text(false, getString(R.string.omarchy_hint)).apply { alpha = 0.7f })

        heading(getString(R.string.omarchy_toast_section))
        row(R.string.omarchy_toast, onOff(prefs.toastEnabled)) { prefs.toastEnabled = !prefs.toastEnabled }
        if (prefs.toastEnabled) row(R.string.omarchy_toast_duration, getString(
            if (prefs.toastLong) R.string.omarchy_long else R.string.omarchy_short,
        )) { prefs.toastLong = !prefs.toastLong }

        heading(getString(R.string.omarchy_top_line_section))
        row(R.string.omarchy_top_line, onOff(prefs.topLineEnabled)) { prefs.topLineEnabled = !prefs.topLineEnabled }
        if (prefs.topLineEnabled) {
            row(R.string.omarchy_position, edge(prefs.topLinePosition)) { prefs.topLinePosition = next(Edge.entries, prefs.topLinePosition) }
            row(R.string.omarchy_top_line_clock, getString(
                if (prefs.topLineClock == TopLineClock.ABOVE) R.string.omarchy_top_line_clock_above else R.string.omarchy_top_line_clock_replace,
            )) { prefs.topLineClock = next(TopLineClock.entries, prefs.topLineClock) }
            moduleEditor()
        }

        heading(getString(R.string.omarchy_dots_section))
        row(R.string.omarchy_dots, onOff(prefs.dotsEnabled)) { prefs.dotsEnabled = !prefs.dotsEnabled }
        if (prefs.dotsEnabled) {
            row(R.string.omarchy_dots_style, getString(
                if (prefs.dotsStyle == DotStyle.DOTS) R.string.omarchy_dots_style_dots else R.string.omarchy_dots_style_numbers,
            )) { prefs.dotsStyle = next(DotStyle.entries, prefs.dotsStyle) }
            row(R.string.omarchy_position, edge(prefs.dotsPosition)) { prefs.dotsPosition = next(Edge.entries, prefs.dotsPosition) }
            column.addView(text(false, getString(R.string.omarchy_dots_hint)).apply { alpha = 0.7f })
        }

        ThemeApplier.apply(column)
    }
}
