package com.outsmartis.yoke.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHoldTest {
    private val settingsJump = HomeReturn.isSystemJump("android.settings.MANAGE_DEFAULT_APPS_SETTINGS", false)

    @Test fun whichJumpsAreSystemOnes() {
        assertTrue(settingsJump)
        assertTrue(HomeReturn.isSystemJump("android.intent.action.MAIN", true)) // the HOME chooser
        assertTrue(HomeReturn.isSystemJump("android.app.role.action.REQUEST_ROLE", false))
        assertFalse(HomeReturn.isSystemJump("android.intent.action.VIEW", false)) // web / Play listing
        assertFalse(HomeReturn.isSystemJump(null, false))
    }

    @Test fun backFromTheDefaultAppsScreenIsNotAHomePress() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        h.onPause(); assertTrue(h.onStop())
        assertTrue(h.swallowsHome(isHomeIntent = true, hasRunAction = false))
    }

    @Test fun aHoldThatOnlyPausedYokeThenHomeStillGoesHome() {
        val h = HomeHold() // a role / battery dialog, or a translucent own activity
        h.start(settingsJump, 0)
        h.onPause()
        h.onResume(500)
        assertFalse(h.active)
        assertFalse(h.swallowsHome(true, false))
    }

    @Test fun aHoldThatNeverSawAStopDoesNotSwallowHome() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        assertFalse(h.swallowsHome(true, false))
    }

    @Test fun aHelperAppJumpThenHomeGoesHome() {
        val h = HomeHold()
        h.start(system = false, now = 0) // Conductore, Shizuku, a store listing
        h.onPause(); h.onStop()
        assertFalse(h.swallowsHome(true, false))
    }

    @Test fun aNormalHomePressWithNoHoldPops() = assertFalse(HomeHold().swallowsHome(true, false))

    @Test fun aResumeWithNeitherPauseNorStopRightAfterTheStartKeepsTheHold() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        h.onResume(50) // the re-delivered HOME intent resuming while the jump is being started
        assertTrue(h.active)
    }

    @Test fun aHoldNothingFollowedIsNotStuck() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        h.onResume(HomeReturn.HOLD_GRACE_MS + 1)
        assertFalse(h.active)
    }

    @Test fun comingBackAfterAStopEndsTheHold() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        h.onPause(); h.onStop()
        h.onResume(60_000)
        assertFalse(h.active)
        assertEquals(false, h.swallowsHome(true, false))
    }

    @Test fun aRequestedActionIsNeverSwallowed() {
        val h = HomeHold()
        h.start(settingsJump, 0)
        h.onPause(); h.onStop()
        assertFalse(h.swallowsHome(true, hasRunAction = true))
    }

    @Test fun theResumeAroundYokesOwnHomeJumpDoesNotEndTheFollowingHold() {
        val h = HomeHold()
        h.start(settingsJump, 0)       // the HOME chooser jump, delivered back to Yoke
        h.onOwnHomeDelivered()
        h.start(settingsJump, 10)      // the default-apps screen
        h.onPause()
        h.onResume(20)                 // the re-resume around the delivered HOME intent
        assertTrue(h.active)
        h.onStop()
        assertTrue(h.swallowsHome(true, false))
    }
}
