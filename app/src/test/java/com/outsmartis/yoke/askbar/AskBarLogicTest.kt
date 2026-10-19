package com.outsmartis.yoke.askbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskBarLogicTest {

    private fun shows(
        query: String,
        barOn: Boolean = true,
        agent: AskAgent = AskAgent.PHONE_ASSISTANT,
        conductore: Boolean = true,
        baseMatches: Int = 0,
    ) = AskBarLogic.showsAskRow(barOn, agent, conductore, query, baseMatches)

    @Test
    fun showsWhenNothingMatches() {
        assertTrue(shows("what is the weather in tomar"))
    }

    @Test
    fun hiddenWhenAnAppOrLinkMatches() {
        assertFalse(shows("maps", baseMatches = 1))
    }

    @Test
    fun hiddenWithBarOff() {
        assertFalse(shows("anything", barOn = false))
    }

    @Test
    fun hiddenWithAgentNone() {
        assertFalse(shows("anything", agent = AskAgent.NONE))
    }

    @Test
    fun conductoreMissingHidesTheRow() {
        assertFalse(shows("anything", agent = AskAgent.CONDUCTORE, conductore = false))
        assertTrue(shows("anything", agent = AskAgent.CONDUCTORE, conductore = true))
    }

    @Test
    fun hiddenForBlankQuery() {
        assertFalse(shows(""))
        assertFalse(shows("   "))
    }

    @Test
    fun hiddenForPrefixModesAndBangs() {
        assertFalse(shows("=2+2"))
        assertFalse(shows(">theme"))
        assertFalse(shows("/maps"))
        assertFalse(shows("!w lisbon"))
    }

    @Test
    fun effectiveAgentFallsBackToNoneWithoutConductore() {
        assertEquals(AskAgent.NONE, AskBarLogic.effective(AskAgent.CONDUCTORE, false))
        assertEquals(AskAgent.CONDUCTORE, AskBarLogic.effective(AskAgent.CONDUCTORE, true))
        assertEquals(AskAgent.PHONE_ASSISTANT, AskBarLogic.effective(AskAgent.PHONE_ASSISTANT, false))
    }

    @Test
    fun conductoreOptionDisabledWhenNotInstalled() {
        assertFalse(AskBarLogic.isEnabled(AskAgent.CONDUCTORE, false))
        assertTrue(AskBarLogic.isEnabled(AskAgent.CONDUCTORE, true))
        assertTrue(AskBarLogic.isEnabled(AskAgent.PHONE_ASSISTANT, false))
        assertTrue(AskBarLogic.isEnabled(AskAgent.NONE, false))
    }

    @Test
    fun cycleSkipsConductoreWhenNotInstalled() {
        assertEquals(AskAgent.NONE, AskBarLogic.next(AskAgent.PHONE_ASSISTANT, false))
        assertEquals(AskAgent.PHONE_ASSISTANT, AskBarLogic.next(AskAgent.NONE, false))
        // A saved Conductore choice moves on even after it was uninstalled.
        assertEquals(AskAgent.NONE, AskBarLogic.next(AskAgent.CONDUCTORE, false))
    }

    @Test
    fun cycleIncludesConductoreWhenInstalled() {
        assertEquals(AskAgent.CONDUCTORE, AskBarLogic.next(AskAgent.PHONE_ASSISTANT, true))
        assertEquals(AskAgent.NONE, AskBarLogic.next(AskAgent.CONDUCTORE, true))
        assertEquals(AskAgent.PHONE_ASSISTANT, AskBarLogic.next(AskAgent.NONE, true))
    }

    @Test
    fun unknownKeyFallsBackToDefault() {
        assertEquals(AskAgent.DEFAULT, AskAgent.fromKey(null))
        assertEquals(AskAgent.DEFAULT, AskAgent.fromKey("gemini"))
        assertEquals(AskAgent.CONDUCTORE, AskAgent.fromKey("conductore"))
        assertEquals(AskAgent.NONE, AskAgent.fromKey("none"))
    }

    @Test
    fun assistantPackageFromSetting() {
        assertEquals("com.google.android.googlequicksearchbox",
            AskBarLogic.assistantPackage("com.google.android.googlequicksearchbox/com.x.Y", null, "com.outsmartis.yoke"))
    }

    @Test
    fun assistantPackageFallsBackToResolved() {
        assertEquals("com.a", AskBarLogic.assistantPackage(null, "com.a", "com.outsmartis.yoke"))
        assertEquals("com.a", AskBarLogic.assistantPackage("  ", "com.a", "com.outsmartis.yoke"))
    }

    @Test
    fun assistantPackageNullWhenYokeChooserOrUnknown() {
        assertEquals(null, AskBarLogic.assistantPackage("com.outsmartis.yoke/.assist.AssistEntry", "com.a", "com.outsmartis.yoke"))
        assertEquals(null, AskBarLogic.assistantPackage(null, "android", "com.outsmartis.yoke"))
        assertEquals(null, AskBarLogic.assistantPackage(null, null, "com.outsmartis.yoke"))
    }
}
