package com.outsmartis.yoke.phone

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager

/**
 * Telecom binds this once Yoke is the phone app (IN_CALL_SERVICE_UI). It only tracks calls and
 * shows the placeholder call screen: the call itself lives in the telephony stack, so if this
 * `:phone` process dies Telecom rebinds or falls back to the system call screen and the call goes on.
 * Telecom plays the ringtone itself (no IN_CALL_SERVICE_RINGING), and no call is ever filtered:
 * emergency calls show like any other.
 */
class YokeInCallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_SELECT_PHONE_ACCOUNT) pickAccount(call)
            PhoneCalls.update(call)
            refresh()
        }
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        PhoneCalls.add(call)
        val state = PhoneCalls.stateOf(call)
        if (state == Call.STATE_SELECT_PHONE_ACCOUNT) pickAccount(call)
        refresh()
        // A ringing call reaches the user through the full-screen notification; one just placed opens the screen.
        if (CallPhase.of(state) != CallPhase.RINGING) InCallActivity.open(this)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        PhoneCalls.remove(call)
        refresh()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        InCallActivity.open(this)
    }

    override fun onDestroy() {
        CallNotification.cancel(this)
        super.onDestroy()
    }

    private fun refresh() = CallNotification.show(this, PhoneCalls.registry.screen())

    /**
     * Dual SIM with no default: Telecom waits for a SIM. Until YOK-040's picker exists, take the
     * first one so the call isn't left hanging.
     */
    private fun pickAccount(call: Call) {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val account = runCatching { getSystemService(TelecomManager::class.java).callCapablePhoneAccounts.firstOrNull() }.getOrNull()
            ?: return
        runCatching { call.phoneAccountSelected(account, false) }
    }
}
