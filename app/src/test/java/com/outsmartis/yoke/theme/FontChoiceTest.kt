package com.outsmartis.yoke.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontChoiceTest {
    @Test fun everyIdRoundTrips() = FontChoice.entries.forEach { assertEquals(it, FontChoice.fromId(it.id)) }

    @Test fun idsAreUnique() = assertEquals(FontChoice.entries.size, FontChoice.entries.map { it.id }.toSet().size)

    @Test fun unknownOrMissingIdIsSystem() {
        assertEquals(FontChoice.SYSTEM, FontChoice.fromId("comic_sans"))
        assertEquals(FontChoice.SYSTEM, FontChoice.fromId(null))
    }

    @Test fun oldJetBrainsSwitchStillWorks() {
        assertEquals(FontChoice.JETBRAINS_MONO, FontChoice.resolve(null, true))
        assertEquals(FontChoice.SYSTEM, FontChoice.resolve(null, false))
    }

    @Test fun storedChoiceWinsOverTheOldSwitch() {
        assertEquals(FontChoice.SYSTEM, FontChoice.resolve("system", true))
        assertEquals(FontChoice.GEIST, FontChoice.resolve("geist", false))
    }

    @Test fun jetBrainsIdIsTheOneTheOldCodeUsed() = assertEquals("jetbrains_mono", FontChoice.JETBRAINS_MONO.id)

    @Test fun onlySystemHasNoFiles() = FontChoice.entries.forEach {
        assertEquals(it == FontChoice.SYSTEM, it.regular == 0 && it.bold == 0)
        assertTrue(it.regular != it.bold || it == FontChoice.SYSTEM)
    }
}
