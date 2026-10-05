package com.outsmartis.yoke.weather

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Open-Meteo access. Weather is the only thing in Yoke that touches the network (it is why the
 * app holds INTERNET); keep it that way. Plain HttpURLConnection, off the main thread, no polling:
 * a refresh runs only when home becomes visible and the cached reading is old.
 */
object WeatherRepository {

    private const val TIMEOUT_MS = 10_000
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var inFlight = false

    /** Refresh when due (or [force]d); [onDone] runs on the main thread after a refresh attempt that ran. */
    fun refreshIfNeeded(context: Context, force: Boolean = false, onDone: () -> Unit) {
        val app = context.applicationContext
        val prefs = WeatherPrefs(app)
        if (!prefs.enabled || inFlight) return
        if (!force && !WeatherFormat.needsRefresh(prefs.reading, prefs.units, System.currentTimeMillis())) return
        inFlight = true
        val finish = { inFlight = false; main.post(onDone); Unit }
        val manual = prefs.city?.let { it.latitude to it.longitude }
        if (prefs.useCurrentLocation && WeatherLocation.hasPermission(app)) {
            WeatherLocation.fix(app) { fix -> fetchAndStore(prefs, fix ?: manual, finish) }
        } else {
            fetchAndStore(prefs, manual, finish)
        }
    }

    private fun fetchAndStore(prefs: WeatherPrefs, coords: Pair<Double, Double>?, finish: () -> Unit) {
        if (coords == null) return finish()
        val units = prefs.units
        executor.execute {
            try {
                val body = get(WeatherUrls.forecast(coords.first, coords.second, units))
                WeatherParser.parseForecast(body, System.currentTimeMillis(), units)?.let { prefs.reading = it }
            } catch (e: IOException) {
                // keep the cached reading; the home line hides it once it is 3 hours old
            } catch (e: JSONException) {
            } catch (e: RuntimeException) {
            } finally {
                finish()
            }
        }
    }

    /** City search for the settings picker. [onResult] runs on the main thread; null means the request failed. */
    fun search(query: String, onResult: (List<GeoPlace>?) -> Unit) {
        executor.execute {
            val result = try {
                WeatherParser.parseGeocoding(get(WeatherUrls.geocoding(query)))
            } catch (e: IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
            main.post { onResult(result) }
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
