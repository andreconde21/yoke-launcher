package com.outsmartis.yoke.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReturnTest {

    @Test
    fun popsToHomeUnlessPrivateSpaceIsToggling() {
        assertTrue(HomeReturn.shouldPop(false))
        assertFalse(HomeReturn.shouldPop(true))
    }

    @Test
    fun scrollIsRestoredOnlyOnceTheContentIsTallEnough() {
        assertNull(HomeReturn.scrollTarget(900, 1500, 1000)) // only 500 scrollable: rows still filling in
        assertEquals(900, HomeReturn.scrollTarget(900, 2000, 1000))
        assertEquals(1000, HomeReturn.scrollTarget(1000, 2000, 1000))
        assertNull(HomeReturn.scrollTarget(0, 2000, 1000))
    }
}
