package com.outsmartis.yoke.lockscreen

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.R

/**
 * Keeps Yoke alive and listening for the display turning off while the Yoke lock screen is on.
 * Screen broadcasts only reach receivers registered at runtime, hence a service and not a
 * manifest receiver. No wake locks, no polling: it sleeps until ACTION_SCREEN_OFF.
 *
 * Foreground type specialUse (see the manifest property): none of the typed ones fit a
 * "react to the screen turning off" service. Not direct-boot aware, so it never runs before the
 * first unlock after a reboot.
 */
class LockScreenService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> LockScreenController.onScreenOff(context)
                Intent.ACTION_SCREEN_ON -> LockScreenController.onScreenOn()
            }
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LockScreenController.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, LockScreenController.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_lock_screen)
            .setContentTitle(getString(R.string.lock_screen_yoke_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        try {
            ServiceCompat.startForeground(
                this, LockScreenController.NOTIFY_SERVICE, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: RuntimeException) {
            Log.w(LockScreenController.TAG, "startForeground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // Checked after startForeground: a service started with startForegroundService that stops first crashes
        if (!LockScreenPolicy.shouldRunService(LockScreenPrefs(this).enabled, LockScreenController.launchPathOpen())) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            registered = false
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
            }
        }
        super.onDestroy()
    }
}
