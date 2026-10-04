package com.outsmartis.yoke.gestures

/**
 * Everything on the home screen that can run an action.
 * [label] is what the Gestures screen and the cheat sheet show.
 */
enum class Trigger(val label: String) {
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    TWO_FINGER_SWIPE_UP("Two-finger swipe up"),
    TWO_FINGER_SWIPE_DOWN("Two-finger swipe down"),
    TWO_FINGER_SWIPE_LEFT("Two-finger swipe left"),
    TWO_FINGER_SWIPE_RIGHT("Two-finger swipe right"),

    // Swipes that start at the screen edge and travel along it. Inward swipes from the
    // edges are deliberately not triggers: they collide with the system back gesture.
    EDGE_LEFT_SWIPE_UP("Left edge, swipe up"),
    EDGE_LEFT_SWIPE_DOWN("Left edge, swipe down"),
    EDGE_RIGHT_SWIPE_UP("Right edge, swipe up"),
    EDGE_RIGHT_SWIPE_DOWN("Right edge, swipe down"),

    DOUBLE_TAP("Double tap"),
    LONG_PRESS_EMPTY("Long press empty space"),
    TAP_CLOCK("Tap clock"),
    TAP_DATE("Tap date"),
    PINCH_IN("Pinch in"),
    PINCH_OUT("Pinch out"),
    VOLUME_UP("Volume up (on home)"),
    VOLUME_DOWN("Volume down (on home)"),
    HOME_ON_HOME("Home button on home"),
    BACK_ON_HOME("Back on home");

    companion object {
        fun fromName(name: String): Trigger? = entries.firstOrNull { it.name == name }
    }
}
