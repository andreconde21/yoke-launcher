package com.outsmartis.yoke.iconrow

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.outsmartis.yoke.helper.getUserHandleFromString

/** Draws icon-row bitmaps. Results are cached per (app, style, size, colour); a theme change changes the key. */
object IconRenderer {

    private val cache = LruCache<String, Bitmap>(24)

    fun render(context: Context, slot: IconSlot, style: IconStyle, sizePx: Int, color: Int): Bitmap? {
        val key = "${slot.packageName}/${slot.activityClassName}/${slot.user}/${style.id}/$sizePx/$color"
        cache.get(key)?.let { return it }
        val info = resolve(context, slot) ?: return null
        val bmp = try {
            when (style) {
                IconStyle.ORIGINAL -> drawIcon(info.getIcon(0), sizePx)
                IconStyle.MONOCHROME -> monochrome(context, info, slot, sizePx, color)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun label(context: Context, slot: IconSlot): String = runCatching { resolve(context, slot)?.label?.toString() }
        .getOrNull() ?: slot.packageName

    fun clear() = cache.evictAll()

    private fun resolve(context: Context, slot: IconSlot): LauncherActivityInfo? {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val list = launcher.getActivityList(slot.packageName, getUserHandleFromString(context, slot.user))
        return list.firstOrNull { it.componentName.className == slot.activityClassName } ?: list.firstOrNull()
    }

    private fun monochrome(context: Context, info: LauncherActivityInfo, slot: IconSlot, size: Int, color: Int): Bitmap {
        val icon = info.getIcon(context.resources.displayMetrics.densityDpi)
        if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable) {
            icon.monochrome?.let { mono ->
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val d = mono.mutate()
                // The layer is 108dp with a 72dp visible area; scale so that area fills the square.
                val pad = size / 6
                d.setBounds(-pad, -pad, size + pad, size + pad)
                d.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                d.draw(Canvas(bmp))
                return bmp
            }
        }
        val raw = drawIcon(icon, size)
        val pixels = IntArray(size * size)
        raw.getPixels(pixels, 0, size, 0, 0, size, size)
        val mask = IconMask.toMask(pixels, color)
        if (mask != null) {
            return Bitmap.createBitmap(mask, size, size, Bitmap.Config.ARGB_8888)
        }
        return letterTile(info.label?.toString()?.firstOrNull() ?: slot.packageName.first(), size, color)
    }

    private fun drawIcon(icon: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bmp))
        return bmp
    }

    private fun letterTile(letter: Char, size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.06f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val r = RectF(stroke, stroke, size - stroke, size - stroke)
        c.drawRoundRect(r, size * 0.25f, size * 0.25f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = size * 0.5f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        c.drawText(letter.uppercaseChar().toString(), size / 2f, y, paint)
        return bmp
    }
}
