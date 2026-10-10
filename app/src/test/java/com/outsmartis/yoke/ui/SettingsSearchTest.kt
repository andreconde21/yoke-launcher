package com.outsmartis.yoke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun matchesTheRowLabelIgnoringCase() = assertTrue(SettingsSearch.matches("WEATH", "Home screen", listOf("Weather", "On")))
    @Test fun matchesTheSectionTitle() = assertTrue(SettingsSearch.matches("look", "Look", listOf("Font", "System")))
    @Test fun everyWordMustAppear() = assertFalse(SettingsSearch.matches("weather font", "Home screen", listOf("Weather", "On")))
    @Test fun wordsCanComeFromLabelAndValue() = assertTrue(SettingsSearch.matches("font system", "Look", listOf("Font", "System")))
    @Test fun noMatch() = assertFalse(SettingsSearch.matches("vault", "Look", listOf("Font", "System")))
}
