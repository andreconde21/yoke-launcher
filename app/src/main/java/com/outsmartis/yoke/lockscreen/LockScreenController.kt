package com.outsmartis.yoke.lockscreen

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.MyAccessibilityService

/**
 * The opt-in Yoke lock screen: a themed glance screen shown over the system keyguard. When the
 * display turns off, [LockScreenService] calls [onScreenOff], which puts [LockScreenActivity] up
 * so it is already there at wake. Unlocking stays the system's (requestDismissKeyguard).
 *
 * Starting an activity from the background is blocked on Android 10+, so the start goes, in order:
 * from the accessibility service when it is connected (a system-bound accessibility service is an
 * exemption), else a plain start (allowed if another exemption applies), and when the screen has
 * not come up shortly after, a silent full-screen-intent notification, which the system launches
 * directly while the screen is off.
 */
object LockScreenController {

    const val TAG = "YokeLock"
    const val CHANNEL_SERVICE = "yoke_lock_service"
    const val CHANNEL_LAUNCH = "yoke_lock_launch"
    const val NOTIFY_SERVICE = 3701
    const val NOTIFY_LAUNCH = 3702
    private const val LAUNCH_CHECK_MS = 800L

    private val main = Handler(Looper.getMainLooper())

    fun isEnabled(context: Context): Boolean = LockScreenPrefs(context).enabled

    /**
     * The on/off switch. Today the temporary Settings row calls it; YOK-036's "Lock screen"
     * setting calls it with true for its "Yoke lock screen" level and false for every other level.
     */
    fun setEnabled(context: Context, on: Boolean) {
        LockScreenPrefs(context).enabled = on
        sync(context)
        if (!on) NotificationManagerCompat.from(context).cancel(NOTIFY_LAUNCH)
    }

    /**
     * Starts or stops the service to match the setting. Call from the foreground (Settings, home
     * resuming): Android 12+ refuses foreground-service starts from the background.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, LockScreenService::class.java)
        if (!LockScreenPrefs(app).enabled) {
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

    /** The display just turned off. Runs on the main thread (the service's receiver). */
    fun onScreenOff(context: Context) {
        val app = context.applicationContext
        val decision = decide(app)
        if (decision != LockScreenPolicy.Decision.SHOW) {
            Log.d(TAG, "not shown: $decision")
            return
        }
        startDirect(app)
        main.postDelayed({
            val up = LockScreenActivity.alive
            if (LockScreenPolicy.needsFullScreenFallback(up, canUseFullScreenIntent(app))) {
                Log.d(TAG, "direct start blocked, posting full-screen intent")
                postFullScreen(app)
            } else Log.d(TAG, if (up) "shown by direct start" else "direct start blocked, no full-screen permission")
        }, LAUNCH_CHECK_MS)
    }

    fun decide(context: Context): LockScreenPolicy.Decision {
        val audio = context.getSystemService(AudioManager::class.java)
        val usages = try {
            audio?.activePlaybackConfigurations?.map { it.audioAttributes.usage }.orEmpty()
        } catch (e: RuntimeException) {
            emptyList()
        }
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: false
        return LockScreenPolicy.decide(isEnabled(context), unlocked, audio?.mode ?: LockScreenPolicy.MODE_NORMAL, usages)
    }

    /** At least one launch path is open: the accessibility service, or full-screen notifications. */
    fun canLaunch(context: Context): Boolean =
        MyAccessibilityService.instance != null || canUseFullScreenIntent(context)

    /** Notifications allowed and, on Android 14+, the "full-screen notifications" special access. */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    }

    /** The system screen for the Android 14+ "full-screen notifications" access, or null below 14. */
    fun fullScreenSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + context.packageName))
        } else null

    private fun startDirect(context: Context) {
        val intent = LockScreenActivity.intent(context)
        // The system-bound accessibility service exempts the app from the background start block
        val starter: Context = MyAccessibilityService.instance ?: context
        try {
            starter.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "direct start failed", e)
        }
    }

    @SuppressLint("MissingPermission") // checked by canUseFullScreenIntent; SecurityException caught below
    private fun postFullScreen(context: Context) {
        ensureChannels(context)
        val pending = PendingIntent.getActivity(
            context, 0, LockScreenActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_lock_screen)
            .setContentTitle(context.getString(R.string.lock_screen_yoke_setting))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_LAUNCH, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "no notification permission", e)
        }
    }

    /**
     * The service's quiet channel, and the launch channel: full-screen intents only fire from a
     * high-importance channel, so it is high but silent and without vibration.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, context.getString(R.string.lock_screen_yoke_service_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LAUNCH, context.getString(R.string.lock_screen_yoke_launch_channel), NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
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
