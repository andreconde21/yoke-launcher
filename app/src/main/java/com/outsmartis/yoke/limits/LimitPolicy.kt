package com.outsmartis.yoke.limits

import java.time.Instant
import java.time.ZoneId

/** The pause curve and the day it counts in. Pure, so it is unit tested. */
object LimitPolicy {

    /** Medium curve: seconds of pause before the 1st, 2nd, ... open of the day; [CAP] after. */
    val CURVE = listOf(3, 5, 8, 13, 21, 34)
    const val CAP = 60

    /** The day rolls over at 04:00, so a late night still counts as the evening before. */
    const val RESET_HOUR = 4L

    fun delaySeconds(opensSoFar: Int): Int = CURVE.getOrElse(opensSoFar.coerceAtLeast(0)) { CAP }

    fun dayKey(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        // Local clock arithmetic, so the 04:00 reset holds on daylight-saving change days too.
        Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime().minusHours(RESET_HOUR).toLocalDate().toString()

    /** "1st", "2nd", "3rd", "4th", "11th", "21st"... */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /**
     * Accessibility (level B): whether the app now in front should get the pause screen.
     * [passPackage] is the app the user just let through; it may stay in front.
     */
    fun shouldPause(foreground: String?, limited: Set<String>, passPackage: String?, ignored: Set<String>): Boolean =
        foreground != null && foreground in limited && foreground != passPackage && foreground !in ignored

    /**
     * Whether a change of foreground ends the current pass: leaving for another real app does,
     * keyboards, system pop-ups and Yoke's own pause screen don't.
     */
    fun endsPass(foreground: String?, passPackage: String?, ignored: Set<String>): Boolean =
        passPackage != null && foreground != null && foreground != passPackage && foreground !in ignored
}
