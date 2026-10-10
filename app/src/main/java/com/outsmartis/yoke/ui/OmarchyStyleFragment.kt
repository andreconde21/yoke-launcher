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
import com.outsmartis.yoke.omarchy.OmarchyStylePrefs
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

    private fun render() {
        column.removeAllViews()
        column.addView(text(true, getString(R.string.omarchy_title)).apply { textSize = 22f })
        column.addView(text(false, getString(R.string.omarchy_hint)).apply { alpha = 0.7f })

        heading(getString(R.string.omarchy_toast_section))
        row(R.string.omarchy_toast, onOff(prefs.toastEnabled)) { prefs.toastEnabled = !prefs.toastEnabled }
        if (prefs.toastEnabled) row(R.string.omarchy_toast_duration, getString(
            if (prefs.toastLong) R.string.omarchy_long else R.string.omarchy_short,
        )) { prefs.toastLong = !prefs.toastLong }

        ThemeApplier.apply(column)
    }
}
