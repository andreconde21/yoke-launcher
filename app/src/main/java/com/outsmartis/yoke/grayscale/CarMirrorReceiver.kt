package com.outsmartis.yoke.grayscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * CarMirror (github.com/andreconde21/tesla-car-mirror) shows the phone's apps on a car screen;
 * grayscale would reach the car too. While it streams it sends this every few minutes with
 * active=true, and active=false when the car disconnects. Without a repeat the signal lapses
 * after [GrayscaleController.CAR_MIRROR_TTL_MS], so a crashed CarMirror can't keep the phone in colour.
 *
 * Android 14 and newer tell us who sent it (CarMirror opts in); anyone else is ignored there.
 */
class CarMirrorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (Build.VERSION.SDK_INT >= 34 && sentFromPackage != CARMIRROR) return
        GrayscaleController.carMirror(context, intent.getBooleanExtra(EXTRA_ACTIVE, false))
    }

    companion object {
        const val ACTION = "com.outsmartis.yoke.action.CAR_MIRROR"
        const val EXTRA_ACTIVE = "active"
        const val CARMIRROR = "dev.outsmartis.carmirror"
    }
}
