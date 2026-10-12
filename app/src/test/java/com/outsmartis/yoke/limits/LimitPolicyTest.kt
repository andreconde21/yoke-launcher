package com.outsmartis.yoke.limits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LimitPolicyTest {

    private val zone = ZoneId.of("Europe/Lisbon")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun mediumCurveThenCap() {
        val delays = (0 until 9).map { LimitPolicy.delaySeconds(it) }
        assertEquals(listOf(3, 5, 8, 13, 21, 34, 60, 60, 60), delays)
    }

    @Test
    fun negativeCountStartsAtTheFirstStep() {
        assertEquals(3, LimitPolicy.delaySeconds(-1))
    }

    @Test
    fun dayRollsOverAtFour() {
        assertEquals("2026-10-11", LimitPolicy.dayKey(at(2026, 10, 12, 3, 59), zone))
        assertEquals("2026-10-12", LimitPolicy.dayKey(at(2026, 10, 12, 4, 0), zone))
        assertEquals("2026-10-12", LimitPolicy.dayKey(at(2026, 10, 12, 23, 30), zone))
    }

    @Test
    fun dayKeyAcrossDstChange() {
        // Portugal leaves summer time on 2026-10-25 at 02:00.
        assertEquals("2026-10-24", LimitPolicy.dayKey(at(2026, 10, 25, 3, 30), zone))
        assertEquals("2026-10-25", LimitPolicy.dayKey(at(2026, 10, 25, 4, 30), zone))
    }

    @Test
    fun ordinals() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111).map(LimitPolicy::ordinal),
        )
    }

    private val ignored = setOf("com.android.systemui", "com.outsmartis.yoke", "keyboard")

    @Test
    fun pausesALimitedAppInFront() {
        assertTrue(LimitPolicy.shouldPause("insta", setOf("insta"), null, ignored))
    }

    @Test
    fun letsTheVisitedAppStay() {
        assertFalse(LimitPolicy.shouldPause("insta", setOf("insta"), "insta", ignored))
    }

    @Test
    fun ignoresUnlimitedAndSystem() {
        assertFalse(LimitPolicy.shouldPause("maps", setOf("insta"), null, ignored))
        assertFalse(LimitPolicy.shouldPause(null, setOf("insta"), null, ignored))
        assertFalse(LimitPolicy.shouldPause("com.android.systemui", setOf("com.android.systemui"), null, ignored))
    }

    @Test
    fun leavingForAnotherAppEndsTheVisit() {
        assertTrue(LimitPolicy.endsPass("maps", "insta", ignored))
    }

    @Test
    fun keyboardsPopUpsAndYokeDontEndTheVisit() {
        assertFalse(LimitPolicy.endsPass("keyboard", "insta", ignored))
        assertFalse(LimitPolicy.endsPass("com.android.systemui", "insta", ignored))
        assertFalse(LimitPolicy.endsPass("com.outsmartis.yoke", "insta", ignored))
        assertFalse(LimitPolicy.endsPass("insta", "insta", ignored))
        assertFalse(LimitPolicy.endsPass("maps", null, ignored))
    }
}
