package com.outsmartis.yoke.omarchy

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * The animated theme switch (YOK-025): [recreate] draws the current window into a bitmap before
 * recreating, and [reveal] (called by the new activity once its content is set) lays that bitmap
 * over the new look and fades it out. Off, or with animations removed, it is a plain recreate.
 */
object ThemeTransition {

    private class Snapshot(val bitmap: Bitmap, val durationMs: Long, val at: Long)

    @Volatile
    private var pending: Snapshot? = null

    fun recreate(activity: Activity) {
        val duration = ThemeFade.durationMs(OmarchyStylePrefs(activity).fadeMode, animatorScale(activity))
        if (duration > 0) pending = snapshot(activity)?.let { Snapshot(it, duration, SystemClock.uptimeMillis()) }
        activity.recreate()
    }

    /** Call from onCreate after setContentView. */
    fun reveal(activity: Activity) {
        val s = pending ?: return
        pending = null
        if (SystemClock.uptimeMillis() - s.at > ThemeFade.SNAPSHOT_MAX_AGE_MS) return s.bitmap.recycle()
        val decor = activity.window.decorView as? ViewGroup ?: return s.bitmap.recycle()
        val cover = ImageView(activity).apply {
            setImageBitmap(s.bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
        }
        decor.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Start once the new look has drawn its first frame under the cover.
        cover.post {
            cover.animate().alpha(0f).setDuration(s.durationMs).withEndAction {
                decor.removeView(cover)
                s.bitmap.recycle()
            }.start()
        }
    }

    private fun snapshot(activity: Activity): Bitmap? = try {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) null
        else Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
    } catch (_: Throwable) {
        null // out of memory or a view that cannot draw: just recreate
    }

    private fun animatorScale(activity: Activity): Float = try {
        Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Exception) {
        1f
    }
}
