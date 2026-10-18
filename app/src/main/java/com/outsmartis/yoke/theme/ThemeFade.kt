package com.outsmartis.yoke.theme

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.outsmartis.yoke.helper.isEinkDisplay

/**
 * Colour cross-fade for a theme change (YOK-033). A theme change recreates the activity, so
 * [recreate] first draws the current window into a bitmap; the new activity calls [reveal] once its
 * content is set, lays that bitmap over the new look and fades it out.
 */
object ThemeFade {

    const val DURATION_MS = 250L

    /** A snapshot older than this belongs to some other recreate (the fade would be a lie). */
    const val SNAPSHOT_MAX_AGE_MS = 3000L

    /**
     * Fade length for the system animator duration scale; 0 means no fade at all. The scale is only
     * the on/off switch here: ViewPropertyAnimator multiplies the duration by it itself.
     */
    fun durationMs(animatorScale: Float): Long =
        if (animatorScale <= 0f || animatorScale.isNaN()) 0L else DURATION_MS

    /** True when a snapshot taken at [at] is too old to belong to the recreate now being revealed. */
    fun isStale(now: Long, at: Long): Boolean = now - at > SNAPSHOT_MAX_AGE_MS || now < at

    /**
     * Windows that show the system wallpaper are faded too: the snapshot is drawn from the decor
     * view only, so the wallpaper is NOT in it (those pixels stay transparent). The old content
     * (text, icons, scrims) cross-fades over the new window, and the wallpaper underneath is the
     * live one, never a copy, so it cannot flicker or double up. No wallpaper permission is needed.
     */
    fun canFade(duration: Long): Boolean = duration > 0

    private class Snapshot(val bitmap: Bitmap, val durationMs: Long, val at: Long)

    @Volatile
    private var pending: Snapshot? = null

    /** Same as [Activity.recreate], with the colours fading from the old look to the new one. */
    fun recreate(activity: Activity) {
        val duration = if (activity.isEinkDisplay()) 0L else durationMs(animatorScale(activity))
        dropPending()
        if (canFade(duration)) {
            pending = snapshot(activity)?.let { Snapshot(it, duration, SystemClock.uptimeMillis()) }
            // If the new activity never reveals it (finished, crashed), do not keep the bitmap around.
            if (pending != null) {
                handler.removeCallbacks(dropRunnable)
                handler.postDelayed(dropRunnable, SNAPSHOT_MAX_AGE_MS)
            }
        }
        activity.recreate()
    }

    // Lazy: the pure helpers above are unit-tested on the JVM, where Looper is not available.
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val dropRunnable = Runnable { dropPending() }

    private fun dropPending() {
        pending?.bitmap?.recycle()
        pending = null
    }

    /** Call from onCreate after setContentView. */
    fun reveal(activity: Activity) {
        val s = pending ?: return
        pending = null
        if (isStale(SystemClock.uptimeMillis(), s.at)) return s.bitmap.recycle()
        val decor = activity.window.decorView as? ViewGroup ?: return s.bitmap.recycle()
        val cover = ImageView(activity).apply {
            setImageBitmap(s.bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
        }
        decor.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Start once the new look has had its first frame under the cover.
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
