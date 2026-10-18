package com.outsmartis.yoke.askbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.outsmartis.yoke.R
import com.outsmartis.yoke.theme.ThemeApplier

/**
 * The home bar itself: a hint that looks like a search field and a mic. It is not editable; a tap
 * opens the command palette, which does the searching. Colours follow the current theme.
 */
class AskBarView(context: Context) : LinearLayout(context) {

    val mic: ImageView
    private val label: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = resources.displayMetrics.density
        val fg = ThemeApplier.textColor(context, secondary = false)
        val muted = ThemeApplier.textColor(context, secondary = true)
        background = GradientDrawable().apply {
            cornerRadius = HEIGHT_DP / 2f * density
            setStroke(density.toInt().coerceAtLeast(1), muted)
            setColor(ColorUtils.setAlphaComponent(fg, 0x14))
        }
        setPadding((18 * density).toInt(), 0, (4 * density).toInt(), 0)
        minimumHeight = (HEIGHT_DP * density).toInt()
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.ask_bar_hint)

        label = TextView(context, null, 0, R.style.TextSmall).apply {
            hint = context.getString(R.string.ask_bar_hint)
            setTextColor(fg)
            setHintTextColor(muted)
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        mic = ImageView(context).apply {
            setImageResource(R.drawable.ic_mic)
            imageTintList = ColorStateList.valueOf(fg)
            alpha = IDLE_MIC_ALPHA
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.ask_bar_mic)
        }
        val micSize = (HEIGHT_DP * density).toInt()
        addView(mic, LayoutParams(micSize, micSize))
    }

    /** Dictated text while it comes in; empty shows the hint again. */
    fun showText(text: String) {
        label.text = text
    }

    fun setListening(on: Boolean) {
        mic.alpha = if (on) 1f else IDLE_MIC_ALPHA
    }

    companion object {
        const val HEIGHT_DP = 48
        private const val IDLE_MIC_ALPHA = 0.6f

        fun heightPx(context: Context): Int = (HEIGHT_DP * context.resources.displayMetrics.density).toInt()
    }
}
