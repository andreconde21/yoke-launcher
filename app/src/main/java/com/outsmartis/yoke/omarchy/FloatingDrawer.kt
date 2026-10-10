package com.outsmartis.yoke.omarchy

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Rearranges the drawer's own views into a Walker-like floating box (YOK-021): the same search
 * field and results list, only moved into a bordered panel. Searching, auto-launch and long
 * press are untouched because the views and the adapter are the same ones.
 */
object FloatingDrawer {

    /** Does nothing unless the floating style is chosen. [onOutside] runs for a tap outside the box. */
    fun install(root: LinearLayout, searchRow: View, list: RecyclerView, onOutside: () -> Unit) {
        val ctx = root.context
        val p = OmarchyStylePrefs(ctx)
        if (p.drawerStyle != DrawerStyle.FLOATING) return
        val c = OmarchyColors.of(ctx)
        val d = ctx.resources.displayMetrics.density
        val maxResults = p.drawerMaxResults

        root.removeAllViews()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor((c.background and 0x00FFFFFF) or (0x99 shl 24))
        root.setOnClickListener { onOutside() }

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // taps inside the box do not close it
            val pad = (12 * d).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 6 * d
                setColor(c.surface)
                setStroke((2 * d).toInt().coerceAtLeast(1), c.accent)
            }
        }
        panel.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(View(ctx).apply { setBackgroundColor(c.secondary); alpha = 0.4f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * d).toInt().coerceAtLeast(1)).apply {
                topMargin = (4 * d).toInt()
                bottomMargin = (4 * d).toInt()
            })
        val frame = MaxHeightFrame(ctx).apply { maxHeightPx = FloatingDrawerSpec.listMaxHeightPx((48 * d).toInt(), maxResults) }
        frame.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Once a row is laid out, cap the list at exactly maxResults of them.
        list.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val row = (v as RecyclerView).getChildAt(0)?.height ?: return@addOnLayoutChangeListener
            val max = FloatingDrawerSpec.listMaxHeightPx(row, maxResults)
            if (row > 0 && frame.maxHeightPx != max) {
                frame.maxHeightPx = max
                frame.post { frame.requestLayout() }
            }
        }
        panel.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val screen = ctx.resources.displayMetrics.widthPixels
        val width = FloatingDrawerSpec.widthPx(screen, p.drawerWidthPct, (640 * d).toInt())
        val top = if (p.drawerPosition == FloatPosition.TOP) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * d).toInt())
        } else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(Space(ctx), top)
        root.addView(panel, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Space(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // The box moves up with the keyboard instead of the list padding itself.
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom + (16 * d).toInt())
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** A frame that never measures taller than [maxHeightPx]. */
    private class MaxHeightFrame(context: Context) : FrameLayout(context) {
        var maxHeightPx = Int.MAX_VALUE

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val capped = if (mode == MeasureSpec.UNSPECIFIED) maxHeightPx else minOf(size, maxHeightPx)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST))
        }
    }
}
