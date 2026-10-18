package com.outsmartis.yoke.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFadeTest {

    @Test
    fun animationsRemovedMeansNoFade() {
        assertEquals(0L, ThemeFade.durationMs(0f))
        assertEquals(0L, ThemeFade.durationMs(-1f))
        assertEquals(0L, ThemeFade.durationMs(Float.NaN))
    }

    @Test
    fun anyPositiveScaleIsBaseDuration() {
        // The animator applies the system scale itself; scaling here too would square it.
        assertEquals(250L, ThemeFade.durationMs(1f))
        assertEquals(250L, ThemeFade.durationMs(0.5f))
        assertEquals(250L, ThemeFade.durationMs(10f))
    }

    @Test
    fun staleSnapshotIsDropped() {
        assertFalse(ThemeFade.isStale(1000L, 1000L))
        assertFalse(ThemeFade.isStale(4000L, 1000L))
        assertTrue(ThemeFade.isStale(4001L, 1000L))
        assertTrue(ThemeFade.isStale(500L, 1000L)) // clock went backwards
    }

    @Test
    fun fadesUnlessAnimationsOff() {
        assertTrue(ThemeFade.canFade(250L))
        assertFalse(ThemeFade.canFade(0L))
    }
}
