package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFadeTest {
    @Test fun offNeverFades() = assertEquals(0L, ThemeFade.durationMs(FadeMode.OFF, 1f))
    @Test fun fadeAndQuickFade() {
        assertEquals(ThemeFade.FADE_MS, ThemeFade.durationMs(FadeMode.FADE, 1f))
        assertEquals(ThemeFade.QUICK_MS, ThemeFade.durationMs(FadeMode.QUICK, 1f))
        assertTrue(ThemeFade.QUICK_MS < ThemeFade.FADE_MS)
    }
    @Test fun removedAnimationsMeanNoFade() {
        assertEquals(0L, ThemeFade.durationMs(FadeMode.FADE, 0f))
        assertEquals(0L, ThemeFade.durationMs(FadeMode.QUICK, 0f))
    }
    @Test fun followsTheAnimatorScale() = assertEquals(ThemeFade.FADE_MS / 2, ThemeFade.durationMs(FadeMode.FADE, 0.5f))
    @Test fun defaultsToOff() = assertEquals(FadeMode.OFF, FadeMode.fromId("bogus"))
}
