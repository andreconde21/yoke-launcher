package com.outsmartis.yoke.lockscreen

import com.outsmartis.yoke.lockscreen.LockScreenPolicy.Decision
import com.outsmartis.yoke.lockscreen.LockScreenPolicy.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenPolicyTest {

    private fun decide(
        enabled: Boolean = true,
        launchPathOpen: Boolean = true,
        unlocked: Boolean = true,
        mode: Int = LockScreenPolicy.MODE_NORMAL,
        usages: List<Int> = emptyList(),
        interactive: Boolean = false,
        lockPending: Boolean = true,
        otherInFront: Boolean = false,
    ) = LockScreenPolicy.decide(enabled, launchPathOpen, unlocked, mode, usages, interactive, lockPending, otherInFront)

    @Test fun showsWhenOnAndIdle() = assertEquals(Decision.SHOW, decide())

    @Test fun offByTheSetting() = assertEquals(Decision.OFF, decide(enabled = false))

    @Test fun neverBeforeTheFirstUnlock() = assertEquals(Decision.BEFORE_FIRST_UNLOCK, decide(unlocked = false))

    @Test fun offWinsOverEverythingElse() =
        assertEquals(Decision.OFF, decide(enabled = false, unlocked = false, mode = LockScreenPolicy.MODE_IN_CALL))

    @Test fun ringingCallIsLeftAlone() = assertEquals(Decision.CALL, decide(mode = LockScreenPolicy.MODE_RINGTONE))

    @Test fun activeCallIsLeftAlone() = assertEquals(Decision.CALL, decide(mode = LockScreenPolicy.MODE_IN_CALL))

    @Test fun voipCallIsLeftAlone() = assertEquals(Decision.CALL, decide(mode = LockScreenPolicy.MODE_IN_COMMUNICATION))

    @Test fun callScreeningAndRedirectsCount() {
        assertTrue(LockScreenPolicy.inCall(LockScreenPolicy.MODE_CALL_SCREENING))
        assertTrue(LockScreenPolicy.inCall(LockScreenPolicy.MODE_CALL_REDIRECT))
        assertTrue(LockScreenPolicy.inCall(LockScreenPolicy.MODE_COMMUNICATION_REDIRECT))
    }

    @Test fun invalidAndCurrentModesAreNotCalls() {
        assertFalse(LockScreenPolicy.inCall(-2))
        assertFalse(LockScreenPolicy.inCall(-1))
        assertFalse(LockScreenPolicy.inCall(LockScreenPolicy.MODE_NORMAL))
    }

    @Test fun ringingAlarmIsLeftAlone() =
        assertEquals(Decision.URGENT_SOUND, decide(usages = listOf(1, LockScreenPolicy.USAGE_ALARM)))

    @Test fun ringtoneSoundIsLeftAlone() =
        assertEquals(Decision.URGENT_SOUND, decide(usages = listOf(LockScreenPolicy.USAGE_NOTIFICATION_RINGTONE)))

    @Test fun musicDoesNotBlock() = assertEquals(Decision.SHOW, decide(usages = listOf(1)))

    @Test fun noLaunchPathMeansNothing() = assertEquals(Decision.NO_LAUNCH_PATH, decide(launchPathOpen = false))

    @Test fun screenAlreadyOnIsNeverCovered() = assertEquals(Decision.SCREEN_ON, decide(interactive = true))

    @Test fun noLockMeansNothingToGlanceOver() = assertEquals(Decision.NOT_LOCKED, decide(lockPending = false))

    @Test fun cameraOrEmergencyInFrontIsNotCovered() = assertEquals(Decision.OTHER_ACTIVITY, decide(otherInFront = true))

    @Test fun callBeatsLockAndCameraChecks() =
        assertEquals(Decision.CALL, decide(mode = LockScreenPolicy.MODE_IN_CALL, lockPending = false, otherInFront = true))

    @Test fun screenOnBeatsCallAndSound() =
        assertEquals(Decision.SCREEN_ON, decide(interactive = true, mode = LockScreenPolicy.MODE_RINGTONE))

    @Test fun serviceRunsOnlyWhenOnAndLaunchable() {
        assertTrue(LockScreenPolicy.shouldRunService(enabled = true, launchPathOpen = true))
        assertFalse(LockScreenPolicy.shouldRunService(enabled = true, launchPathOpen = false)) // restored backup, no accessibility
        assertFalse(LockScreenPolicy.shouldRunService(enabled = false, launchPathOpen = true))
    }

    @Test fun createdWhileOffAfterOurScreenOffIsKept() =
        assertTrue(LockScreenPolicy.acceptCreate(interactive = false, offAt = 5000L, screenOnAt = 1000L))

    @Test fun createdWhileTheScreenIsOnIsRefused() =
        assertFalse(LockScreenPolicy.acceptCreate(interactive = true, offAt = 5000L, screenOnAt = 1000L))

    @Test fun screenCameOnAfterTheOffIsRefused() =
        assertFalse(LockScreenPolicy.acceptCreate(interactive = false, offAt = 5000L, screenOnAt = 5200L))

    @Test fun startWithoutAnOffStampIsRefused() =
        assertFalse(LockScreenPolicy.acceptCreate(interactive = false, offAt = 0L, screenOnAt = 0L))

    @Test fun protectedForegroundMatchesOnlyListedPackages() {
        val protectedPkgs = setOf("com.cam", "com.dialer")
        assertTrue(LockScreenPolicy.isProtectedForeground("com.cam", protectedPkgs))
        assertFalse(LockScreenPolicy.isProtectedForeground("com.chrome", protectedPkgs))
        assertFalse(LockScreenPolicy.isProtectedForeground(null, protectedPkgs))
    }

    @Test fun channelOverrideCanOnlyHideMore() {
        val none = LockScreenPolicy.VISIBILITY_NO_OVERRIDE
        assertEquals(LockScreenPolicy.VISIBILITY_PRIVATE, LockScreenPolicy.effectiveVisibility(LockScreenPolicy.VISIBILITY_PRIVATE, none))
        assertEquals(LockScreenPolicy.VISIBILITY_SECRET, LockScreenPolicy.effectiveVisibility(LockScreenPolicy.VISIBILITY_PUBLIC, LockScreenPolicy.VISIBILITY_SECRET))
        assertEquals(LockScreenPolicy.VISIBILITY_PRIVATE, LockScreenPolicy.effectiveVisibility(LockScreenPolicy.VISIBILITY_PUBLIC, LockScreenPolicy.VISIBILITY_PRIVATE))
        assertEquals(LockScreenPolicy.VISIBILITY_PRIVATE, LockScreenPolicy.effectiveVisibility(LockScreenPolicy.VISIBILITY_PRIVATE, LockScreenPolicy.VISIBILITY_PUBLIC))
        assertEquals(LockScreenPolicy.VISIBILITY_SECRET, LockScreenPolicy.effectiveVisibility(LockScreenPolicy.VISIBILITY_SECRET, none))
    }

    @Test fun swipeOnALockedPhoneAsksTheSystem() = assertEquals(Unlock.DISMISS_KEYGUARD, LockScreenPolicy.onSwipeUp(true))

    @Test fun swipeWithoutALockJustLeaves() = assertEquals(Unlock.FINISH, LockScreenPolicy.onSwipeUp(false))

    @Test fun somethingInFrontWhileTheScreenIsOnMakesItLeave() {
        assertTrue(LockScreenPolicy.finishOnStop(interactive = true, finishing = false))
        assertFalse(LockScreenPolicy.finishOnStop(interactive = false, finishing = false)) // screen went off: stay up
        assertFalse(LockScreenPolicy.finishOnStop(interactive = true, finishing = true))
    }

    @Test fun wakingWithoutALockMakesItLeave() {
        assertTrue(LockScreenPolicy.finishOnResume(interactive = true, keyguardLocked = false))
        assertFalse(LockScreenPolicy.finishOnResume(interactive = true, keyguardLocked = true))
        assertFalse(LockScreenPolicy.finishOnResume(interactive = false, keyguardLocked = false)) // brought up while off
    }

    @Test fun swipeNeedsDistanceUpwards() {
        assertTrue(LockScreenPolicy.isSwipeUp(dy = -300f, height = 2000))
        assertFalse(LockScreenPolicy.isSwipeUp(dy = -100f, height = 2000))
        assertFalse(LockScreenPolicy.isSwipeUp(dy = 400f, height = 2000))
        assertFalse(LockScreenPolicy.isSwipeUp(dy = -400f, height = 0))
    }

    @Test fun secretNotificationsNeverShow() {
        assertFalse(LockScreenPolicy.shownOnLock(LockScreenPolicy.VISIBILITY_SECRET, systemShowsOnLock = true))
        assertTrue(LockScreenPolicy.shownOnLock(LockScreenPolicy.VISIBILITY_PRIVATE, systemShowsOnLock = true))
    }

    @Test fun nothingShowsWhenTheSystemHidesLockNotifications() =
        assertFalse(LockScreenPolicy.shownOnLock(LockScreenPolicy.VISIBILITY_PUBLIC, systemShowsOnLock = false))

    @Test fun privateTextFollowsTheSystemSensitiveSetting() {
        assertFalse(LockScreenPolicy.revealOnLock(LockScreenPolicy.VISIBILITY_PRIVATE, systemAllowsPrivate = false))
        assertTrue(LockScreenPolicy.revealOnLock(LockScreenPolicy.VISIBILITY_PRIVATE, systemAllowsPrivate = true))
        assertTrue(LockScreenPolicy.revealOnLock(LockScreenPolicy.VISIBILITY_PUBLIC, systemAllowsPrivate = false))
    }

    @Test fun musicNeedsATitleAndAPlayingOrPausedSession() {
        assertTrue(LockScreenPolicy.showMusic("Song", "", 3))
        assertTrue(LockScreenPolicy.showMusic("", "Artist", 2))
        assertFalse(LockScreenPolicy.showMusic("", " ", 3))
        assertFalse(LockScreenPolicy.showMusic("Song", "Artist", LockScreenPolicy.STATE_STOPPED))
        assertFalse(LockScreenPolicy.showMusic("Song", "Artist", LockScreenPolicy.STATE_NONE))
        assertFalse(LockScreenPolicy.showMusic("Song", "Artist", null))
    }

    @Test fun musicLineJoinsWhatExists() {
        assertEquals("Song — Artist", LockScreenPolicy.musicLine(" Song ", "Artist"))
        assertEquals("Song", LockScreenPolicy.musicLine("Song", ""))
        assertEquals("Artist", LockScreenPolicy.musicLine("", "Artist"))
    }
}
