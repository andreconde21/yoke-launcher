package com.outsmartis.yoke.helper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps a bottom sheet's last row above the navigation bar and the keyboard.
 *
 * Relying on the dialog's own insets alone was not enough: on some phones the
 * dialog is told nothing about the navigation bar and still draws behind it.
 * So besides the insets, this measures where the sheet actually ends on the
 * screen and pads it by however much it reaches into the navigation bar.
 */
object SheetInsets {

    fun install(scroll: View, content: View, side: Int, top: Int, bottom: Int) {
        var left = 0
        var right = 0
        var ime = 0
        var navOverlap = 0
        var reported = 0
        fun apply() = content.setPadding(side + left, top, side + right, bottom + maxOf(ime, navOverlap, reported))

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            left = bars.left
            right = bars.right
            reported = bars.bottom
            ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            apply()
            insets
        }
        scroll.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val sheetBottom = loc[1] + v.height
            val navTop = screenHeight(v.context) - navBarHeight(v)
            val overlap = (sheetBottom - navTop).coerceAtLeast(0)
            // Padding grows the sheet upwards (it is gravity bottom), so its bottom edge stays put: stable.
            if (overlap != navOverlap) {
                navOverlap = overlap
                v.post { apply() }
            }
        }
        apply()
    }

    private fun screenHeight(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.currentWindowMetrics.bounds.height()
        else DisplayMetrics().also {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(it)
        }.heightPixels
    }

    /** The navigation bar height as the activity sees it, else the system's dimension for it. */
    private fun navBarHeight(view: View): Int {
        val activity = view.context.findActivity()
        val fromActivity = activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        if (fromActivity > 0) return fromActivity
        val res = view.resources
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
