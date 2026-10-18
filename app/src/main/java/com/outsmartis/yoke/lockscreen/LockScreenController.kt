package com.outsmartis.yoke.lockscreen

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.MyAccessibilityService

/**
 * The opt-in Yoke lock screen: a themed glance screen shown over the system keyguard. When the
 * display turns off, [LockScreenService] calls [onScreenOff], which puts [LockScreenActivity] up
 * so it is already there at wake. Unlocking stays the system's (requestDismissKeyguard).
 *
 * Starting an activity from the background is blocked on Android 10+; the one path used is a
 * start from the connected accessibility service (a system-bound accessibility service is an
 * exemption). There is deliberately no full-screen-intent fallback: the system wakes the screen
 * for those, which would loop (screen off, wake, screen off).
 *
 * Every launch re-checks [decide] right before the start, and the activity re-checks in onCreate.
 * Known limit: whether another show-when-locked app (camera, emergency dialer) is in front is
 * judged from the accessibility service's last foreground package and a fixed package list, so an
 * unlisted app could still be covered; it leaves on its own when the screen is on and it stops.
 */
object LockScreenController {

    const val TAG = "YokeLock"
    const val CHANNEL_SERVICE = "yoke_lock_service"
    const val NOTIFY_SERVICE = 3701

    /** elapsedRealtime of the last screen-on / screen-off broadcast (main thread). */
    @Volatile var screenOnAt = 0L
        private set
    @Volatile var screenOffAt = 0L
        private set

    // Dialers and emergency UIs that show over the lock screen; the secure camera is added by intent
    private val EXTRA_PROTECTED = setOf(
        // Emergency UIs only. Dialers are left out: the foreground package only changes on window-state events,
        // so a dialer opened while unlocked would block the lock screen until another app came to the front.
        // A call in progress is already handled by the audio mode in the policy.
        "com.android.emergency", "com.samsung.android.emergency",
    )

    fun isEnabled(context: Context): Boolean = LockScreenPrefs(context).enabled

    /** The one launch path: the accessibility service is connected. */
    fun launchPathOpen(): Boolean = MyAccessibilityService.instance != null

    /**
     * The on/off switch. Today the temporary Settings row calls it; YOK-036's "Lock screen"
     * setting calls it with true for its "Yoke lock screen" level and false for every other level.
     */
    fun setEnabled(context: Context, on: Boolean) {
        LockScreenPrefs(context).enabled = on
        sync(context)
    }

    /**
     * Starts or stops the service to match the setting (and the launch path: with the pref on but
     * no accessibility service, e.g. after a backup restore, it stays stopped). Call from the
     * foreground (Settings, home resuming): Android 12+ refuses foreground-service starts from the background.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, LockScreenService::class.java)
        if (!LockScreenPolicy.shouldRunService(LockScreenPrefs(app).enabled, launchPathOpen())) {
            app.stopService(intent)
            return
        }
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (an IllegalStateException) when not in front; home retries on resume
            Log.w(TAG, "service not started", e)
        }
    }

    fun onScreenOn() {
        screenOnAt = SystemClock.elapsedRealtime()
    }

    /** The display just turned off. Runs on the main thread (the service's receiver). */
    fun onScreenOff(context: Context) {
        val app = context.applicationContext
        val offAt = SystemClock.elapsedRealtime()
        screenOffAt = offAt
        val decision = decide(app)
        if (decision != LockScreenPolicy.Decision.SHOW) {
            Log.d(TAG, "not shown: $decision")
            return
        }
        // Re-checked at the last moment: a double-press camera or a call can land in between
        if (!interactive(app)) startDirect(app, offAt) else Log.d(TAG, "not shown: ${LockScreenPolicy.Decision.SCREEN_ON}")
    }

    /** The full launch decision from live state. Also called by the activity's onCreate. */
    fun decide(context: Context): LockScreenPolicy.Decision {
        val audio = context.getSystemService(AudioManager::class.java)
        val usages = try {
            audio?.activePlaybackConfigurations?.map { it.audioAttributes.usage }.orEmpty()
        } catch (e: RuntimeException) {
            emptyList()
        }
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: false
        val km = context.getSystemService(KeyguardManager::class.java)
        val lockPending = km != null && (km.isKeyguardLocked || km.isDeviceSecure)
        return LockScreenPolicy.decide(
            enabled = isEnabled(context),
            launchPathOpen = launchPathOpen(),
            userUnlocked = unlocked,
            audioMode = audio?.mode ?: LockScreenPolicy.MODE_NORMAL,
            playingUsages = usages,
            interactive = interactive(context),
            lockPending = lockPending,
            otherActivityInFront = LockScreenPolicy.isProtectedForeground(
                MyAccessibilityService.foregroundPackage, protectedPackages(context),
            ),
        )
    }

    fun interactive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Packages whose UI shows over the lock screen and must not be covered: the secure camera and emergency UIs. */
    private fun protectedPackages(context: Context): Set<String> {
        val found = HashSet(EXTRA_PROTECTED)
        try {
            val pm = context.packageManager
            // Only the secure camera shows over the lock screen; the normal camera app would stick (see above)
            pm.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE), 0)
                .forEach { found.add(it.activityInfo.packageName) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "protected packages", e)
        }
        return found
    }

    private fun startDirect(context: Context, offAt: Long) {
        val intent = LockScreenActivity.intent(context, offAt)
        // The system-bound accessibility service exempts the app from the background start block
        val starter: Context = MyAccessibilityService.instance ?: context
        try {
            starter.startActivity(intent)
            Log.d(TAG, "shown by direct start")
        } catch (e: RuntimeException) {
            Log.w(TAG, "direct start failed", e)
        }
    }

    /** The service's quiet notification channel. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, context.getString(R.string.lock_screen_yoke_service_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    /** Whether the system shows notifications on the lock screen. Unreadable: assume yes (the content rule still applies). */
    fun systemShowsNotificationsOnLock(context: Context): Boolean =
        secureInt(context, "lock_screen_show_notifications", 1) != 0

    /** Whether the user allows sensitive notification content on the lock screen. Unreadable: hide it. */
    fun systemAllowsPrivateOnLock(context: Context): Boolean =
        secureInt(context, "lock_screen_allow_private_notifications", 0) != 0

    private fun secureInt(context: Context, key: String, fallback: Int): Int = try {
        Settings.Secure.getInt(context.contentResolver, key, fallback)
    } catch (e: RuntimeException) {
        fallback
    }
}
