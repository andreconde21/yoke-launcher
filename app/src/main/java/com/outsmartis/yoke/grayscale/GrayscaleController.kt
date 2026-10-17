package com.outsmartis.yoke.grayscale

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.inputmethod.InputMethodManager
import android.content.ComponentName
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.showToast

/** Reads and writes the system grayscale and runs the policy. Everything calls [evaluate]. */
object GrayscaleController {

    const val PAUSE_MINUTES = 15

    /** CarMirror repeats its signal every 4 min; without one for this long it has stopped or crashed. */
    const val CAR_MIRROR_TTL_MS = 10 * 60_000L
    const val ADB_COMMAND = "adb shell pm grant %s android.permission.WRITE_SECURE_SETTINGS"

    private const val ENABLED_KEY = "accessibility_display_daltonizer_enabled"
    private const val MODE_KEY = "accessibility_display_daltonizer"
    private val ALWAYS_IGNORED = setOf("com.android.systemui", "android")

    fun grantCommand(context: Context) = ADB_COMMAND.format(context.packageName)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Package names of enabled keyboards, which flash by when typing and must not flip the screen. */
    fun ignoredPackages(context: Context): Set<String> {
        val imes = try {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        return ALWAYS_IGNORED + imes
    }

    /**
     * Re-decides and writes only on change. [foregroundPackage] comes from a window event;
     * null re-uses the last real foreground package.
     */
    fun evaluate(context: Context, foregroundPackage: String? = null, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = GrayscalePrefs(app)
        try {
            if (!prefs.featureOn) {
                restore(app, prefs)
                return
            }
            val decision = GrayscalePolicy.decide(
                featureOn = true,
                pausedUntil = prefs.pausedUntil,
                now = now,
                foregroundPackage = foregroundPackage,
                exceptions = prefs.exceptions,
                ignoredPackages = ignoredPackages(app),
                lastPackage = prefs.lastPackage,
                carMirrorUntil = if (prefs.carColour) prefs.carMirrorUntil else 0L,
            )
            if (decision.rememberedPackage != prefs.lastPackage) prefs.lastPackage = decision.rememberedPackage
            val seen = foregroundPackage?.takeIf { it.isNotBlank() && it !in ignoredPackages(app) }
            if (seen != null && prefs.recent.firstOrNull() != seen) prefs.recent = GrayscalePolicy.pushRecent(prefs.recent, seen)
            if (hasPermission(app)) {
                if (decision.grayscale && prefs.previous == null) {
                    prefs.previous = GrayscalePolicy.previousToRemember(read(app))
                }
                write(app, decision.grayscale, prefs.previous)
            }
            scheduleRecheck(app, prefs.pausedUntil, prefs.carMirrorUntil, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restore(context: Context, prefs: GrayscalePrefs) {
        val previous = prefs.previous ?: return
        if (hasPermission(context)) write(context, false, previous)
        prefs.previous = null
        cancelPauseAlarm(context)
    }

    /** CarMirror says the phone is (or no longer is) on a car screen; see [CarMirrorReceiver]. */
    fun carMirror(context: Context, active: Boolean, now: Long = System.currentTimeMillis()) {
        GrayscalePrefs(context).carMirrorUntil = if (active) now + CAR_MIRROR_TTL_MS else 0L
        evaluate(context, now = now)
    }

    private fun read(context: Context): DaltonizerState {
        val cr = context.contentResolver
        return DaltonizerState(
            Settings.Secure.getInt(cr, ENABLED_KEY, 0) == 1,
            Settings.Secure.getInt(cr, MODE_KEY, -1),
        )
    }

    private fun write(context: Context, grayscale: Boolean, previous: DaltonizerState?) {
        val target = GrayscalePolicy.resolve(grayscale, read(context), previous) ?: return
        val cr = context.contentResolver
        try {
            target.mode?.let { Settings.Secure.putInt(cr, MODE_KEY, it) }
            Settings.Secure.putInt(cr, ENABLED_KEY, if (target.enabled) 1 else 0)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun setFeatureOn(context: Context, on: Boolean) {
        val prefs = GrayscalePrefs(context)
        prefs.featureOn = on
        if (!on) prefs.pausedUntil = 0L
        evaluate(context)
        refreshTile(context)
    }

    /** Gesture, palette and tile entry point. Toasts the setup hint when the permission is missing. */
    fun toggle(context: Context) {
        val prefs = GrayscalePrefs(context)
        val turnOn = !prefs.featureOn
        setFeatureOn(context, turnOn)
        if (turnOn && !hasPermission(context)) context.showToast(R.string.grayscale_needs_permission)
        else context.showToast(if (turnOn) R.string.grayscale_toast_on else R.string.grayscale_toast_off)
    }

    fun pause(context: Context, minutes: Int = PAUSE_MINUTES) {
        val prefs = GrayscalePrefs(context)
        if (!prefs.featureOn) {
            context.showToast(R.string.grayscale_pause_needs_on)
            return
        }
        prefs.pausedUntil = System.currentTimeMillis() + minutes * 60_000L
        evaluate(context)
        context.showToast(context.getString(R.string.grayscale_toast_paused, minutes))
    }

    fun resume(context: Context) {
        GrayscalePrefs(context).pausedUntil = 0L
        evaluate(context)
    }

    fun refreshTile(context: Context) {
        try {
            TileService.requestListeningState(context, ComponentName(context, GrayscaleTileService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Pause end, CarMirror signal lapsing

    private fun pauseIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, GrayscaleReceiver::class.java).setAction(GrayscaleReceiver.ACTION_RECHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleRecheck(context: Context, pausedUntil: Long, carMirrorUntil: Long, now: Long) {
        val next = listOf(pausedUntil, carMirrorUntil).filter { it > now }.minOrNull() ?: return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.set(AlarmManager.RTC, next + 500, pauseIntent(context))
    }

    private fun cancelPauseAlarm(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pauseIntent(context))
    }
}

/**
 * Fires when a pause ends, or CarMirror's signal lapses, so the screen goes grayscale again
 * without waiting for a window change.
 */
class GrayscaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GrayscaleController.evaluate(context)
    }

    companion object {
        const val ACTION_RECHECK = "com.outsmartis.yoke.GRAYSCALE_RECHECK"
    }
}
