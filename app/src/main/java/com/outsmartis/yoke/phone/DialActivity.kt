package com.outsmartis.yoke.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.widget.Toast
import com.outsmartis.yoke.R
import com.outsmartis.yoke.databinding.ActivityDialBinding

/**
 * ACTION_DIAL (with a `tel:` number or none): a plain number field and a Call button until
 * YOK-040's keypad. Calls go to TelecomManager.placeCall; CALL_PHONE is asked at that moment if
 * the role hasn't granted it. Emergency numbers are never held back behind that dialog.
 */
class DialActivity : Activity() {

    companion object {
        private const val REQUEST_CALL = 1
    }

    private lateinit var binding: ActivityDialBinding
    private var pending: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showNumber(intent)
        binding.dialCall.setOnClickListener { call(binding.dialNumber.text.toString().trim()) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNumber(intent)
    }

    private fun showNumber(intent: Intent?) {
        val number = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        binding.dialNumber.setText(number)
        binding.dialNumber.setSelection(number.length)
    }

    private fun call(number: String) {
        if (number.isEmpty()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            place(number)
        } else if (isEmergency(number)) {
            handToSystemDialer(number)
        } else {
            pending = number
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CALL) return
        val number = pending ?: return
        pending = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) place(number)
        else Toast.makeText(this, R.string.phone_needs_permission, Toast.LENGTH_LONG).show()
    }

    private fun place(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        val telecom = getSystemService(TelecomManager::class.java)
        val placed = runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), Bundle()) }.isSuccess
        when {
            placed -> finish()
            isEmergency(number) -> handToSystemDialer(number)
            else -> Toast.makeText(this, R.string.phone_call_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun isEmergency(number: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { getSystemService(TelephonyManager::class.java).isEmergencyNumber(number) }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            PhoneNumberUtils.isEmergencyNumber(number)
        }

    /** The system dialer always places emergency calls; sending it there explicitly can't loop back to Yoke. */
    private fun handToSystemDialer(number: String) {
        val systemDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { getSystemService(TelecomManager::class.java).systemDialerPackage }.getOrNull()
        } else null
        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        if (systemDialer != null && systemDialer != packageName &&
            runCatching { startActivity(dial.setPackage(systemDialer)) }.isSuccess
        ) {
            finish()
            return
        }
        Toast.makeText(this, R.string.phone_call_failed, Toast.LENGTH_LONG).show()
    }
}
