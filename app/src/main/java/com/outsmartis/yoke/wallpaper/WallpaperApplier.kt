package com.outsmartis.yoke.wallpaper

import com.outsmartis.yoke.theme.ThemePrefs

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.theme.ThemeStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Fetches, crops and sets wallpapers. Everything here runs on one background
 * thread; callbacks arrive on the main thread. Network (HttpURLConnection) is
 * used only for Omarchy backgrounds; weather is the only other network user.
 */
object WallpaperApplier {

    /** Result of an apply: null error means success. */
    fun interface Callback {
        fun done(error: String?)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun cacheDir(context: Context) = File(context.cacheDir, "omarchy-bg").also { it.mkdirs() }
    private fun ownImage(context: Context) = File(File(context.filesDir, "wallpaper").also { it.mkdirs() }, "own.jpg")

    fun cachedFull(context: Context, themeId: String, file: String) =
        File(File(cacheDir(context), WallpaperLogic.cacheName(themeId)).also { it.mkdirs() }, WallpaperLogic.cacheName(file))

    private fun cachedThumb(context: Context, themeId: String, file: String) =
        File(File(cacheDir(context), WallpaperLogic.cacheName(themeId)).also { it.mkdirs() },
            "thumb-" + WallpaperLogic.cacheName(file) + ".jpg")

    fun hasOwnImage(context: Context) = ownImage(context).isFile

    private fun screen(context: Context): Pair<Int, Int> {
        val m = context.resources.displayMetrics
        return minOf(m.widthPixels, m.heightPixels) to maxOf(m.widthPixels, m.heightPixels)
    }

    private fun post(cb: Callback?, error: String?) {
        if (cb != null) main.post { cb.done(error) }
    }

    // Theme colour

    /** Solid wallpaper in the active theme's background colour; System default gives black or white. */
    fun applyThemeColour(context: Context, callback: Callback? = null) {
        val app = context.applicationContext
        val prefs = WallpaperPrefs(app)
        val colour = themeColour(app)
        val target = prefs.choice.target
        worker.execute {
            val error = setSolid(app, colour, target)
            if (error == null) {
                prefs.applied = true
                prefs.lastSolid = "$colour:${target.name}"
            }
            post(callback, error)
        }
    }

    fun themeColour(context: Context): Int {
        ThemeStore.current(context)?.let { return it.background }
        val dark = when (ThemeStore.nightMode(context, Prefs(context).appTheme)) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) Color.BLACK else Color.WHITE
    }

    /** Called when the theme changed: re-colours the wallpaper while the source is Theme colour. */
    fun onThemeChanged(context: Context) {
        val app = context.applicationContext
        val prefs = WallpaperPrefs(app)
        val choice = prefs.choice
        // Like Omarchy: an Omarchy background follows the theme to the new theme's first background.
        if (prefs.applied && choice.source == WallpaperSource.OMARCHY) {
            val theme = currentBackgroundTheme(app) ?: return
            if (theme == choice.omarchyTheme) return
            val file = WallpaperLogic.backgroundsFor(theme).first()
            prefs.choice = choice.copy(omarchyTheme = theme, omarchyFile = file)
            applyOmarchy(app, theme, file)
            return
        }
        if (!prefs.applied || choice.source != WallpaperSource.THEME_COLOUR) return
        if (prefs.lastSolid == "${themeColour(app)}:${choice.target.name}") return
        applyThemeColour(app)
    }

    private fun setSolid(context: Context, colour: Int, target: WallpaperTarget): String? {
        val (w, h) = screen(context)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        bitmap.eraseColor(colour)
        return try {
            setBitmap(context, bitmap, target)
        } finally {
            bitmap.recycle()
        }
    }

    // Omarchy backgrounds

    /** Downloads (when not cached) and returns the thumbnail file, or null on failure. Call off the main thread. */
    fun thumbnail(context: Context, themeId: String, file: String): File? {
        val thumb = cachedThumb(context, themeId, file)
        if (thumb.isFile && thumb.length() > 0) return thumb
        val full = download(context, themeId, file) ?: return null
        val bmp = decodeSampled(full, 360, 640) ?: return null
        return try {
            thumb.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            thumb
        } catch (_: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun download(context: Context, themeId: String, file: String): File? {
        val dest = cachedFull(context, themeId, file)
        if (dest.isFile && dest.length() > 0) return dest
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            val conn = URL(WallpaperLogic.backgroundUrl(themeId, file)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode != 200) return null
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            if (tmp.renameTo(dest)) dest else null
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    /** The Omarchy theme whose backgrounds go with the current theme, or null when it has none. */
    fun currentBackgroundTheme(context: Context): String? =
        WallpaperLogic.backgroundTheme(ThemePrefs(context).themeId, ThemeStore.current(context)?.id)

    /**
     * For the "Next background" gesture: shows the current theme's next Omarchy background on home
     * and lock (keeping the chosen target and dim). Returns false when the theme has no backgrounds.
     */
    fun nextBackground(context: Context, callback: Callback? = null): Boolean {
        val app = context.applicationContext
        val prefs = WallpaperPrefs(app)
        val theme = currentBackgroundTheme(app) ?: return false
        val file = WallpaperLogic.nextBackground(theme, prefs.choice, prefs.applied) ?: return false
        prefs.choice = prefs.choice.copy(source = WallpaperSource.OMARCHY, omarchyTheme = theme, omarchyFile = file)
        prefs.applied = true
        applyOmarchy(app, theme, file, callback)
        return true
    }

    fun applyOmarchy(context: Context, themeId: String, file: String, callback: Callback? = null) {
        val app = context.applicationContext
        worker.execute {
            val full = download(app, themeId, file)
            if (full == null) return@execute post(callback, "download")
            val (w, h) = screen(app)
            val bmp = decodeCropped(full, w, h)
            if (bmp == null) return@execute post(callback, "decode")
            post(callback, setAndRemember(app, bmp))
        }
    }

    // Your image

    /** Crops the picked image to the screen, keeps a copy for re-applying, and sets it. */
    fun applyPicked(context: Context, uri: Uri, callback: Callback? = null) {
        val app = context.applicationContext
        worker.execute {
            val (w, h) = screen(app)
            val bmp = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) null else {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = WallpaperLogic.sampleSize(bounds.outWidth, bounds.outHeight, w, h)
                    }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?.let { cropToScreen(it, w, h) }
                }
            } catch (_: Exception) {
                null
            }
            if (bmp == null) return@execute post(callback, "decode")
            try {
                ownImage(app).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            } catch (_: Exception) {
            }
            val prefs = WallpaperPrefs(app)
            prefs.choice = prefs.choice.copy(source = WallpaperSource.IMAGE)
            val error = setBitmap(app, bmp, prefs.choice.target)
            bmp.recycle()
            if (error == null) prefs.applied = true
            post(callback, error)
        }
    }

    /** Applies the saved choice again (used when the target changes). */
    fun reapply(context: Context, callback: Callback? = null) {
        val app = context.applicationContext
        val c = WallpaperPrefs(app).choice
        when (c.source) {
            WallpaperSource.THEME_COLOUR -> applyThemeColour(app, callback)
            WallpaperSource.OMARCHY ->
                if (c.omarchyTheme.isEmpty() || c.omarchyFile.isEmpty()) post(callback, null)
                else applyOmarchy(app, c.omarchyTheme, c.omarchyFile, callback)
            WallpaperSource.IMAGE -> worker.execute {
                val (w, h) = screen(app)
                val bmp = decodeCropped(ownImage(app), w, h)
                if (bmp == null) return@execute post(callback, "decode")
                post(callback, setAndRemember(app, bmp))
            }
        }
    }

    private fun setAndRemember(context: Context, bmp: Bitmap): String? {
        val prefs = WallpaperPrefs(context)
        val error = try {
            setBitmap(context, bmp, prefs.choice.target)
        } finally {
            bmp.recycle()
        }
        if (error == null) prefs.applied = true
        return error
    }

    // Decoding

    private fun decodeSampled(file: File, reqW: Int, reqH: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) null
        else BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = WallpaperLogic.sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        })
    } catch (_: Exception) {
        null
    }

    private fun decodeCropped(file: File, w: Int, h: Int): Bitmap? =
        decodeSampled(file, w, h)?.let { cropToScreen(it, w, h) }

    /** Centre-crops [src] to the w x h aspect and scales to exactly that size. Recycles [src]. */
    private fun cropToScreen(src: Bitmap, w: Int, h: Int): Bitmap {
        val r = WallpaperLogic.cropRect(src.width, src.height, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src, Rect(r.left, r.top, r.right, r.bottom), Rect(0, 0, w, h),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        src.recycle()
        return out
    }

    private fun setBitmap(context: Context, bitmap: Bitmap, target: WallpaperTarget): String? = try {
        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
        null
    } catch (e: Exception) {
        e.message ?: "set"
    }
}
