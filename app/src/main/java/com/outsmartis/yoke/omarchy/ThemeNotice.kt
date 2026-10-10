package com.outsmartis.yoke.omarchy

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.outsmartis.yoke.helper.isSystemAnimationsDisabled
import com.outsmartis.yoke.theme.ThemeApplier

/**
 * A small boxed notice in the top corner after Next theme / Next background, like Omarchy's
 * notifications, painted in the (new) theme's colours. Shown only when the option is on.
 */
object ThemeNotice {

    private const val TAG = "yoke.themeNotice"

    @Volatile
    private var pending: String? = null

    /** Shows [text] on [activity] now. */
    fun post(activity: Activity, text: String) {
        if (OmarchyStylePrefs(activity).toastEnabled) show(activity, text)
    }

    /** Keeps [text] for the next [showPending], for changes that recreate the activity first. */
    fun postAfterRecreate(context: Context, text: String) {
        if (OmarchyStylePrefs(context).toastEnabled) pending = text
    }

    /** Call from onResume: shows the notice kept by [postAfterRecreate], once. */
    fun showPending(activity: Activity) {
        val text = pending ?: return
        pending = null
        activity.window.decorView.post { if (!activity.isFinishing) show(activity, text) }
    }

    private fun show(activity: Activity, text: String) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        decor.findViewWithTag<View>(TAG)?.let { decor.removeView(it) }
        val c = OmarchyColors.of(activity)
        val d = activity.resources.displayMetrics.density
        val box = TextView(activity).apply {
            tag = TAG
            this.text = text
            textSize = 14f
            setTextColor(c.foreground)
            ThemeApplier.fonts(activity)?.let { typeface = it.regular }
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 4 * d
                setColor(c.surface)
                setStroke((2 * d).toInt().coerceAtLeast(1), c.accent)
            }
        }
        val top = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = top + (16 * d).toInt()
            marginEnd = (16 * d).toInt()
        }
        decor.addView(box, lp)
        val animate = !activity.isSystemAnimationsDisabled()
        if (animate) {
            box.alpha = 0f
            box.animate().alpha(1f).setDuration(150).start()
        }
        box.postDelayed({
            if (animate) box.animate().alpha(0f).setDuration(250).withEndAction { decor.removeView(box) }.start()
            else decor.removeView(box)
        }, ThemeNoticeText.durationMs(OmarchyStylePrefs(activity).toastLong))
    }
}
