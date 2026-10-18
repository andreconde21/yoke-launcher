package com.outsmartis.yoke.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.outsmartis.yoke.R

/**
 * The call notification: a full-screen intent while a call rings (that is what wakes the lock
 * screen into InCallActivity), a quiet ongoing one during a call. When Yoke may not post it
 * (notifications denied, or Android 14+ took the full-screen permission away) a ringing call
 * opens the screen directly instead; Telecom lets its bound in-call service start activities.
 */
internal object CallNotification {

    private const val CHANNEL_INCOMING = "phone_incoming"
    private const val CHANNEL_ONGOING = "phone_ongoing"
    private const val ID = 38

    fun show(context: Context, screen: CallScreen) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannels(context, manager)
        val number = { n: String? -> n ?: context.getString(R.string.phone_unknown_number) }
        val notification = when (screen) {
            is CallScreen.Incoming -> NotificationCompat.Builder(context, CHANNEL_INCOMING)
                .setContentTitle(context.getString(if (screen.waiting) R.string.phone_waiting else R.string.phone_incoming))
                .setContentText(number(screen.number))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(screenIntent(context, null), true)
                .addAction(0, context.getString(R.string.phone_decline), screenIntent(context, CallAction.DECLINE))
                .addAction(0, context.getString(R.string.phone_answer), screenIntent(context, CallAction.ANSWER))
            is CallScreen.Ongoing -> NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setContentTitle(context.getString(if (screen.phase == CallPhase.HOLDING) R.string.phone_on_hold else R.string.phone_on_call))
                .setContentText(number(screen.number))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, context.getString(R.string.phone_hang_up), screenIntent(context, CallAction.HANG_UP))
            else -> null
        }
        if (notification == null) {
            manager.cancel(ID)
            return
        }
        notification
            .setSmallIcon(R.drawable.ic_phone_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(screenIntent(context, null))
        val posted = canPost(context, manager, fullScreen = screen is CallScreen.Incoming) &&
            runCatching { manager.notify(ID, notification.build()) }.isSuccess
        if (!posted && screen is CallScreen.Incoming) InCallActivity.open(context)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    private fun canPost(context: Context, manager: NotificationManager, fullScreen: Boolean): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (fullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return manager.canUseFullScreenIntent()
        return true
    }

    private fun screenIntent(context: Context, action: CallAction?): PendingIntent = PendingIntent.getActivity(
        context, action?.ordinal?.plus(1) ?: 0, InCallActivity.intent(context, action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels(context: Context, manager: NotificationManager) {
        // Telecom rings; the channels stay silent so the ringtone isn't doubled.
        val incoming = NotificationChannel(
            CHANNEL_INCOMING, context.getString(R.string.phone_channel_incoming), NotificationManager.IMPORTANCE_HIGH,
        ).apply { setSound(null, null); enableVibration(false) }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING, context.getString(R.string.phone_channel_ongoing), NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannels(listOf(incoming, ongoing))
    }
}
