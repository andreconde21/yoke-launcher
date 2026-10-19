package com.outsmartis.yoke.helper

import com.outsmartis.yoke.ui.SettingsScroll

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
    fun anyStopYokeDidNotCauseResetsToHomeOnReturn() {
        assertTrue(HomeReturn.resetOnReturn(false, false, false)) // Home, Recents, a notification, screen off
    }

    @Test
    fun recreatePrivateSpaceAndYokesOwnLaunchesKeepTheScreen() {
        assertFalse(HomeReturn.resetOnReturn(true, false, false))
        assertFalse(HomeReturn.resetOnReturn(false, true, false))
        assertFalse(HomeReturn.resetOnReturn(false, false, true))
    }

    private val own = "com.outsmartis.yoke"

    @Test
    fun resultLaunchesHoldButPlainStartActivityDoesNot() {
        // Activity.startActivity and Fragment.startActivity both reach the override with request code -1
        assertTrue(HomeReturn.holdsScreen(0, null, null, own)) // ActivityResultLauncher codes start at 0
        assertTrue(HomeReturn.holdsScreen(65537, "android.intent.action.OPEN_DOCUMENT_TREE", null, own))
        assertFalse(HomeReturn.holdsScreen(-1, "android.intent.action.VIEW", null, own)) // a link or URI
    }

    @Test
    fun yokesOwnScreensAndSettingsJumpsHoldTheScreen() {
        assertTrue(HomeReturn.holdsScreen(-1, null, own, own))
        assertTrue(HomeReturn.holdsScreen(-1, "android.settings.ACCESSIBILITY_SETTINGS", null, own))
        assertTrue(HomeReturn.holdsScreen(-1, "android.settings.APPLICATION_DETAILS_SETTINGS", null, own))
        assertTrue(HomeReturn.holdsScreen(-1, "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", null, own))
    }

    @Test
    fun anExplicitOptInHoldsAHelperJump() {
        // Shizuku, Conductore, a store page opened from Yoke's own Settings
        assertTrue(HomeReturn.holdsScreen(-1, "android.intent.action.MAIN", "moe.shizuku.privileged.api", own, explicitHold = true))
        assertTrue(HomeReturn.holdsScreen(-1, "android.intent.action.VIEW", null, own, explicitHold = true))
        assertFalse(HomeReturn.holdsScreen(-1, "android.intent.action.VIEW", null, own, explicitHold = false))
    }

    @Test
    fun externalAppsAndUrisNeverHold() {
        assertFalse(HomeReturn.holdsScreen(-1, "android.intent.action.MAIN", "com.google.android.apps.maps", own))
        assertFalse(HomeReturn.holdsScreen(-1, "android.intent.action.VIEW", null, own))
        assertFalse(HomeReturn.holdsScreen(-1, "android.intent.action.SEND", "com.whatsapp", own))
    }

    @Test
    fun scrollIsRestoredOnlyOnceTheContentIsTallEnough() {
        assertNull(HomeReturn.scrollTarget(900, 1500, 1000)) // only 500 scrollable: rows still filling in
        assertEquals(900, HomeReturn.scrollTarget(900, 2000, 1000))
        assertEquals(1000, HomeReturn.scrollTarget(1000, 2000, 1000))
        assertNull(HomeReturn.scrollTarget(0, 2000, 1000))
    }
}

class SettingsScrollTest {

    @Test
    fun positionSurvivesTwoRecreatesInARow() {
        SettingsScroll.y = 900
        SettingsScroll.onViewDestroyed(changingConfigurations = true)
        assertEquals(900, SettingsScroll.takeWanted()) // first new view, not yet laid out
        SettingsScroll.onViewDestroyed(changingConfigurations = true) // recreated again before the restore
        assertEquals(900, SettingsScroll.takeWanted())
    }

    @Test
    fun leavingSettingsForRealForgetsThePosition() {
        SettingsScroll.y = 900
        SettingsScroll.onViewDestroyed(changingConfigurations = false)
        assertEquals(0, SettingsScroll.takeWanted())
    }
}
