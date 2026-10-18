package com.outsmartis.yoke.glance

import com.outsmartis.yoke.cockpit.CardHead
import com.outsmartis.yoke.weather.TempUnit
import com.outsmartis.yoke.weather.WeatherReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GlanceLogicTest {

    private val zone = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 10, 18)

    private fun at(day: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun timed(title: String, day: LocalDate, h: Int, minutes: Int = 60) =
        GlanceEvent(title, at(day, h), at(day, h) + minutes * 60_000L, false, "uri:$title")

    private fun allDay(title: String, day: LocalDate, days: Int = 1) = GlanceEvent(
        title, day.atStartOfDay(zone).toInstant().toEpochMilli(),
        day.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(), true, "uri:$title",
    )

    private val now = at(today, 9, 0)

    @Test
    fun noEventsGivesNull() {
        assertNull(GlanceLogic.pickNext(emptyList(), now, zone))
    }

    @Test
    fun earliestUpcomingTimedEventToday() {
        val events = listOf(timed("Late", today, 15), timed("Soon", today, 10, 30))
        assertEquals("Soon", GlanceLogic.pickNext(events, now, zone)?.title)
    }

    @Test
    fun ongoingEventWinsAndIsLabelledNow() {
        val ongoing = timed("Standup", today, 8, 90)
        val picked = GlanceLogic.pickNext(listOf(timed("Later", today, 11), ongoing), now, zone)!!
        assertEquals("Standup", picked.title)
        assertEquals(DayLabel.NOW, GlanceLogic.dayLabel(picked, now, zone))
    }

    @Test
    fun endedEventsAreSkipped() {
        val events = listOf(timed("Done", today, 7), timed("Next", today, 12))
        assertEquals("Next", GlanceLogic.pickNext(events, now, zone)?.title)
    }

    @Test
    fun upcomingTimedEventIsLabelledToday() {
        val e = timed("Review", today, 10, 30)
        assertEquals(DayLabel.TODAY, GlanceLogic.dayLabel(e, now, zone))
    }

    @Test
    fun timedEventBeatsAllDayToday() {
        val events = listOf(allDay("Holiday", today), timed("Call", today, 14))
        assertEquals("Call", GlanceLogic.pickNext(events, now, zone)?.title)
    }

    @Test
    fun allDayShownWhenNoTimedEventIsLeftToday() {
        val events = listOf(timed("Over", today, 7), allDay("Holiday", today), timed("Tomorrow call", today.plusDays(1), 9))
        val picked = GlanceLogic.pickNext(events, now, zone)!!
        assertEquals("Holiday", picked.title)
        assertEquals(DayLabel.TODAY, GlanceLogic.dayLabel(picked, now, zone))
    }

    @Test
    fun multiDayAllDayCoveringTodayCounts() {
        val trip = allDay("Trip", today.minusDays(1), days = 3)
        assertEquals("Trip", GlanceLogic.pickNext(listOf(trip), now, zone)?.title)
    }

    @Test
    fun allDayEndedYesterdayIsSkipped() {
        assertNull(GlanceLogic.pickNext(listOf(allDay("Yesterday", today.minusDays(1))), now, zone))
    }

    @Test
    fun nothingLeftTodayFallsToTomorrowTimedFirst() {
        val tomorrow = today.plusDays(1)
        val events = listOf(allDay("Birthday", tomorrow), timed("Dentist", tomorrow, 11), timed("Over", today, 7))
        val picked = GlanceLogic.pickNext(events, now, zone)!!
        assertEquals("Dentist", picked.title)
        assertEquals(DayLabel.TOMORROW, GlanceLogic.dayLabel(picked, now, zone))
    }

    @Test
    fun onlyAllDayTomorrow() {
        val picked = GlanceLogic.pickNext(listOf(allDay("Birthday", today.plusDays(1))), now, zone)!!
        assertEquals("Birthday", picked.title)
        assertEquals(DayLabel.TOMORROW, GlanceLogic.dayLabel(picked, now, zone))
    }

    @Test
    fun dayAfterTomorrowIsIgnored() {
        assertNull(GlanceLogic.pickNext(listOf(timed("Far", today.plusDays(2), 10)), now, zone))
    }

    @Test
    fun lateEveningMovesOnToTomorrow() {
        val late = at(today, 23, 30)
        val events = listOf(timed("Dinner", today, 20), timed("Breakfast", today.plusDays(1), 8))
        assertEquals("Breakfast", GlanceLogic.pickNext(events, late, zone)?.title)
    }

    @Test
    fun blankTitlesAreIgnored() {
        assertNull(GlanceLogic.pickNext(listOf(timed("  ", today, 10)), now, zone))
    }

    @Test
    fun nextChangeIsStartWhenUpcomingEndWhenOngoing() {
        val upcoming = timed("A", today, 10)
        assertEquals(upcoming.start, GlanceLogic.nextChangeAt(upcoming, now))
        val ongoing = timed("B", today, 8, 120)
        assertEquals(ongoing.end, GlanceLogic.nextChangeAt(ongoing, now))
        val day = allDay("C", today)
        assertEquals(day.end, GlanceLogic.nextChangeAt(day, now))
    }

    @Test
    fun allDayFromProviderUtcMidnightBecomesLocalMidnight() {
        val utcMidnight = LocalDate.of(2026, 10, 18).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val local = LocalDate.of(2026, 10, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(local, GlanceLogic.allDayToLocal(utcMidnight, zone))
        // a zone behind UTC must still land on the same calendar day
        val la = ZoneId.of("America/Los_Angeles")
        assertEquals(LocalDate.of(2026, 10, 18).atStartOfDay(la).toInstant().toEpochMilli(), GlanceLogic.allDayToLocal(utcMidnight, la))
    }

    @Test
    fun cockpitCardsBecomeEvents() {
        val cards = listOf(
            CardHead("Call", "scheduled", today, "10:00") to "obsidian://a",
            CardHead("Write", "scheduled", today.plusDays(1), "") to "obsidian://b",
            CardHead("Old", "scheduled", today.minusDays(1), "09:00") to "obsidian://c",
            CardHead("Done", "done", today, "11:00") to "obsidian://d",
            CardHead("Later", "scheduled", today.plusDays(2), "") to "obsidian://e",
            CardHead("Bad time", "scheduled", today, "25:99") to "obsidian://f",
        )
        val events = GlanceLogic.cockpitEvents(cards, today, zone)
        assertEquals(listOf("Call", "Write", "Bad time"), events.map { it.title })
        assertEquals(at(today, 10), events[0].start)
        assertEquals(30 * 60_000L, events[0].end - events[0].start)
        assertEquals(true, events[1].allDay)
        assertEquals(true, events[2].allDay)
        assertEquals("obsidian://a", events[0].uri)
    }

    private fun reading(fetchedAt: Long, min: Double? = 13.0, max: Double? = 21.0) =
        WeatherReading(17.6, 2, true, min, max, fetchedAt, TempUnit.CELSIUS)

    @Test
    fun weatherText() {
        val w = GlanceLogic.weather(reading(now), TempUnit.CELSIUS, true, now)!!
        assertEquals("18°", w.temp)
        assertEquals("partly cloudy", w.description)
        assertEquals(21, w.high)
        assertEquals(13, w.low)
    }

    @Test
    fun weatherWithoutHighLow() {
        val w = GlanceLogic.weather(reading(now), TempUnit.CELSIUS, false, now)!!
        assertNull(w.high)
        assertNull(w.low)
    }

    @Test
    fun staleOrMissingOrWrongUnitWeatherIsHidden() {
        assertNull(GlanceLogic.weather(null, TempUnit.CELSIUS, true, now))
        assertNull(GlanceLogic.weather(reading(now - 4 * 60 * 60 * 1000L), TempUnit.CELSIUS, true, now))
        assertNull(GlanceLogic.weather(reading(now), TempUnit.FAHRENHEIT, true, now))
    }

    @Test
    fun sourceCyclesNoneCalendarCockpit() {
        assertEquals(GlanceSource.CALENDAR, GlanceSource.NONE.next())
        assertEquals(GlanceSource.COCKPIT, GlanceSource.CALENDAR.next())
        assertEquals(GlanceSource.NONE, GlanceSource.COCKPIT.next())
        assertEquals(GlanceSource.NONE, GlanceSource.fromName("garbage"))
    }
}
