package com.outsmartis.yoke.glance

import com.outsmartis.yoke.cockpit.CardHead
import com.outsmartis.yoke.weather.WeatherCodes
import com.outsmartis.yoke.weather.WeatherFormat
import com.outsmartis.yoke.weather.WeatherReading
import com.outsmartis.yoke.weather.TempUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Where the glance card gets its event from. */
enum class GlanceSource {
    NONE, CALENDAR, COCKPIT;

    /** NONE -> CALENDAR -> COCKPIT -> NONE, the order the Settings row cycles in. */
    fun next(): GlanceSource = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): GlanceSource = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * One event the card can show. [start]/[end] are epoch millis; an all-day event spans local midnight to local
 * midnight (the calendar provider's UTC days are converted by the repository). [uri] opens it in its source.
 */
data class GlanceEvent(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val uri: String,
)

enum class DayLabel { NOW, TODAY, TOMORROW }

/** The weather half's text: "18°", "partly cloudy", and today's high and low when known. */
data class GlanceWeather(val temp: String, val description: String, val high: Int?, val low: Int?)

/** Pure card logic: which event is next, what to call its day, when to look again, and the weather text. */
object GlanceLogic {

    private const val COCKPIT_EVENT_MS = 30 * 60 * 1000L

    /**
     * The event to show at [now], looking at today and tomorrow only:
     * 1. a timed event that is on now or still to come today (the earliest start, so an ongoing one wins),
     * 2. else an all-day event covering today,
     * 3. else tomorrow's first timed event, then tomorrow's all-day ones.
     * Events that have ended, and anything after tomorrow, are ignored. Null when nothing is left.
     */
    fun pickNext(events: List<GlanceEvent>, now: Long, zone: ZoneId): GlanceEvent? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val tomorrowStart = startOf(today.plusDays(1), zone)
        val dayAfterStart = startOf(today.plusDays(2), zone)
        val live = events.filter { it.title.isNotBlank() && maxOf(it.end, it.start) > now && it.start < dayAfterStart }
        val byStart = compareBy<GlanceEvent>({ it.start }, { it.title.lowercase() })

        live.filter { !it.allDay && it.start < tomorrowStart }.minWithOrNull(byStart)?.let { return it }
        live.filter { it.allDay && it.start < tomorrowStart }.minWithOrNull(byStart)?.let { return it }
        val tomorrow = live.filter { it.start >= tomorrowStart }
        return tomorrow.filter { !it.allDay }.minWithOrNull(byStart) ?: tomorrow.minWithOrNull(byStart)
    }

    /** "Now" for a timed event that has started or an all-day one covering today, else Today or Tomorrow. */
    fun dayLabel(event: GlanceEvent, now: Long, zone: ZoneId): DayLabel {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val tomorrowStart = startOf(today.plusDays(1), zone)
        return when {
            event.start >= tomorrowStart -> DayLabel.TOMORROW
            event.allDay -> DayLabel.TODAY
            event.start <= now -> DayLabel.NOW
            else -> DayLabel.TODAY
        }
    }

    /** When the card should be looked at again: when [event] starts (the label changes) or ends (it is replaced). */
    fun nextChangeAt(event: GlanceEvent, now: Long): Long =
        if (!event.allDay && event.start > now) event.start else maxOf(event.end, now + 1)

    /**
     * Cockpit cards as events: a card due today or tomorrow, not done or archived. A timed card is a
     * half-hour event at its time; an untimed one is all-day. Overdue and later cards are not events.
     */
    fun cockpitEvents(cards: List<Pair<CardHead, String>>, today: LocalDate, zone: ZoneId): List<GlanceEvent> =
        cards.mapNotNull { (head, uri) ->
            val due = head.due ?: return@mapNotNull null
            if (head.title.isEmpty() || head.status == "done" || head.status == "archived") return@mapNotNull null
            if (due != today && due != today.plusDays(1)) return@mapNotNull null
            val minutes = timeToMinutes(head.time)
            val dayStart = startOf(due, zone)
            if (minutes == null) {
                GlanceEvent(head.title, dayStart, startOf(due.plusDays(1), zone), allDay = true, uri = uri)
            } else {
                val start = due.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
                GlanceEvent(head.title, start, start + COCKPIT_EVENT_MS, allDay = false, uri = uri)
            }
        }

    /** An all-day instance from the calendar provider (UTC midnights) as local-midnight millis. */
    fun allDayToLocal(utcMillis: Long, zone: ZoneId): Long =
        startOf(Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate(), zone)

    /** Null when there is no usable reading (none, other units, or older than WeatherFormat's limit). */
    fun weather(reading: WeatherReading?, units: TempUnit, showHighLow: Boolean, now: Long): GlanceWeather? {
        if (WeatherFormat.line(reading, units, showHighLow, now).isEmpty() || reading == null) return null
        val both = showHighLow && reading.min != null && reading.max != null
        return GlanceWeather(
            temp = "${degrees(reading.temp)}°",
            description = WeatherCodes.describe(reading.code),
            high = if (both) degrees(reading.max!!) else null,
            low = if (both) degrees(reading.min!!) else null,
        )
    }

    private fun degrees(value: Double): Int = value.roundToInt().let { if (it == 0) 0 else it }

    private fun timeToMinutes(time: String): Int? {
        val parts = time.split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    private fun startOf(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
}
