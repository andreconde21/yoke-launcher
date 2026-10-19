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

    private val HOLDING_ACTIONS = setOf(
        "android.intent.action.OPEN_DOCUMENT", "android.intent.action.OPEN_DOCUMENT_TREE",
        "android.intent.action.CREATE_DOCUMENT", "android.intent.action.GET_CONTENT", "android.intent.action.PICK",
        "android.provider.action.PICK_IMAGES", "android.intent.action.CHOOSER",
    )

    /**
     * Does a launch by Yoke itself keep the current screen? Anything that wants a result does; so do Yoke's
     * own screens, the system settings and the document / photo pickers. Opening another app does not:
     * coming back from it lands on home.
     */
    fun holdsScreen(wantsResult: Boolean, action: String?, targetPackage: String?, ownPackage: String): Boolean =
        wantsResult || targetPackage == ownPackage || action in HOLDING_ACTIONS ||
            action?.startsWith("android.settings.") == true

    /** Where a pending scroll position can be restored: null while the content is still too short. */
    fun scrollTarget(wanted: Int, contentHeight: Int, viewHeight: Int): Int? {
        if (wanted <= 0) return null
        return if (contentHeight - viewHeight >= wanted) wanted else null
    }
}
