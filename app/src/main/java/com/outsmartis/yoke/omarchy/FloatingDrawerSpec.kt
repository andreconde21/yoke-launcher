package com.outsmartis.yoke.omarchy

/** The app drawer as today's full-screen list or as a Walker-like floating box. */
enum class DrawerStyle(val id: String) {
    LIST("list"), FLOATING("floating");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: LIST
    }
}

enum class FloatPosition(val id: String) {
    CENTRE("centre"), TOP("top");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: CENTRE
    }
}

/** The floating drawer's allowed sizes and its measurements. Pure, so it is unit tested. */
object FloatingDrawerSpec {

    val WIDTHS_PCT = listOf(70, 85, 95)
    val MAX_RESULTS = listOf(5, 8, 12)

    const val DEFAULT_WIDTH_PCT = 85
    const val DEFAULT_MAX_RESULTS = 8

    fun valid(value: Int, allowed: List<Int>, fallback: Int): Int = if (value in allowed) value else fallback

    fun next(current: Int, allowed: List<Int>): Int = allowed[(allowed.indexOf(current) + 1) % allowed.size]

    /** The box's width: [percent] of the screen, never wider than [maxPx] (tablets, landscape). */
    fun widthPx(screenPx: Int, percent: Int, maxPx: Int): Int = minOf(screenPx * percent / 100, maxPx)

    /** The results list grows with the results up to [maxResults] rows, then scrolls. */
    fun listMaxHeightPx(rowPx: Int, maxResults: Int): Int = rowPx.coerceAtLeast(1) * maxResults
}
