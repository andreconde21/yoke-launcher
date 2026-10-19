package com.outsmartis.yoke.wallpaper

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
import android.util.Log
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

    private const val TAG = "YokeWallpaper"
    private const val DOWNLOAD_ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 800L

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
        val target = prefs.effectiveTarget
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
        if (prefs.applied && !ownsWallpaper(app, prefs)) {
            // Someone else set the wallpaper since Yoke did: leave it alone and stop managing it.
            prefs.applied = false
            return
        }
        when (val next = WallpaperLogic.backgroundForTheme(choice, prefs.applied, ThemeStore.resolved(app).theme?.id)) {
            is ThemeBackground.Switch -> {
                // Keep the old image until the new one is set; if it cannot be fetched, use the theme colour.
                val before = prefs.choice
                prefs.choice = before.copy(omarchyTheme = next.themeId, omarchyFile = next.file)
                applyOmarchy(app, next.themeId, next.file) { error ->
                    if (error != null) {
                        prefs.choice = prefs.choice.copy(source = WallpaperSource.THEME_COLOUR)
                        applyThemeColour(app)
                    }
                }
                return
            }
            ThemeBackground.Colour -> {
                prefs.choice = choice.copy(source = WallpaperSource.THEME_COLOUR)
                applyThemeColour(app)
                return
            }
            ThemeBackground.Keep -> Unit
        }
        if (!prefs.applied || choice.source != WallpaperSource.THEME_COLOUR) return
        if (prefs.lastSolid == "${themeColour(app)}:${prefs.effectiveTarget.name}") return
        applyThemeColour(app)
    }

    /** True while the wallpaper(s) Yoke writes are still the ones it set last (see [WallpaperLogic.mayOverwrite]). */
    fun ownsWallpaper(context: Context, prefs: WallpaperPrefs = WallpaperPrefs(context)): Boolean = try {
        val wm = WallpaperManager.getInstance(context)
        val target = prefs.effectiveTarget
        val system = target != WallpaperTarget.LOCK
        val lock = target != WallpaperTarget.HOME
        val currentSystem = if (system) wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) else 0
        val currentLock = if (lock) wm.getWallpaperId(WallpaperManager.FLAG_LOCK) else 0
        // Ids recorded before this build are trusted once, now, so a later outside change is caught
        val systemId = if (system) WallpaperLogic.adoptId(prefs.systemId, currentSystem, false) else prefs.systemId
        val lockId = if (lock) WallpaperLogic.adoptId(prefs.lockId, currentLock, true) else prefs.lockId
        if (systemId != prefs.systemId) prefs.systemId = systemId
        if (lockId != prefs.lockId) prefs.lockId = lockId
        WallpaperLogic.ownsTarget(system, lock, systemId, currentSystem, lockId, currentLock)
    } catch (_: Exception) {
        false
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
        // One quiet retry: the first request of a cold connection (fresh install, idle radio) can fail
        // where the next one succeeds, and the Wallpaper screen would otherwise fall back to the theme colour.
        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            fetch(themeId, file, dest)?.let { return it }
            if (attempt < DOWNLOAD_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private fun fetch(themeId: String, file: String, dest: File): File? {
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            val conn = URL(WallpaperLogic.backgroundUrl(themeId, file)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode != 200) {
                    Log.w(TAG, "background $themeId/$file: HTTP ${conn.responseCode}")
                    return null
                }
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            if (tmp.renameTo(dest)) dest else null
        } catch (e: Exception) {
            Log.w(TAG, "background $themeId/$file: ${e.javaClass.simpleName} ${e.message}")
            tmp.delete()
            null
        }
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
            val error = setBitmap(app, bmp, prefs.effectiveTarget)
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
            setBitmap(context, bmp, prefs.effectiveTarget)
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
        val wm = WallpaperManager.getInstance(context)
        wm.setBitmap(bitmap, null, true, flags)
        val prefs = WallpaperPrefs(context)
        if (flags and WallpaperManager.FLAG_SYSTEM != 0) prefs.systemId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        if (flags and WallpaperManager.FLAG_LOCK != 0) prefs.lockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        null
    } catch (e: Exception) {
        e.message ?: "set"
    }
}
