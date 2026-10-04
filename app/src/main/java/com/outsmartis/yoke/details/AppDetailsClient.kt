package com.outsmartis.yoke.details

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Launcher side of the "launcher details provider" contract: an app that declares
 * `<meta-data android:name="com.outsmartis.launcher.DETAILS_AUTHORITY">` serves `/items`
 * and `/summary` from a provider guarded by [READ_PERMISSION] (API 31+ only).
 */
class AppDetailsClient(context: Context) {

    private val context = context.applicationContext

    sealed class Result {
        data class Ok(val details: AppDetails) : Result()
        /** The platform cannot grant the permission (API < 31). */
        object NeedsNewerAndroid : Result()
        /** Permission denied, provider missing or failing. */
        object Unavailable : Result()
    }

    /** The authority [packageName] declares, or null when it has no details provider. */
    fun authorityOf(packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString(META_DATA_KEY)?.takeIf { it.isNotBlank() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Reads the sheet's content. Blocking: call off the main thread. */
    fun load(authority: String): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Result.NeedsNewerAndroid
        return try {
            val items = query(authority, "items") { DetailsMapper.item(it) }
            val summary = query(authority, "summary") { DetailsMapper.summary(it) }.firstOrNull()
            Result.Ok(AppDetails(authority, summary, DetailsMapper.order(items)))
        } catch (_: SecurityException) {
            Result.Unavailable
        } catch (_: RuntimeException) {
            Result.Unavailable
        }
    }

    /** Calls [onChange] on the main thread when the provider reports new data. Pair with [unobserve]. */
    fun observe(authority: String, onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        try {
            context.contentResolver.registerContentObserver(Uri.parse("content://$authority/"), true, observer)
        } catch (_: RuntimeException) {
        }
        return observer
    }

    fun unobserve(observer: ContentObserver) = context.contentResolver.unregisterContentObserver(observer)

    private fun <T> query(authority: String, path: String, map: (RowSource) -> T): List<T> =
        context.contentResolver.query(Uri.parse("content://$authority/$path"), null, null, null, null)?.use { c ->
            val row = CursorRow(c)
            buildList { while (c.moveToNext()) add(map(row)) }
        } ?: throw IllegalStateException("provider returned no cursor")

    private class CursorRow(private val c: Cursor) : RowSource {
        private fun idx(name: String): Int = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) } ?: -1
        override fun string(column: String) = idx(column).takeIf { it >= 0 }?.let(c::getString)
        override fun int(column: String) = idx(column).takeIf { it >= 0 }?.let(c::getInt)
        override fun long(column: String) = idx(column).takeIf { it >= 0 }?.let(c::getLong)
    }

    companion object {
        const val META_DATA_KEY = "com.outsmartis.launcher.DETAILS_AUTHORITY"
        const val READ_PERMISSION = "com.outsmartis.permission.READ_LAUNCHER_DETAILS"
    }
}
