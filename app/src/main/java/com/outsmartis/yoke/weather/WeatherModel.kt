package com.outsmartis.yoke.weather

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

enum class TempUnit(val symbol: String, val apiValue: String) {
    CELSIUS("°C", "celsius"),
    FAHRENHEIT("°F", "fahrenheit");

    companion object {
        fun fromName(name: String?): TempUnit = entries.firstOrNull { it.name == name } ?: CELSIUS
    }
}

/** One cached reading. [min]/[max] are today's low/high, null when the API omitted them. */
data class WeatherReading(
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val min: Double?,
    val max: Double?,
    val fetchedAt: Long,
    val units: TempUnit,
) {
    fun toJson(): String = JSONObject()
        .put("temp", temp).put("code", code).put("isDay", isDay)
        .put("min", min ?: JSONObject.NULL).put("max", max ?: JSONObject.NULL)
        .put("fetchedAt", fetchedAt).put("units", units.name)
        .toString()

    companion object {
        fun fromJson(text: String?): WeatherReading? {
            if (text.isNullOrBlank()) return null
            return try {
                val o = JSONObject(text)
                WeatherReading(
                    temp = o.getDouble("temp"),
                    code = o.getInt("code"),
                    isDay = o.optBoolean("isDay", true),
                    min = if (o.isNull("min")) null else o.getDouble("min"),
                    max = if (o.isNull("max")) null else o.getDouble("max"),
                    fetchedAt = o.getLong("fetchedAt"),
                    units = TempUnit.fromName(o.optString("units")),
                )
            } catch (e: JSONException) {
                null
            }
        }
    }
}

/** A geocoding result the user can pick. */
data class GeoPlace(
    val name: String,
    val admin1: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Lisbon, Lisbon, Portugal": name, region and country, skipping what is missing or repeated. */
    val label: String
        get() = listOfNotNull(name, admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

object WeatherParser {

    /** Parses the forecast response; null when the payload has no usable current temperature. */
    fun parseForecast(json: String, fetchedAt: Long, units: TempUnit): WeatherReading? = try {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        WeatherReading(
            temp = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) != 0,
            min = daily?.optJSONArray("temperature_2m_min")?.firstDouble(),
            max = daily?.optJSONArray("temperature_2m_max")?.firstDouble(),
            fetchedAt = fetchedAt,
            units = units,
        )
    } catch (e: JSONException) {
        null
    }

    /** Parses the geocoding response; no "results" key (Open-Meteo omits it for no match) is an empty list. */
    fun parseGeocoding(json: String): List<GeoPlace> = try {
        val results = JSONObject(json).optJSONArray("results")
        if (results == null) emptyList() else (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            if (!o.has("latitude") || !o.has("longitude") || o.optString("name").isBlank()) return@mapNotNull null
            GeoPlace(
                name = o.getString("name"),
                admin1 = o.optString("admin1").ifBlank { null },
                country = o.optString("country").ifBlank { null },
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
            )
        }
    } catch (e: JSONException) {
        emptyList()
    }

    private fun JSONArray.firstDouble(): Double? = if (length() == 0 || isNull(0)) null else optDouble(0).takeUnless { it.isNaN() }
}

object WeatherFormat {

    const val REFRESH_AFTER_MS = 30 * 60 * 1000L
    const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

    /** True when there is no reading, it is for other units, or it is due a refresh. */
    fun needsRefresh(reading: WeatherReading?, units: TempUnit, now: Long): Boolean =
        reading == null || reading.units != units || now - reading.fetchedAt > REFRESH_AFTER_MS || reading.fetchedAt > now

    fun isStale(reading: WeatherReading, now: Long): Boolean = now - reading.fetchedAt > STALE_AFTER_MS

    /**
     * "17° · light rain · 12°/19°". Empty when there is no reading, it is older than 3 hours or it
     * was fetched in other units, so the home screen never shows wrong data.
     */
    fun line(reading: WeatherReading?, units: TempUnit, showHighLow: Boolean, now: Long): String {
        if (reading == null || reading.units != units || isStale(reading, now)) return ""
        val parts = mutableListOf("${degrees(reading.temp)}°", WeatherCodes.describe(reading.code))
        if (showHighLow && reading.min != null && reading.max != null) {
            parts += "${degrees(reading.min)}°/${degrees(reading.max)}°"
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun degrees(value: Double): String {
        val r = value.roundToInt()
        return (if (r == 0) 0 else r).toString() // no "-0"
    }
}

object WeatherCodes {

    /** WMO weather interpretation codes as Open-Meteo reports them, as short lowercase words. */
    fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80, 81 -> "rain showers"
        82 -> "heavy showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm, hail"
        else -> ""
    }
}
