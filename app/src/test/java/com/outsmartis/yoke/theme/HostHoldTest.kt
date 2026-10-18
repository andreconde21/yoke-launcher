package com.outsmartis.yoke.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostHoldTest {

    @Test
    fun poppingToHomeIsTheDefaultWhenStopped() {
        assertTrue(HostHold.shouldPopToHome(false, false, false))
    }

    @Test
    fun recreatePrivateSpaceAndThePickerKeepTheCurrentScreen() {
        assertFalse(HostHold.shouldPopToHome(true, false, false))
        assertFalse(HostHold.shouldPopToHome(false, true, false))
        assertFalse(HostHold.shouldPopToHome(false, false, true))
    }
}
