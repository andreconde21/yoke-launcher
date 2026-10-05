package com.outsmartis.yoke.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.iconrow.IconRenderer
import com.outsmartis.yoke.iconrow.IconPosition
import com.outsmartis.yoke.iconrow.IconRowPrefs
import com.outsmartis.yoke.iconrow.IconSize
import com.outsmartis.yoke.iconrow.IconSlot
import com.outsmartis.yoke.iconrow.IconSlots
import com.outsmartis.yoke.iconrow.IconStyle

/** Icon row settings and the slot editor (add, replace, reorder, remove), built in code. */
class IconRowFragment : BaseFragment() {

    private lateinit var prefs: IconRowPrefs
    private lateinit var column: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        prefs = IconRowPrefs(ctx)
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

    private fun <T> next(all: List<T>, current: T): T = all[(all.indexOf(current) + 1) % all.size]

    private fun render() {
        val ctx = requireContext()
        column.removeAllViews()
        column.addView(text(true, getString(R.string.icon_row_title)).apply { textSize = 22f })
        column.addView(text(true, "${getString(R.string.icon_row_enabled)}: ${onOff(prefs.enabled)}") { prefs.toggle(); render() })
        column.addView(text(true, "${getString(R.string.icon_row_style)}: " + getString(
            if (prefs.style == IconStyle.MONOCHROME) R.string.icon_row_style_mono else R.string.icon_row_style_original,
        )) { prefs.style = next(IconStyle.entries, prefs.style); render() })
        column.addView(text(true, "${getString(R.string.icon_row_labels)}: ${onOff(prefs.labels)}") { prefs.labels = !prefs.labels; render() })
        column.addView(text(true, "${getString(R.string.icon_row_size)}: " + getString(when (prefs.size) {
            IconSize.SMALL -> R.string.icon_row_size_small
            IconSize.MEDIUM -> R.string.icon_row_size_medium
            IconSize.LARGE -> R.string.icon_row_size_large
        })) { prefs.size = next(IconSize.entries, prefs.size); render() })
        column.addView(text(true, "${getString(R.string.icon_row_position)}: " + getString(
            if (prefs.position == IconPosition.BOTTOM) R.string.icon_row_position_bottom else R.string.icon_row_position_clock,
        )) { prefs.position = next(IconPosition.entries, prefs.position); render() })

        val slots = prefs.slots
        column.addView(text(true, "${getString(R.string.icon_row_apps)}: " + getString(R.string.icon_row_apps_count, slots.size)).apply {
            setPadding(paddingLeft, (24 * resources.displayMetrics.density).toInt(), paddingRight, paddingBottom)
        })
        column.addView(text(false, getString(R.string.icon_row_editor_hint)).apply { alpha = 0.7f })
        for ((i, slot) in slots.withIndex()) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(text(false, "${i + 1}. ${labelOf(ctx, slot)}") { pick(i) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val pad = (14 * resources.displayMetrics.density).toInt()
            fun btn(s: String, enabled: Boolean, f: () -> Unit) = text(true, s) { if (enabled) f() }.apply {
                setPadding(pad, paddingTop, pad, paddingBottom)
                alpha = if (enabled) 1f else 0.3f
            }
            row.addView(btn("↑", i > 0) { prefs.slots = IconSlots.move(prefs.slots, i, -1); render() })
            row.addView(btn("↓", i < slots.size - 1) { prefs.slots = IconSlots.move(prefs.slots, i, 1); render() })
            row.addView(btn("✕", true) { prefs.slots = IconSlots.remove(prefs.slots, i); render() })
            column.addView(row)
        }
        if (slots.size < IconSlots.MAX) column.addView(text(true, "+ " + getString(R.string.icon_row_add_app)) { pick(slots.size) })
        com.outsmartis.yoke.theme.ThemeApplier.apply(column)
    }

    private fun labelOf(ctx: Context, slot: IconSlot) = IconRenderer.label(ctx, slot)

    private fun pick(index: Int) {
        ViewModelProvider(requireActivity())[MainViewModel::class.java].getAppList(false)
        findNavController().navigate(
            R.id.action_iconRowFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_SET_ICON_ROW_APP_1 + index),
        )
    }
}
