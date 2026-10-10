package com.outsmartis.yoke.iconrow

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.theme.ThemeApplier

/** The row itself: slots evenly spaced, each an icon with an optional label. */
class IconRowView(context: Context) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun bind(
        prefs: IconRowPrefs,
        onTap: (IconSlot) -> Unit,
        onLongPress: (Int, IconSlot) -> Unit,
    ) {
        removeAllViews()
        val density = resources.displayMetrics.density
        val sizePx = (prefs.size.dp * density).toInt()
        val color = ThemeApplier.textColor(context, secondary = false)
        for ((i, slot) in prefs.slots.withIndex()) {
            val bmp = IconRenderer.render(context, slot, prefs.style, sizePx, color) ?: continue
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = (8 * density).toInt()
                setPadding(0, pad, 0, pad)
                isClickable = true
                isFocusable = true
                setOnClickListener { onTap(slot) }
                setOnLongClickListener { onLongPress(i, slot); true }
            }
            cell.addView(ImageView(context).apply {
                setImageBitmap(bmp)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(sizePx, sizePx))
            val label = IconRenderer.label(context, slot)
            cell.contentDescription = label
            if (prefs.labels) cell.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                text = label
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(color)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Height the row takes at [prefs]'s settings, used to keep the home text slots clear of it. */
    companion object {
        fun heightPx(context: Context, prefs: IconRowPrefs): Int {
            val d = context.resources.displayMetrics.density
            return ((prefs.size.dp + 16 + if (prefs.labels) 16 else 0) * d).toInt()
        }
    }
}
