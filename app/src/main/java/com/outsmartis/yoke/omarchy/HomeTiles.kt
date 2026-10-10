package com.outsmartis.yoke.omarchy

/** When home app tiles get a border, like Hyprland's window borders. */
enum class BorderMode(val id: String) {
    OFF("off"), PRESS("press"), ALWAYS("always");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** Which theme colour a tile's border takes. */
enum class BorderColor { ACCENT, SECONDARY }

/** The tile options' allowed values and the border colour rule. Pure, so it is unit tested. */
object HomeTiles {

    val WIDTHS_DP = listOf(1, 2, 3)
    val RADII_DP = listOf(0, 4, 8, 12)
    val GAPS_DP = listOf(0, 4, 8, 16)

    const val DEFAULT_WIDTH_DP = 2
    const val DEFAULT_RADIUS_DP = 0
    const val DEFAULT_GAP_DP = 4

    /** [value] if allowed, else the [fallback]. */
    fun valid(value: Int, allowed: List<Int>, fallback: Int): Int = if (value in allowed) value else fallback

    /** The allowed value after [current], wrapping. */
    fun next(current: Int, allowed: List<Int>): Int = allowed[(allowed.indexOf(current) + 1) % allowed.size]

    /** The pressed / focused tile is accent; the others are secondary when always bordered, else bare. */
    fun border(mode: BorderMode, highlighted: Boolean): BorderColor? = when {
        mode == BorderMode.OFF -> null
        highlighted -> BorderColor.ACCENT
        mode == BorderMode.ALWAYS -> BorderColor.SECONDARY
        else -> null
    }
}
