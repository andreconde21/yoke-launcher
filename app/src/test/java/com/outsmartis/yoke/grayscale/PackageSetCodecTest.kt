package com.outsmartis.yoke.grayscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageSetCodecTest {

    @Test
    fun roundTrip() {
        val set = setOf("com.b", "com.a", "org.example.x")
        assertEquals(set, PackageSetCodec.decode(PackageSetCodec.encode(set)))
    }

    @Test
    fun emptySetIsDistinctFromMissing() {
        assertEquals(emptySet<String>(), PackageSetCodec.decode(PackageSetCodec.encode(emptySet())))
        assertNull(PackageSetCodec.decode(null))
        assertNull(PackageSetCodec.decode("  "))
    }

    @Test
    fun garbageFallsBackToNull() {
        assertNull(PackageSetCodec.decode("not json"))
    }

    @Test
    fun defaultsCoverBothYokeIds() {
        assertEquals(setOf("com.outsmartis.yoke", "com.outsmartis.yoke.debug"), GrayscalePrefs.DEFAULT_EXCEPTIONS)
    }
}
