package com.outsmartis.yoke.gestures

import org.json.JSONException
import org.json.JSONObject

/** Result of parsing stored or imported gesture JSON. */
sealed class GestureParse {
    data class Ok(val config: GestureConfig) : GestureParse()
    data class Error(val message: String) : GestureParse()
}

/**
 * Old swipe left/right apps and the lock switch, as stored before gestures became
 * configurable. A null [GestureDefaults.migrate] argument means a fresh install.
 */
data class LegacyGestureState(
    val swipeLeft: GestureAction?,    // OpenApp / OpenShortcut when an app was chosen, else null
    val swipeLeftEnabled: Boolean,
    val swipeRight: GestureAction?,
    val swipeRightEnabled: Boolean,
    val lockModeOn: Boolean,
    val clockApp: GestureAction.OpenApp?,
    val calendarApp: GestureAction.OpenApp?,
)

/** Immutable trigger-to-action map. Triggers without an entry are [GestureAction.None]. */
data class GestureConfig(val actions: Map<Trigger, GestureAction>) {

    operator fun get(trigger: Trigger): GestureAction = actions[trigger] ?: GestureAction.None

    fun isBound(trigger: Trigger): Boolean = get(trigger) != GestureAction.None

    fun with(trigger: Trigger, action: GestureAction): GestureConfig =
        GestureConfig(actions + (trigger to action))

    fun bound(): List<Pair<Trigger, GestureAction>> =
        Trigger.entries.mapNotNull { t -> get(t).takeIf { it != GestureAction.None }?.let { t to it } }

    fun toJson(): String {
        val gestures = JSONObject()
        Trigger.entries.forEach { gestures.put(it.name, get(it).toJson()) }
        return JSONObject().put("version", VERSION).put("gestures", gestures).toString(2)
    }

    companion object {
        const val VERSION = 1

        /** Strict: any unknown trigger, bad action or malformed JSON is an error and nothing is applied. */
        fun parse(text: String): GestureParse {
            val root = try {
                JSONObject(text.trim())
            } catch (e: JSONException) {
                return GestureParse.Error("Not valid JSON")
            }
            val gestures = root.optJSONObject("gestures")
                ?: return GestureParse.Error("Missing \"gestures\" object")
            val map = LinkedHashMap<Trigger, GestureAction>()
            for (key in gestures.keys()) {
                val trigger = Trigger.fromName(key)
                    ?: return GestureParse.Error("Unknown gesture \"$key\"")
                val obj = gestures.optJSONObject(key)
                    ?: return GestureParse.Error("Gesture \"$key\" is not an object")
                try {
                    map[trigger] = GestureAction.fromJson(obj)
                } catch (e: IllegalArgumentException) {
                    return GestureParse.Error("$key: ${e.message}")
                }
            }
            return GestureParse.Ok(GestureConfig(map))
        }
    }
}

object GestureDefaults {

    const val CLOCK_URI = "intent:#Intent;action=android.intent.action.SHOW_ALARMS;end"
    const val CALENDAR_URI = "content://com.android.calendar/time"

    fun create(): GestureConfig = GestureConfig(
        mapOf(
            Trigger.SWIPE_UP to GestureAction.AppDrawer,
            Trigger.SWIPE_DOWN to GestureAction.Notifications,
            Trigger.TWO_FINGER_SWIPE_DOWN to GestureAction.QuickSettings,
            Trigger.SWIPE_LEFT to GestureAction.CockpitQuickAdd,
            Trigger.SWIPE_RIGHT to GestureAction.ConductoreSheet,
            Trigger.DOUBLE_TAP to GestureAction.Lock,
            Trigger.LONG_PRESS_EMPTY to GestureAction.CommandPalette,
            Trigger.TWO_FINGER_SWIPE_UP to GestureAction.CommandPalette,
            Trigger.PINCH_IN to GestureAction.ThemePicker,
            Trigger.TAP_CLOCK to GestureAction.OpenUri(CLOCK_URI),
            Trigger.TAP_DATE to GestureAction.OpenUri(CALENDAR_URI),
        )
    )

    /**
     * Defaults, adjusted for what an existing install had configured: chosen swipe apps stay,
     * a switched-off swipe or double-tap-lock stays off, a chosen clock/calendar app stays.
     */
    fun migrate(legacy: LegacyGestureState?): GestureConfig {
        val base = create()
        if (legacy == null) return base
        var config = base
        if (legacy.swipeLeft != null) config = config.with(Trigger.SWIPE_LEFT, legacy.swipeLeft)
        if (!legacy.swipeLeftEnabled) config = config.with(Trigger.SWIPE_LEFT, GestureAction.None)
        if (legacy.swipeRight != null) config = config.with(Trigger.SWIPE_RIGHT, legacy.swipeRight)
        if (!legacy.swipeRightEnabled) config = config.with(Trigger.SWIPE_RIGHT, GestureAction.None)
        if (!legacy.lockModeOn) config = config.with(Trigger.DOUBLE_TAP, GestureAction.None)
        legacy.clockApp?.let { config = config.with(Trigger.TAP_CLOCK, it) }
        legacy.calendarApp?.let { config = config.with(Trigger.TAP_DATE, it) }
        return config
    }
}
