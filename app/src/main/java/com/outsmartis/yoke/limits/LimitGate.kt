package com.outsmartis.yoke.limits

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.outsmartis.yoke.grayscale.GrayscaleController

/**
 * Every way into a limited app goes through here.
 * - Level A: Yoke's own launch paths call [intercept] before starting an app.
 * - Level B: the accessibility service reports the app in front ([onForeground]), so opening a
 *   limited app from a notification, recents or a link also gets the pause screen.
 * - Level C: [HardPause] keeps hard-paused apps suspended between visits.
 * A visit lasts until the user leaves the app (another app in front, Yoke's home, or screen off).
 */
object LimitGate {

    /** Yoke's home may flash by while a let-through app starts; that must not end the visit. */
    private const val PASS_GRACE_MS = 3000L

    /** What to open once the pause is over. */
    sealed class Target {
        class App(val component: ComponentName, val user: UserHandle) : Target()
        class Shortcut(val id: String, val user: UserHandle) : Target()
        class Pending(val intent: PendingIntent) : Target()
        /** The app is already in front, under the pause screen (level B). */
        object InFront : Target()
    }

    /** True when the pause screen took over; the caller must not start the app itself. */
    fun intercept(context: Context, pkg: String, target: Target): Boolean {
        val prefs = LimitPrefs(context)
        if (!prefs.isLimited(pkg) || prefs.passPackage == pkg) return false
        PauseActivity.start(context, pkg, target)
        return true
    }

    fun onForeground(context: Context, pkg: String?) {
        val app = context.applicationContext
        val prefs = LimitPrefs(app)
        if (prefs.limited.isEmpty() && prefs.passPackage == null) return
        val ignored = ignoredPackages(app)
        if (LimitPolicy.endsPass(pkg, prefs.passPackage, ignored)) endVisit(app)
        if (LimitPolicy.shouldPause(pkg, prefs.limited, prefs.passPackage, ignored) && PauseActivity.showingFor != pkg) {
            PauseActivity.start(app, pkg!!, Target.InFront)
        }
    }

    /** Yoke's home is in front: any visit is over. */
    fun onHomeShown(context: Context) {
        val prefs = LimitPrefs(context)
        if (prefs.passPackage != null && System.currentTimeMillis() - prefs.passAt < PASS_GRACE_MS) return
        endVisit(context)
    }

    fun onScreenOff(context: Context) = endVisit(context)

    /** The user waited and chose Open: let [pkg] through until they leave it. */
    fun startVisit(context: Context, pkg: String) {
        val prefs = LimitPrefs(context)
        prefs.recordOpen(pkg)
        prefs.passPackage = pkg
    }

    private fun endVisit(context: Context) {
        val prefs = LimitPrefs(context)
        if (prefs.passPackage != null) prefs.passPackage = null
        HardPause.applyAll(context)
    }

    private fun ignoredPackages(context: Context): Set<String> =
        GrayscaleController.ignoredPackages(context) + context.packageName
}
