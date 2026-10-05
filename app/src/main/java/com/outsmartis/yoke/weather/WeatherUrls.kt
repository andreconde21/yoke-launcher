package com.outsmartis.yoke.weather

import java.net.URLEncoder
import java.util.Locale

object WeatherUrls {

    /** Coordinates leave the device at 2 decimals (about 1 km), whatever their source. */
    fun coordinate(value: Double): String = String.format(Locale.US, "%.2f", value)

    fun forecast(latitude: Double, longitude: Double, units: TempUnit): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1" +
            if (units == TempUnit.FAHRENHEIT) "&temperature_unit=fahrenheit" else ""

    fun geocoding(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&count=5&language=en&format=json"
}
