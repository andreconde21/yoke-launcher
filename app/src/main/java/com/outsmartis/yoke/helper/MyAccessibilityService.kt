package com.outsmartis.yoke.helper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.outsmartis.yoke.R
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.grayscale.GrayscaleController

class MyAccessibilityService : AccessibilityService() {

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            GrayscaleController.evaluate(applicationContext)
        }
    }
    private var receiverRegistered = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        instance = this
        Prefs(applicationContext).lockModeOn = true
        super.onServiceConnected()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this, screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        GrayscaleController.evaluate(applicationContext)
    }

    private fun unregisterScreenOn() {
        if (!receiverRegistered) return
        receiverRegistered = false
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Smart grayscale: which app is in front. Only the package name is read.
            GrayscaleController.evaluate(applicationContext, event.packageName?.toString())
            return
        }
        // The lock click only counts on Yoke's own views; the service now hears every app.
        if (event.packageName?.toString() != packageName) return
        try {
            val source: AccessibilityNodeInfo = event.source ?: return
            if (source.className != "android.widget.FrameLayout") return

            when (source.contentDescription) {
                getString(R.string.lock_layout_description) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
                // Home button for recents feature disabled
                // getString(R.string.recents_layout_description) -> {
                //     performGlobalAction(GLOBAL_ACTION_RECENTS)
                // }
            }
        } catch (e: Exception) {
            return
        }
    }

    override fun onInterrupt() {

    }

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenOn()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenOn()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** The connected service, used by gesture actions that need a global action (lock, recents). */
        @Volatile
        var instance: MyAccessibilityService? = null
            private set
    }
}