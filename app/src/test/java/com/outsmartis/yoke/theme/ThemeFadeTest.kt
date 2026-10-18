package com.outsmartis.yoke.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeFadeTest {

    @Test
    fun animationsRemovedMeansNoFade() {
        assertEquals(0L, ThemeFade.durationMs(0f))
        assertEquals(0L, ThemeFade.durationMs(-1f))
        assertEquals(0L, ThemeFade.durationMs(Float.NaN))
    }

    @Test
    fun normalScaleIsQuarterSecond() = assertEquals(250L, ThemeFade.durationMs(1f))

    @Test
    fun scaleStretchesButIsCapped() {
        assertEquals(125L, ThemeFade.durationMs(0.5f))
        assertEquals(1000L, ThemeFade.durationMs(10f))
    }
}
