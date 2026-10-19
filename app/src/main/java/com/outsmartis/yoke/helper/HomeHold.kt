package com.outsmartis.yoke.helper

/**
 * The in-app hold of MainActivity: Yoke started one of its own jumps and the screen (and its result) must
 * survive. It records what really happened to Yoke since, so a launch that only paused it (a dialog, a
 * translucent own activity) never lets the hold outlive its jump, and so only a real stop inside a
 * system Settings / role screen lets the HOME the system sends on Back through without a pop.
 *
 * [now] is a monotonic clock in ms, passed in so this stays a pure object.
 */
class HomeHold {
    var active = false
        private set
    private var systemJump = false
    private var paused = false
    private var stopped = false
    private var since = 0L

    /** [system]: a system Settings, default-launcher chooser or role jump (see [HomeReturn.isSystemJump]). */
    fun start(system: Boolean, now: Long) {
        active = true
        systemJump = system
        paused = false
        stopped = false
        since = now
    }

    fun clear() {
        active = false
        systemJump = false
        paused = false
        stopped = false
    }

    fun onPause() {
        if (active) paused = true
    }

    /** Returns whether the stop was caused by the held jump (the caller then does not reset on return). */
    fun onStop(): Boolean {
        if (active) stopped = true
        return active
    }

    /**
     * Back in front. A pause or stop in between means the jump has come and gone; a resume with neither
     * (the activity re-delivering an intent while the jump is being started) keeps a hold that was only just
     * set, but not for longer than [HomeReturn.HOLD_GRACE_MS].
     */
    fun onResume(now: Long) {
        if (stopped || paused || now - since > HomeReturn.HOLD_GRACE_MS) clear()
    }

    /**
     * A HOME intent while a system Settings/role screen that stopped Yoke is being left: the system returning
     * (Back), not the Home button, so no pop. Anything else, including a hold from a helper app or one that
     * only paused Yoke, is a real Home press.
     */
    fun swallowsHome(isHomeIntent: Boolean, hasRunAction: Boolean): Boolean =
        active && stopped && systemJump && isHomeIntent && !hasRunAction
}
