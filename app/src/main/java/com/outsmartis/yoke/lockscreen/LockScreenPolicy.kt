package com.outsmartis.yoke.lockscreen

/**
 * The Yoke lock screen's decisions, free of Android types so they are unit-tested. The Android
 * constants it needs are duplicated below, like [com.outsmartis.yoke.notifications.NotificationLogic] does.
 */
object LockScreenPolicy {

    /** Why the screen is (not) put up when the display turns off; logged for the device spike. */
    enum class Decision { SHOW, OFF, NO_LAUNCH_PATH, BEFORE_FIRST_UNLOCK, SCREEN_ON, CALL, URGENT_SOUND, NOT_LOCKED, OTHER_ACTIVITY }

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

    /** NotificationChannel.getLockscreenVisibility() when the user and the app set nothing. */
    const val VISIBILITY_NO_OVERRIDE = -1000

    // android.media.session.PlaybackState states
    const val STATE_NONE = 0
    const val STATE_STOPPED = 1

    /**
     * A ringing, active or VoIP call, from AudioManager.getMode(): needs no permission, unlike
     * TelephonyManager.getCallState() (READ_PHONE_STATE from Android 12).
     */
    fun inCall(audioMode: Int): Boolean = audioMode in MODE_RINGTONE..MODE_COMMUNICATION_REDIRECT

    /**
     * The lock screen already up must give way the moment a call starts ringing, so the system's
     * full-screen call UI takes over (its Answer button would otherwise hit our PIN bouncer).
     */
    fun finishForCall(audioMode: Int): Boolean = inCall(audioMode)

    /** An alarm, ringtone or call is sounding (AudioManager.getActivePlaybackConfigurations usages). */
    fun urgentSound(usages: Collection<Int>): Boolean =
        usages.any { it == USAGE_ALARM || it == USAGE_NOTIFICATION_RINGTONE || it == USAGE_VOICE_COMMUNICATION }

    /**
     * Whether to put the screen up now. Re-run right before every launch and again in the
     * activity's onCreate, since the world moves between the screen-off broadcast and the start.
     *
     * @param launchPathOpen the accessibility service is connected (the only launch path)
     * @param interactive the display is on (PowerManager.isInteractive)
     * @param lockPending the keyguard is locked, or the device is secure so it will lock on screen-off
     * @param otherActivityInFront a known show-when-locked app (camera, emergency dialer) is in front
     */
    fun decide(
        enabled: Boolean,
        launchPathOpen: Boolean,
        userUnlocked: Boolean,
        audioMode: Int,
        playingUsages: Collection<Int>,
        interactive: Boolean,
        lockPending: Boolean,
        otherActivityInFront: Boolean,
    ): Decision = when {
        !enabled -> Decision.OFF
        !launchPathOpen -> Decision.NO_LAUNCH_PATH
        !userUnlocked -> Decision.BEFORE_FIRST_UNLOCK
        interactive -> Decision.SCREEN_ON
        inCall(audioMode) -> Decision.CALL
        urgentSound(playingUsages) -> Decision.URGENT_SOUND
        !lockPending -> Decision.NOT_LOCKED
        otherActivityInFront -> Decision.OTHER_ACTIVITY
        else -> Decision.SHOW
    }

    /** The service only runs when it could show something: on, and a launch path is open. */
    fun shouldRunService(enabled: Boolean, launchPathOpen: Boolean): Boolean = enabled && launchPathOpen

    /**
     * The activity may stay: the display is off, and our launch carries the elapsedRealtime of the
     * screen-off it answers ([offAt], 0 when absent, e.g. a relaunch from recents) with no screen-on
     * ([screenOnAt]) since. Rejects the double-press-camera race where the screen woke first.
     */
    fun acceptCreate(
        interactive: Boolean, offAt: Long, screenOnAt: Long,
        afterCall: Boolean = false, keyguardLocked: Boolean = false,
    ): Boolean =
        if (afterCall) showAfterCall(interactive, keyguardLocked)
        else !interactive && offAt > 0L && screenOnAt <= offAt

    /**
     * A call ended with the display still on and the keyguard (PIN) showing: the system keyguard is
     * what the user sees, so put ours back over it. With the screen off the usual screen-off path does it.
     */
    fun showAfterCall(interactive: Boolean, keyguardLocked: Boolean): Boolean = interactive && keyguardLocked

    /** Foreground package is a known show-when-locked app (secure camera, emergency dialer) that must not be covered. */
    fun isProtectedForeground(foreground: String?, protectedPackages: Set<String>): Boolean =
        foreground != null && foreground in protectedPackages

    /**
     * What the lock screen may show of a notification: the app's own visibility, lowered by the
     * user's per-channel override when there is one (secret < private < public).
     */
    fun effectiveVisibility(notificationVisibility: Int, channelOverride: Int): Int =
        if (channelOverride == VISIBILITY_NO_OVERRIDE) notificationVisibility else minOf(notificationVisibility, channelOverride)

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
