package com.outsmartis.yoke.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private val now = 1_000_000_000_000L
    private fun reading(
        temp: Double = 17.4, code: Int = 61, min: Double? = 12.2, max: Double? = 18.6,
        age: Long = 0, units: TempUnit = TempUnit.CELSIUS,
    ) = WeatherReading(temp, code, true, min, max, now - age, units)

    @Test fun wmoCodes() {
        assertEquals("clear", WeatherCodes.describe(0))
        assertEquals("mainly clear", WeatherCodes.describe(1))
        assertEquals("partly cloudy", WeatherCodes.describe(2))
        assertEquals("overcast", WeatherCodes.describe(3))
        assertEquals("fog", WeatherCodes.describe(45))
        assertEquals("fog", WeatherCodes.describe(48))
        assertEquals("drizzle", WeatherCodes.describe(53))
        assertEquals("light rain", WeatherCodes.describe(61))
        assertEquals("rain", WeatherCodes.describe(63))
        assertEquals("heavy rain", WeatherCodes.describe(65))
        assertEquals("freezing rain", WeatherCodes.describe(66))
        assertEquals("snow", WeatherCodes.describe(73))
        assertEquals("rain showers", WeatherCodes.describe(81))
        assertEquals("snow showers", WeatherCodes.describe(85))
        assertEquals("thunderstorm", WeatherCodes.describe(95))
        assertEquals("", WeatherCodes.describe(1234))
    }

    @Test fun parsesForecast() {
        val json = """{"latitude":38.72,"longitude":-9.14,"current_units":{"temperature_2m":"°C"},
            "current":{"time":"2026-10-05T10:00","interval":900,"temperature_2m":17.4,"weather_code":61,"is_day":1},
            "daily":{"time":["2026-10-05"],"temperature_2m_max":[18.6],"temperature_2m_min":[12.2]}}"""
        val r = WeatherParser.parseForecast(json, 42L, TempUnit.CELSIUS)!!
        assertEquals(17.4, r.temp, 0.0)
        assertEquals(61, r.code)
        assertTrue(r.isDay)
        assertEquals(12.2, r.min!!, 0.0)
        assertEquals(18.6, r.max!!, 0.0)
        assertEquals(42L, r.fetchedAt)
    }

    @Test fun forecastWithoutDailyOrBrokenIsHandled() {
        val noDaily = """{"current":{"temperature_2m":-0.4,"weather_code":0,"is_day":0}}"""
        val r = WeatherParser.parseForecast(noDaily, 1L, TempUnit.FAHRENHEIT)!!
        assertNull(r.min)
        assertEquals(false, r.isDay)
        assertNull(WeatherParser.parseForecast("not json", 1L, TempUnit.CELSIUS))
        assertNull(WeatherParser.parseForecast("""{"error":true}""", 1L, TempUnit.CELSIUS))
    }

    @Test fun readingJsonRoundTrips() {
        val r = reading(min = null)
        assertEquals(r, WeatherReading.fromJson(r.toJson()))
        assertEquals(reading(units = TempUnit.FAHRENHEIT), WeatherReading.fromJson(reading(units = TempUnit.FAHRENHEIT).toJson()))
        assertNull(WeatherReading.fromJson("garbage"))
        assertNull(WeatherReading.fromJson(null))
    }

    @Test fun parsesGeocoding() {
        val json = """{"results":[
            {"id":1,"name":"Lisbon","latitude":38.71667,"longitude":-9.13333,"country":"Portugal","admin1":"Lisbon"},
            {"id":2,"name":"Lisbon","latitude":44.03,"longitude":-70.1,"country":"United States","admin1":"Maine"},
            {"id":3,"name":"Nowhere"}],"generationtime_ms":1.2}"""
        val places = WeatherParser.parseGeocoding(json)
        assertEquals(2, places.size)
        assertEquals("Lisbon, Portugal", places[0].label)
        assertEquals("Lisbon, Maine, United States", places[1].label)
        assertEquals(38.71667, places[0].latitude, 0.0)
        assertEquals(emptyList<GeoPlace>(), WeatherParser.parseGeocoding("""{"generationtime_ms":0.5}"""))
        assertEquals(emptyList<GeoPlace>(), WeatherParser.parseGeocoding("nope"))
    }

    @Test fun formatsLine() {
        assertEquals("17° · light rain · 12°/19°", WeatherFormat.line(reading(), TempUnit.CELSIUS, true, now))
        assertEquals("17° · light rain", WeatherFormat.line(reading(), TempUnit.CELSIUS, false, now))
        assertEquals("17° · light rain", WeatherFormat.line(reading(min = null), TempUnit.CELSIUS, true, now))
        assertEquals("0° · clear", WeatherFormat.line(reading(temp = -0.3, code = 0), TempUnit.CELSIUS, false, now))
        assertEquals("-3° · snow", WeatherFormat.line(reading(temp = -2.6, code = 71), TempUnit.CELSIUS, false, now))
        assertEquals("17°", WeatherFormat.line(reading(code = 999), TempUnit.CELSIUS, false, now))
    }

    @Test fun fahrenheitLine() {
        val f = reading(temp = 62.6, min = 54.0, max = 65.5, units = TempUnit.FAHRENHEIT)
        assertEquals("63° · light rain · 54°/66°", WeatherFormat.line(f, TempUnit.FAHRENHEIT, true, now))
        // A reading fetched in other units is wrong data: show nothing
        assertEquals("", WeatherFormat.line(f, TempUnit.CELSIUS, true, now))
    }

    @Test fun staleOrMissingIsEmpty() {
        val threeHours = 3 * 60 * 60 * 1000L
        assertEquals("", WeatherFormat.line(null, TempUnit.CELSIUS, true, now))
        assertNotNull(WeatherFormat.line(reading(age = threeHours), TempUnit.CELSIUS, true, now).takeIf { it.isNotEmpty() })
        assertEquals("", WeatherFormat.line(reading(age = threeHours + 1), TempUnit.CELSIUS, true, now))
    }

    @Test fun refreshRules() {
        val thirty = 30 * 60 * 1000L
        assertTrue(WeatherFormat.needsRefresh(null, TempUnit.CELSIUS, now))
        assertEquals(false, WeatherFormat.needsRefresh(reading(age = thirty), TempUnit.CELSIUS, now))
        assertTrue(WeatherFormat.needsRefresh(reading(age = thirty + 1), TempUnit.CELSIUS, now))
        assertTrue(WeatherFormat.needsRefresh(reading(), TempUnit.FAHRENHEIT, now))
    }

    @Test fun urls() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=38.72&longitude=-9.13" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1",
            WeatherUrls.forecast(38.71667, -9.13333, TempUnit.CELSIUS),
        )
        assertTrue(WeatherUrls.forecast(1.0, 2.0, TempUnit.FAHRENHEIT).endsWith("&temperature_unit=fahrenheit"))
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=S%C3%A3o+Paulo&count=5&language=en&format=json",
            WeatherUrls.geocoding(" São Paulo "),
        )
    }
}
