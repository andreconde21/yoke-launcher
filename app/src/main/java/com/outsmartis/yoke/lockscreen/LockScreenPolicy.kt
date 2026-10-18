package com.outsmartis.yoke.lockscreen

/**
 * The Yoke lock screen's decisions, free of Android types so they are unit-tested. The Android
 * constants it needs are duplicated below, like [com.outsmartis.yoke.notifications.NotificationLogic] does.
 */
object LockScreenPolicy {

    /** Why the screen is (not) put up when the display turns off; logged for the device spike. */
    enum class Decision { SHOW, OFF, BEFORE_FIRST_UNLOCK, CALL, URGENT_SOUND }

    enum class Unlock { DISMISS_KEYGUARD, FINISH }

    // android.media.AudioManager modes
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3
    const val MODE_CALL_SCREENING = 4
    const val MODE_CALL_REDIRECT = 5
    const val MODE_COMMUNICATION_REDIRECT = 6

    // android.media.AudioAttributes usages
    const val USAGE_VOICE_COMMUNICATION = 2
    const val USAGE_ALARM = 4
    const val USAGE_NOTIFICATION_RINGTONE = 6

    // android.app.Notification visibilities
    const val VISIBILITY_SECRET = -1
    const val VISIBILITY_PRIVATE = 0
    const val VISIBILITY_PUBLIC = 1

    // android.media.session.PlaybackState states
    const val STATE_NONE = 0
    const val STATE_STOPPED = 1

    /**
     * A ringing, active or VoIP call, from AudioManager.getMode(): needs no permission, unlike
     * TelephonyManager.getCallState() (READ_PHONE_STATE from Android 12).
     */
    fun inCall(audioMode: Int): Boolean = audioMode in MODE_RINGTONE..MODE_COMMUNICATION_REDIRECT

    /** An alarm, ringtone or call is sounding (AudioManager.getActivePlaybackConfigurations usages). */
    fun urgentSound(usages: Collection<Int>): Boolean =
        usages.any { it == USAGE_ALARM || it == USAGE_NOTIFICATION_RINGTONE || it == USAGE_VOICE_COMMUNICATION }

    fun decide(enabled: Boolean, userUnlocked: Boolean, audioMode: Int, playingUsages: Collection<Int>): Decision = when {
        !enabled -> Decision.OFF
        !userUnlocked -> Decision.BEFORE_FIRST_UNLOCK
        inCall(audioMode) -> Decision.CALL
        urgentSound(playingUsages) -> Decision.URGENT_SOUND
        else -> Decision.SHOW
    }

    /** The direct start did not bring the screen up: post the full-screen notification instead, if allowed. */
    fun needsFullScreenFallback(activityUp: Boolean, canUseFullScreenIntent: Boolean): Boolean =
        !activityUp && canUseFullScreenIntent

    /** Locked (secure or swipe): the system dismisses it, PIN and biometrics stay its own. Not locked: just leave. */
    fun onSwipeUp(keyguardLocked: Boolean): Unlock = if (keyguardLocked) Unlock.DISMISS_KEYGUARD else Unlock.FINISH

    /** Stopped while the screen is on: a call, alarm or the camera came in front; get out of its way for good. */
    fun finishOnStop(interactive: Boolean, finishing: Boolean): Boolean = interactive && !finishing

    /** Woken with no lock (the "lock after a delay" window, or no lock at all): nothing to glance over. */
    fun finishOnResume(interactive: Boolean, keyguardLocked: Boolean): Boolean = interactive && !keyguardLocked

    /** An upward drag of at least [minFraction] of the screen height ([dy] is negative upwards). */
    fun isSwipeUp(dy: Float, height: Int, minFraction: Float = 0.12f): Boolean = height > 0 && -dy >= height * minFraction

    /** A notification line may appear at all: the system shows notifications on the lock screen and it is not secret. */
    fun shownOnLock(visibility: Int, systemShowsOnLock: Boolean): Boolean =
        systemShowsOnLock && visibility != VISIBILITY_SECRET

    /** Its text may appear: public, or the user allows sensitive content on the lock screen. */
    fun revealOnLock(visibility: Int, systemAllowsPrivate: Boolean): Boolean =
        visibility == VISIBILITY_PUBLIC || systemAllowsPrivate

    fun showMusic(title: String, artist: String, state: Int?): Boolean =
        (title.isNotBlank() || artist.isNotBlank()) && state != null && state != STATE_NONE && state != STATE_STOPPED

    /** `Title — Artist`, or whichever part exists. */
    fun musicLine(title: String, artist: String): String {
        val t = title.trim()
        val a = artist.trim()
        return when {
            t.isEmpty() -> a
            a.isEmpty() -> t
            else -> "$t — $a"
        }
    }
}
