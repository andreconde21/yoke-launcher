package com.outsmartis.yoke.helper

/**
 * When the launcher drops back to its home screen: whenever the user left Yoke for another app by any
 * route (Home, Recents, a notification, screen off), the next time Yoke starts it shows home. Yoke's own
 * launches (file and photo pickers, permission prompts, settings jumps, its own screens) keep the screen
 * and their result: those hold the reset until they return. One function, [resetOnReturn], decides for
 * MainActivity; the pop itself runs in onStart, where the fragment manager can take it.
 */
object HomeReturn {

    /**
     * Called from onStop: should the next start land on home? Not for a recreate, a Private Space
     * unlock, or a stop caused by something Yoke itself launched ([inAppHold]).
     */
    fun resetOnReturn(changingConfigurations: Boolean, privateSpaceToggling: Boolean, inAppHold: Boolean): Boolean =
        !changingConfigurations && !privateSpaceToggling && !inAppHold

    fun shouldPop(privateSpaceToggling: Boolean): Boolean = !privateSpaceToggling

    /**
     * Does a launch by Yoke itself keep the current screen? Activity.startActivity and
     * Fragment.startActivity both arrive at startActivityForResult with request code -1, so the code alone
     * says "result wanted" only from 0 up. Yoke's own screens (explicit component or package) and the
     * system Settings jumps (Accessibility, Assistant, default home, app info, notification access) keep
     * the screen too, and so does a launch that opted in with [keepScreen] (the helper apps and store pages
     * Yoke sends the user to from its own Settings). Anything else (another app, a URI, a link) never does: coming back lands on home.
     */
    fun holdsScreen(
        requestCode: Int, action: String?, targetPackage: String?, ownPackage: String, explicitHold: Boolean = false,
    ): Boolean =
        requestCode >= 0 || explicitHold || targetPackage == ownPackage || action?.startsWith("android.settings.") == true

    /** Intent extra set by [keepScreen]: this launch is one of Yoke's own helper jumps (Shizuku, Conductore, a store page). */
    const val EXTRA_HOLD = "com.outsmartis.yoke.extra.KEEP_SCREEN"

    const val HOLD_GRACE_MS = 2000L

    /** Jumps into system Settings, the default-launcher chooser or a role request: the system sends HOME back on Back. */
    fun isSystemJump(action: String?, homeCategory: Boolean): Boolean =
        homeCategory || action == "android.app.role.action.REQUEST_ROLE" || action?.startsWith("android.settings.") == true

    /** Where a pending scroll position can be restored: null while the content is still too short. */
    fun scrollTarget(wanted: Int, contentHeight: Int, viewHeight: Int): Int? {
        if (wanted <= 0) return null
        return if (contentHeight - viewHeight >= wanted) wanted else null
    }
}
