package com.outsmartis.yoke.helper

/**
 * When the launcher drops back to its home screen. Only when the user really goes home (the HOME
 * intent arrives in onNewIntent) or the screen turns off. Stops and "leave hints" are not triggers:
 * Yoke's own file pickers, permission prompts and settings jumps cause them too, and popping the
 * back stack then destroys the screen that is waiting for the result.
 */
object HomeReturn {

    fun shouldPop(privateSpaceToggling: Boolean): Boolean = !privateSpaceToggling

    /** Where a pending scroll position can be restored: null while the content is still too short. */
    fun scrollTarget(wanted: Int, contentHeight: Int, viewHeight: Int): Int? {
        if (wanted <= 0) return null
        return if (contentHeight - viewHeight >= wanted) wanted else null
    }
}
