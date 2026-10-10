package com.outsmartis.yoke.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * A coarse one-off device fix from the platform LocationManager (no Google Play services).
 * Only used when the user chose "Current location"; never listens in the background.
 */
object WeatherLocation {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
    private const val TIMEOUT_MS = 15_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Calls [onFix] once, on an arbitrary thread, with latitude/longitude or null when there is no fix. */
    @SuppressLint("MissingPermission")
    fun fix(context: Context, onFix: (Pair<Double, Double>?) -> Unit) {
        val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null || !hasPermission(context)) return onFix(null)
        try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (providers.isEmpty()) return onFix(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                val timeout = Handler(Looper.getMainLooper())
                val direct = Executor { it.run() }
                var done = false
                val cancel = Runnable { signal.cancel() }
                timeout.postDelayed(cancel, TIMEOUT_MS)
                manager.getCurrentLocation(providers.first(), signal, direct) { location ->
                    timeout.removeCallbacks(cancel)
                    if (done) return@getCurrentLocation
                    done = true
                    onFix(location?.toPair() ?: lastKnown(manager, providers))
                }
            } else {
                onFix(lastKnown(manager, providers))
            }
        } catch (e: SecurityException) {
            onFix(null)
        } catch (e: Exception) {
            onFix(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Pair<Double, Double>? =
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.toPair()

    private fun Location.toPair() = latitude to longitude
}
