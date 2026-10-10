package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLineTest {
    private val c = TopModule.CLOCK
    private val d = TopModule.DATE
    private val b = TopModule.BATTERY
    private val w = TopModule.WEATHER

    @Test fun neverSetIsTheDefault() = assertEquals(TopLine.DEFAULT, TopLine.decode(null))
    @Test fun emptyIsNoModules() = assertEquals(emptyList<TopModule>(), TopLine.decode(""))
    @Test fun roundTripKeepsOrder() = assertEquals(listOf(w, c, b), TopLine.decode(TopLine.encode(listOf(w, c, b))))
    @Test fun unknownAndRepeatedIdsAreDropped() = assertEquals(listOf(c, d), TopLine.decode("clock,nope,date,clock"))

    @Test fun toggleAddsAtTheEndAndRemoves() {
        assertEquals(listOf(c, d, w), TopLine.toggle(listOf(c, d), w))
        assertEquals(listOf(d), TopLine.toggle(listOf(c, d), c))
    }

    @Test fun moveUpAndDown() {
        assertEquals(listOf(d, c, b), TopLine.move(listOf(c, d, b), 1, -1))
        assertEquals(listOf(c, b, d), TopLine.move(listOf(c, d, b), 1, 1))
    }

    @Test fun moveOutOfRangeChangesNothing() {
        assertEquals(listOf(c, d), TopLine.move(listOf(c, d), 0, -1))
        assertEquals(listOf(c, d), TopLine.move(listOf(c, d), 1, 1))
    }

    @Test fun battery() {
        assertEquals("78%", TopLine.battery(78, false))
        assertEquals("78%+", TopLine.battery(78, true))
        assertEquals("", TopLine.battery(0, false))
    }

    @Test fun weatherKeepsTemperatureAndSky() {
        assertEquals("17° light rain", TopLine.weather("17° · light rain · 12°/19°"))
        assertEquals("", TopLine.weather(""))
    }

    @Test fun agendaCount() {
        assertEquals(0, TopLine.agendaCount(""))
        assertEquals(1, TopLine.agendaCount("Today · 10:00 Call accountant"))
        assertEquals(3, TopLine.agendaCount("Overdue · Call accountant · +2"))
        assertEquals("3 due", TopLine.agenda(3))
        assertEquals("", TopLine.agenda(0))
    }

    @Test fun conductoreDotOnlyWhileWaiting() {
        assertEquals("", TopLine.conductore(0))
        assertEquals("● 2", TopLine.conductore(2))
    }

    @Test fun segmentsFollowTheOrderAndSkipEmptyOnes() = assertEquals(
        listOf(b to "50%", c to "10:00"),
        TopLine.segments(listOf(b, w, c), mapOf(c to "10:00", w to "", b to "50%")),
    )

    @Test fun clockOptionDefaultsToReplace() = assertEquals(TopLineClock.REPLACE, TopLineClock.fromId(null))
}
