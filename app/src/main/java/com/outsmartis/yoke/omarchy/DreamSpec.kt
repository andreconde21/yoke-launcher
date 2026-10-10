package com.outsmartis.yoke.omarchy

import java.util.Random

/** What the screensaver shows. */
enum class DreamContent(val id: String) {
    LOGO("logo"), CLOCK("clock"), CLOCK_AGENDA("clock_agenda");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: CLOCK
    }
}

/** The screensaver's wordmark, dim timing and burn-in shift. Pure, so it is unit tested. */
object DreamSpec {

    /** "YOKE" in block letters, drawn in the mono font (Yoke's own name, not Omarchy's artwork). */
    val WORDMARK = listOf(
        "█ █ █▀█ █▄▀ █▀▀",
        " █  █▄█ █ █ ██▄",
    )

    val DIM_AFTER_MIN = listOf(1, 5, 15)
    const val DEFAULT_DIM_AFTER_MIN = 5

    const val SHIFT_EVERY_MS = 60_000L

    fun valid(value: Int): Int = if (value in DIM_AFTER_MIN) value else DEFAULT_DIM_AFTER_MIN

    fun next(current: Int): Int = DIM_AFTER_MIN[(DIM_AFTER_MIN.indexOf(current) + 1) % DIM_AFTER_MIN.size]

    /** The first [shown] characters of the wordmark, line by line, for the typing animation. */
    fun typed(shown: Int): List<String> {
        var left = shown.coerceAtLeast(0)
        return WORDMARK.map { line ->
            val take = minOf(left, line.length)
            left -= take
            line.take(take)
        }
    }

    val WORDMARK_LENGTH: Int get() = WORDMARK.sumOf { it.length }

    /**
     * Where the content sits for shift number [step]: a spot inside the free space ([freeW] x [freeH]
     * pixels around the content), the same for the same step and different from step to step.
     */
    fun offset(step: Long, freeW: Int, freeH: Int): Pair<Int, Int> {
        val r = Random(step * 7919L + 17L)
        val x = if (freeW > 0) r.nextInt(freeW + 1) else 0
        val y = if (freeH > 0) r.nextInt(freeH + 1) else 0
        return x to y
    }
}
