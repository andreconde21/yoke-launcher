package com.outsmartis.yoke.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.omarchy.BorderMode
import com.outsmartis.yoke.omarchy.DotStyle
import com.outsmartis.yoke.omarchy.Edge
import com.outsmartis.yoke.omarchy.FadeMode
import com.outsmartis.yoke.omarchy.HomeTiles
import com.outsmartis.yoke.omarchy.OmarchyStylePrefs
import com.outsmartis.yoke.omarchy.TerminalLabel
import com.outsmartis.yoke.omarchy.TopLine
import com.outsmartis.yoke.omarchy.TopLineClock
import com.outsmartis.yoke.omarchy.TopModule
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemePrefs

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

    /** Turning terminal mode on also picks JetBrains Mono (Look > Font can change it back). */
    private fun toggleTerminal() {
        prefs.terminalEnabled = !prefs.terminalEnabled
        val font = ThemePrefs(requireContext())
        if (prefs.terminalEnabled && !font.jetBrainsMono) {
            font.jetBrainsMono = true
            requireActivity().recreate()
        }
    }

    private fun editPrompt() {
        val ctx = requireContext()
        var field: EditText? = null
        ctx.createDialog(
            title = R.string.omarchy_terminal_prompt,
            action = R.string.save,
            onAction = {
                prefs.terminalPromptText = field?.text?.toString().orEmpty()
                if (isAdded) render()
            },
        ) { container ->
            EditText(container.context).apply {
                setText(prefs.terminalPromptText)
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
                filters = arrayOf(InputFilter.LengthFilter(TerminalLabel.MAX_PROMPT))
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                field = this
            }
        }.showRespectingStatusBar()
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

        heading(getString(R.string.omarchy_terminal_section))
        row(R.string.omarchy_terminal, onOff(prefs.terminalEnabled)) { toggleTerminal() }
        if (prefs.terminalEnabled) {
            row(R.string.omarchy_terminal_prompt, if (prefs.terminalPrompt) "\"${prefs.terminalPromptText}\"" else getString(R.string.off)) {
                prefs.terminalPrompt = !prefs.terminalPrompt
            }
            if (prefs.terminalPrompt) column.addView(text(true, getString(R.string.omarchy_terminal_prompt_edit)) { editPrompt() })
            row(R.string.omarchy_terminal_lowercase, onOff(prefs.terminalLowercase)) { prefs.terminalLowercase = !prefs.terminalLowercase }
            row(R.string.omarchy_terminal_cursor, onOff(prefs.terminalCursor)) { prefs.terminalCursor = !prefs.terminalCursor }
        }

        heading(getString(R.string.omarchy_borders_section))
        row(R.string.omarchy_borders, getString(when (prefs.borderMode) {
            BorderMode.OFF -> R.string.off
            BorderMode.PRESS -> R.string.omarchy_borders_press
            BorderMode.ALWAYS -> R.string.omarchy_borders_always
        })) { prefs.borderMode = next(BorderMode.entries, prefs.borderMode) }
        if (prefs.borderMode != BorderMode.OFF) {
            row(R.string.omarchy_borders_width, "${prefs.borderWidthDp} dp") { prefs.borderWidthDp = HomeTiles.next(prefs.borderWidthDp, HomeTiles.WIDTHS_DP) }
            row(R.string.omarchy_borders_radius, "${prefs.borderRadiusDp} dp") { prefs.borderRadiusDp = HomeTiles.next(prefs.borderRadiusDp, HomeTiles.RADII_DP) }
            row(R.string.omarchy_borders_gap, "${prefs.borderGapDp} dp") { prefs.borderGapDp = HomeTiles.next(prefs.borderGapDp, HomeTiles.GAPS_DP) }
        }

        heading(getString(R.string.omarchy_fade_section))
        row(R.string.omarchy_fade, getString(when (prefs.fadeMode) {
            FadeMode.OFF -> R.string.off
            FadeMode.FADE -> R.string.omarchy_fade_fade
            FadeMode.QUICK -> R.string.omarchy_fade_quick
        })) { prefs.fadeMode = next(FadeMode.entries, prefs.fadeMode) }
        if (prefs.fadeMode != FadeMode.OFF) column.addView(text(false, getString(R.string.omarchy_fade_hint)).apply { alpha = 0.7f })

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
