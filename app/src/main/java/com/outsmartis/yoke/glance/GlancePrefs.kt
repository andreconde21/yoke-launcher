package com.outsmartis.yoke.glance

import android.content.Context
import androidx.core.content.edit

/** Settings of the home glance card (next event and weather). Off by default; its own file like WeatherPrefs. */
class GlancePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yoke.glance", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var source: GlanceSource
        get() = GlanceSource.fromName(prefs.getString("source", null))
        set(value) = prefs.edit { putString("source", value.name) }

    /** The weather half; it also needs weather itself to be on in the weather settings. */
    var showWeather: Boolean
        get() = prefs.getBoolean("show_weather", true)
        set(value) = prefs.edit { putBoolean("show_weather", value) }
}
