package com.outsmartis.yoke.limits

import android.content.Context
import android.content.pm.ApplicationInfo
import com.outsmartis.yoke.system.ShizukuAccess
import kotlin.concurrent.thread

/**
 * Level C: limited apps Android itself keeps paused (`pm suspend`, run as the shell user through
 * Shizuku). A suspended app is greyed out everywhere, can't be started and its notifications are
 * hidden; Yoke lifts it for one visit after the pause screen. Without Shizuku nothing is suspended
 * and levels A and B still apply.
 *
 * The suspension belongs to the shell, so it outlives Yoke: [releaseAll] before uninstalling.
 */
object HardPause {

    private const val DIALOG = "Paused by Yoke. Open it from Yoke."

    fun isSuspended(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SUSPENDED != 0
    }.getOrDefault(false)

    /** Blocking. True when [pkg] ends up suspended. */
    fun suspend(context: Context, pkg: String): Boolean {
        if (!ShizukuAccess.isReady(context)) return false
        if (isSuspended(context, pkg)) return true
        runCatching {
            val r = ShizukuAccess.run("pm", "suspend", "--dialogMessage", DIALOG, pkg)
            if (!r.ok) ShizukuAccess.run("pm", "suspend", pkg)
        }.onFailure { it.printStackTrace() }
        return isSuspended(context, pkg)
    }

    /** Blocking. True when [pkg] ends up not suspended. */
    fun unsuspend(context: Context, pkg: String): Boolean {
        if (!isSuspended(context, pkg)) return true
        if (!ShizukuAccess.isReady(context)) return false
        runCatching { ShizukuAccess.run("pm", "unsuspend", pkg) }.onFailure { it.printStackTrace() }
        return !isSuspended(context, pkg)
    }

    /** Suspends every hard-paused app that isn't currently let through. Runs in the background. */
    fun applyAll(context: Context) {
        val app = context.applicationContext
        val prefs = LimitPrefs(app)
        val targets = prefs.hard.intersect(prefs.limited) - setOfNotNull(prefs.passPackage)
        if (targets.isEmpty() || !ShizukuAccess.isReady(app)) return
        thread(name = "yoke-hard-pause") { targets.forEach { suspend(app, it) } }
    }

    /** Lifts [pkgs] in the background (limit or hard pause turned off). */
    fun release(context: Context, pkgs: Collection<String>) {
        val app = context.applicationContext
        if (pkgs.isEmpty()) return
        thread(name = "yoke-hard-release") { pkgs.forEach { unsuspend(app, it) } }
    }

    fun releaseAll(context: Context) = release(context, LimitPrefs(context).hard)
}
