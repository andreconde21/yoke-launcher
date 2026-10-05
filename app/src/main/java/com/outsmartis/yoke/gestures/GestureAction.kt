package com.outsmartis.yoke.gestures

import org.json.JSONObject

/**
 * What a [Trigger] does. Pure data: execution lives in GestureActionRunner.
 * Each action serialises to a small JSON object with a stable `type` id.
 */
sealed class GestureAction(val type: String, val label: String) {

    object None : GestureAction("none", "None")

    /** [user] is `UserHandle.toString()`, the format the rest of Yoke stores and resolves. */
    data class OpenApp(
        val packageName: String,
        val activityClassName: String?,
        val user: String,
    ) : GestureAction("open_app", "Open app")

    data class OpenShortcut(
        val packageName: String,
        val shortcutId: String,
        val user: String,
    ) : GestureAction("open_shortcut", "Open shortcut")

    /** [uri] is anything Intent.parseUri accepts (https:, content:, intent:#Intent;...;end). */
    data class OpenUri(val uri: String) : GestureAction("open_uri", "Open link")

    object AppDrawer : GestureAction("app_drawer", "App drawer")
    object Search : GestureAction("search", "Search")
    object Notifications : GestureAction("notifications", "Notifications")
    object QuickSettings : GestureAction("quick_settings", "Quick settings")
    object Lock : GestureAction("lock", "Lock screen")
    object RecentApps : GestureAction("recent_apps", "Recent apps")
    object Torch : GestureAction("torch", "Torch")
    object MediaPlayPause : GestureAction("media_play_pause", "Media play / pause")
    object MediaNext : GestureAction("media_next", "Media next")
    object MediaPrevious : GestureAction("media_previous", "Media previous")
    object CockpitQuickAdd : GestureAction("cockpit_quick_add", "Cockpit quick add")
    object CockpitBoard : GestureAction("cockpit_board", "Open Cockpit board")
    object CockpitCalendar : GestureAction("cockpit_calendar", "Open Cockpit calendar")
    object CockpitToday : GestureAction("cockpit_today", "Cockpit today sheet")
    object ConductoreSheet : GestureAction("conductore_sheet", "Conductore sheet")
    object CommandPalette : GestureAction("command_palette", "Command palette")
    object ThemePicker : GestureAction("theme_picker", "Theme picker")
    object GestureCheatSheet : GestureAction("gesture_cheat_sheet", "Gesture cheat sheet")
    object Settings : GestureAction("settings", "Settings")
    object WidgetPage : GestureAction("widget_page", "Widgets page")
    object ToggleGrayscale : GestureAction("toggle_grayscale", "Toggle grayscale")
    object PauseGrayscale : GestureAction("pause_grayscale", "Pause grayscale 15 min")

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        when (val a = this@GestureAction) {
            is OpenApp -> {
                put("package", a.packageName)
                a.activityClassName?.let { put("activity", it) }
                put("user", a.user)
            }
            is OpenShortcut -> {
                put("package", a.packageName)
                put("shortcutId", a.shortcutId)
                put("user", a.user)
            }
            is OpenUri -> put("uri", a.uri)
            else -> {}
        }
    }

    companion object {
        /** Actions without parameters, in the order the picker lists them. */
        val simple: List<GestureAction> = listOf(
            None, AppDrawer, Search, Notifications, QuickSettings, Lock, RecentApps, Torch,
            MediaPlayPause, MediaNext, MediaPrevious, CockpitQuickAdd, CockpitBoard, CockpitCalendar, CockpitToday, ConductoreSheet,
            CommandPalette, ThemePicker, GestureCheatSheet, Settings,
            WidgetPage, ToggleGrayscale, PauseGrayscale,
        )

        /** Throws [IllegalArgumentException] with a readable message when [json] is not a valid action. */
        fun fromJson(json: JSONObject): GestureAction {
            val type = json.optString("type", "")
            return when (type) {
                "open_app" -> {
                    val pkg = json.optString("package", "")
                    require(pkg.isNotBlank()) { "open_app needs a package" }
                    OpenApp(
                        pkg,
                        json.optString("activity", "").ifBlank { null },
                        json.optString("user", ""),
                    )
                }
                "open_shortcut" -> {
                    val pkg = json.optString("package", "")
                    val id = json.optString("shortcutId", "")
                    require(pkg.isNotBlank() && id.isNotBlank()) { "open_shortcut needs a package and shortcutId" }
                    OpenShortcut(pkg, id, json.optString("user", ""))
                }
                "open_uri" -> {
                    val uri = json.optString("uri", "")
                    require(uri.isNotBlank()) { "open_uri needs a uri" }
                    OpenUri(uri)
                }
                else -> simple.firstOrNull { it.type == type }
                    ?: throw IllegalArgumentException("unknown action type \"$type\"")
            }
        }
    }
}
