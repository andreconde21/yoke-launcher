package com.outsmartis.yoke.grayscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscalePolicyTest {

    private val exceptions = setOf("com.maps", "com.outsmartis.yoke")
    private val ignored = setOf("com.android.systemui", "android", "com.keyboard")

    private fun decide(
        fg: String?, last: String? = null, on: Boolean = true,
        pausedUntil: Long = 0, now: Long = 1000, ex: Set<String> = exceptions,
    ) = GrayscalePolicy.decide(on, pausedUntil, now, fg, ex, ignored, last)

    @Test
    fun ordinaryAppIsGrayscaleAndExceptionIsColour() {
        assertTrue(decide("com.chat").grayscale)
        assertFalse(decide("com.maps").grayscale)
        assertEquals("com.maps", decide("com.maps").rememberedPackage)
    }

    @Test
    fun transientPackagesKeepThePreviousDecision() {
        assertFalse(decide("com.android.systemui", last = "com.maps").grayscale)
        assertTrue(decide("com.keyboard", last = "com.chat").grayscale)
        assertEquals("com.maps", decide("android", last = "com.maps").rememberedPackage)
        assertFalse(decide(null, last = "com.maps").grayscale)
    }

    @Test
    fun unknownForegroundStaysGrayscale() {
        val d = decide(null, last = null)
        assertTrue(d.grayscale)
        assertNull(d.rememberedPackage)
    }

    @Test
    fun featureOffAlwaysColour() {
        assertFalse(decide("com.chat", on = false).grayscale)
    }

    @Test
    fun pauseForcesColourUntilItEnds() {
        assertFalse(decide("com.chat", pausedUntil = 2000, now = 1000).grayscale)
        assertTrue(decide("com.chat", pausedUntil = 2000, now = 2000).grayscale)
        assertTrue(GrayscalePolicy.isPaused(2000, 1999))
    }

    @Test
    fun emptyExceptionsMeansEverythingGrayscale() {
        assertTrue(decide("com.maps", ex = emptySet()).grayscale)
    }

    // What gets written

    @Test
    fun grayscaleWritesOnlyWhenDifferent() {
        assertNull(GrayscalePolicy.resolve(true, DaltonizerState.GRAYSCALE, DaltonizerState.COLOUR))
        assertEquals(DaltonizerState.GRAYSCALE, GrayscalePolicy.resolve(true, DaltonizerState(false, -1), DaltonizerState.COLOUR))
        assertEquals(DaltonizerState.GRAYSCALE, GrayscalePolicy.resolve(true, DaltonizerState(true, 12), null))
    }

    @Test
    fun colourTurnsCorrectionOffOrRestoresTheUsersMode() {
        assertEquals(DaltonizerState.COLOUR, GrayscalePolicy.resolve(false, DaltonizerState.GRAYSCALE, DaltonizerState.COLOUR))
        assertNull(GrayscalePolicy.resolve(false, DaltonizerState(false, 0), DaltonizerState.COLOUR))
        val users = DaltonizerState(true, 12)
        assertEquals(users, GrayscalePolicy.resolve(false, DaltonizerState.GRAYSCALE, users))
    }

    @Test
    fun colourBeforeTakeoverNeverClobbersTheUsersOwnMode() {
        assertNull(GrayscalePolicy.resolve(false, DaltonizerState(true, 12), null))
        assertNull(GrayscalePolicy.resolve(false, DaltonizerState(false, -1), null))
    }

    @Test
    fun previousStateKeepsOnlyNonGrayscaleCorrection() {
        assertEquals(DaltonizerState(true, 12), GrayscalePolicy.previousToRemember(DaltonizerState(true, 12)))
        assertEquals(DaltonizerState.COLOUR, GrayscalePolicy.previousToRemember(DaltonizerState(true, 0)))
        assertEquals(DaltonizerState.COLOUR, GrayscalePolicy.previousToRemember(DaltonizerState(false, 12)))
    }
}
