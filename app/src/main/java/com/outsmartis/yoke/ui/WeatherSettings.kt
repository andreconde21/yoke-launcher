package com.outsmartis.yoke.ui

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.showPopupMenu
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.weather.GeoPlace
import com.outsmartis.yoke.weather.TempUnit
import com.outsmartis.yoke.weather.WeatherLocation
import com.outsmartis.yoke.weather.WeatherPrefs
import com.outsmartis.yoke.weather.WeatherRepository

/**
 * The four weather rows on the Settings screen: on/off, location, units, high/low.
 * [requestPermission] launches the ACCESS_COARSE_LOCATION request, whose result comes back to [onPermissionResult].
 */
class WeatherSettings(
    private val fragment: Fragment,
    private val toggle: TextView,
    private val location: TextView,
    private val units: TextView,
    private val highLow: TextView,
    private val requestPermission: ActivityResultLauncher<String>,
) {
    private val context get() = fragment.requireContext()
    private val prefs = WeatherPrefs(fragment.requireContext())

    init {
        toggle.setOnClickListener {
            prefs.enabled = !prefs.enabled
            if (prefs.enabled && prefs.city == null && !prefs.useCurrentLocation) {
                context.showToast(context.getString(R.string.weather_needs_location))
            }
            changed()
        }
        location.setOnClickListener { showLocationMenu(it) }
        units.setOnClickListener {
            prefs.units = if (prefs.units == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
            changed()
        }
        highLow.setOnClickListener {
            prefs.showHighLow = !prefs.showHighLow
            changed()
        }
        populate()
    }

    fun populate() {
        toggle.text = context.getString(if (prefs.enabled) R.string.on else R.string.off)
        location.text = when {
            prefs.useCurrentLocation -> context.getString(R.string.weather_location_current)
            else -> prefs.city?.name ?: context.getString(R.string.weather_location_none)
        }
        units.text = context.getString(if (prefs.units == TempUnit.CELSIUS) R.string.weather_units_c else R.string.weather_units_f)
        highLow.text = context.getString(if (prefs.showHighLow) R.string.on else R.string.off)
    }

    /** Home refreshes in onResume when the reading is missing or old; units and place changes drop it. */
    private fun changed(dropReading: Boolean = false) {
        if (dropReading) prefs.clearReading()
        populate()
    }

    private fun showLocationMenu(anchor: View) {
        val search = 1
        val current = 2
        anchor.showPopupMenu(configure = { menu ->
            menu.add(0, search, 0, R.string.weather_search_city)
            menu.add(0, current, 1, R.string.weather_use_current)
        }) { item ->
            when (item.itemId) {
                search -> askCity()
                current -> useCurrent()
            }
        }
    }

    private fun useCurrent() {
        if (WeatherLocation.hasPermission(context)) {
            prefs.useCurrentLocation = true
            changed(dropReading = true)
        } else {
            requestPermission.launch(WeatherLocation.PERMISSION)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            prefs.useCurrentLocation = true
            changed(dropReading = true)
        } else {
            prefs.useCurrentLocation = false
            context.showToast(
                context.getString(
                    if (prefs.city != null) R.string.weather_permission_denied else R.string.weather_permission_denied_no_city
                )
            )
            populate()
        }
    }

    private fun askCity() {
        lateinit var input: EditText
        context.createDialog(R.string.weather_search_title, R.string.weather_search, onAction = {
            val query = input.text.toString().trim()
            if (query.isNotEmpty()) search(query)
        }) {
            EditText(context, null, 0, R.style.TextSmall).also {
                input = it
                it.setHint(R.string.weather_city_hint)
                it.inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
                it.setSingleLine()
            }
        }.showRespectingStatusBar()
    }

    private fun search(query: String) {
        WeatherRepository.search(query) { results ->
            if (!fragment.isAdded) return@search
            when {
                results == null -> context.showToast(context.getString(R.string.weather_search_failed))
                results.isEmpty() -> context.showToast(context.getString(R.string.weather_no_results))
                else -> pick(results)
            }
        }
    }

    private fun pick(places: List<GeoPlace>) {
        var dialog: com.outsmartis.yoke.helper.YokeDialog? = null
        dialog = context.createDialog(R.string.weather_pick_title, R.string.close) {
            val (scroll, column) = scrollColumn(context, 0.4f)
            for (place in places) {
                column.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                    text = place.label
                    val pad = (10 * resources.displayMetrics.density).toInt()
                    setPadding(0, pad, 0, pad)
                    setOnClickListener {
                        dialog?.dismiss()
                        prefs.city = place
                        prefs.useCurrentLocation = false
                        changed(dropReading = true)
                    }
                })
            }
            scroll
        }
        dialog.showRespectingStatusBar()
    }
}
