package com.outsmartis.yoke.omarchy

import kotlin.math.roundToLong

/** How a theme change is shown: the activity just recreates, or the old look fades out over the new one. */
enum class FadeMode(val id: String) {
    OFF("off"), FADE("fade"), QUICK("quick");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** Cross-fade timing. Pure, so it is unit tested. */
object ThemeFade {

    const val FADE_MS = 450L
    const val QUICK_MS = 200L

    /** A snapshot older than this belongs to a recreate that never happened; it is dropped. */
    const val SNAPSHOT_MAX_AGE_MS = 3000L

    /**
     * The fade length, scaled like every other animation by Android's animator duration scale;
     * 0 (no fade) when off or when animations are removed (scale 0).
     */
    fun durationMs(mode: FadeMode, animatorScale: Float): Long {
        if (animatorScale <= 0f) return 0
        val base = when (mode) {
            FadeMode.OFF -> return 0
            FadeMode.FADE -> FADE_MS
            FadeMode.QUICK -> QUICK_MS
        }
        return (base * animatorScale).roundToLong()
    }
}
