package com.outsmartis.yoke.weather

import android.content.Context
import androidx.core.content.edit

/** Weather settings and the cached reading. Its own file so it never mixes with launcher prefs. */
class WeatherPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("com.outsmartis.yoke.weather", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("ENABLED", false)
        set(value) = prefs.edit { putBoolean("ENABLED", value) }

    var units: TempUnit
        get() = TempUnit.fromName(prefs.getString("UNITS", null))
        set(value) = prefs.edit { putString("UNITS", value.name) }

    var showHighLow: Boolean
        get() = prefs.getBoolean("HIGH_LOW", true)
        set(value) = prefs.edit { putBoolean("HIGH_LOW", value) }

    /** Use a coarse device fix instead of the chosen city. Only ever true after the permission was granted. */
    var useCurrentLocation: Boolean
        get() = prefs.getBoolean("USE_CURRENT", false)
        set(value) = prefs.edit { putBoolean("USE_CURRENT", value) }

    var city: GeoPlace?
        get() {
            val name = prefs.getString("CITY_NAME", null) ?: return null
            val lat = prefs.getString("CITY_LAT", null)?.toDoubleOrNull() ?: return null
            val lon = prefs.getString("CITY_LON", null)?.toDoubleOrNull() ?: return null
            return GeoPlace(name, prefs.getString("CITY_ADMIN1", null), prefs.getString("CITY_COUNTRY", null), lat, lon)
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove("CITY_NAME"); remove("CITY_ADMIN1"); remove("CITY_COUNTRY"); remove("CITY_LAT"); remove("CITY_LON")
            } else {
                putString("CITY_NAME", value.name)
                putString("CITY_ADMIN1", value.admin1)
                putString("CITY_COUNTRY", value.country)
                putString("CITY_LAT", value.latitude.toString())
                putString("CITY_LON", value.longitude.toString())
            }
        }

    var reading: WeatherReading?
        get() = WeatherReading.fromJson(prefs.getString("READING", null))
        set(value) = prefs.edit { if (value == null) remove("READING") else putString("READING", value.toJson()) }

    /** Settings that change what the reading means: drop it so home never shows the old place or units. */
    fun clearReading() { reading = null }
}
