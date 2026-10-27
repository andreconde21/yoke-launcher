package com.outsmartis.yoke.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.gestures.GestureConfig
import com.outsmartis.yoke.gestures.GestureDefaults
import com.outsmartis.yoke.gestures.GestureParse
import com.outsmartis.yoke.gestures.LegacyGestureState

class Prefs(private val context: Context) {
    private val PREFS_FILENAME = "com.outsmartis.yoke"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val PENDING_YOKE_LEVEL = "PENDING_YOKE_LEVEL"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val AUTO_LAUNCH_SINGLE = "AUTO_LAUNCH_SINGLE"
    private val WEB_LINKS = "WEB_LINKS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val APP_THEME = "APP_THEME"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val BOLD_FONT = "BOLD_FONT"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"
    private val GESTURES_JSON = "GESTURES_JSON"
    private val WIDGETS_JSON = "WIDGETS_JSON"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    /**
     * The gesture map. First call after upgrade migrates the old swipe left/right apps, the lock
     * switch and the clock/calendar apps; a fresh install (firstOpen still true) gets plain defaults.
     * MainActivity calls this before it clears firstOpen.
     */
    fun loadGestures(): GestureConfig {
        val stored = prefs.getString(GESTURES_JSON, null)
        if (stored != null) {
            val parsed = GestureConfig.parse(stored)
            if (parsed is GestureParse.Ok) return parsed.config
        }
        val config = GestureDefaults.migrate(if (firstOpen) null else legacyGestureState())
        saveGestures(config)
        return config
    }

    var widgetsJson: String?
        get() = prefs.getString(WIDGETS_JSON, null)
        set(value) = prefs.edit { putString(WIDGETS_JSON, value) }

    fun saveGestures(config: GestureConfig) = prefs.edit { putString(GESTURES_JSON, config.toJson()) }

    private fun legacyGestureState(): LegacyGestureState {
        fun swipe(pkg: String, activity: String?, user: String, isShortcut: Boolean, shortcutId: String): GestureAction? = when {
            pkg.isBlank() -> null
            isShortcut && shortcutId.isNotBlank() -> GestureAction.OpenShortcut(pkg, shortcutId, user)
            else -> GestureAction.OpenApp(pkg, activity?.ifBlank { null }, user)
        }
        fun app(pkg: String, cls: String?, user: String) =
            if (pkg.isBlank()) null else GestureAction.OpenApp(pkg, cls?.ifBlank { null }, user)
        return LegacyGestureState(
            swipeLeft = swipe(appPackageSwipeLeft, appActivityClassNameSwipeLeft, appUserSwipeLeft, isShortcutSwipeLeft, shortcutIdSwipeLeft),
            swipeLeftEnabled = swipeLeftEnabled,
            swipeRight = swipe(appPackageSwipeRight, appActivityClassNameRight, appUserSwipeRight, isShortcutSwipeRight, shortcutIdSwipeRight),
            swipeRightEnabled = swipeRightEnabled,
            lockModeOn = lockModeOn,
            clockApp = app(clockAppPackage, clockAppClassName, clockAppUser),
            calendarApp = app(calendarAppPackage, calendarAppClassName, calendarAppUser),
        )
    }

    /** Show today's screen time on home; separate from the usage-access permission it needs. */
    var showScreenTime: Boolean
        get() = prefs.getBoolean("SHOW_SCREEN_TIME", true)
        set(value) = prefs.edit { putBoolean("SHOW_SCREEN_TIME", value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    /** Launch the app straight away when a typed search narrows the drawer to exactly one match. */
    var autoLaunchSingle: Boolean
        get() = prefs.getBoolean(AUTO_LAUNCH_SINGLE, true)
        set(value) = prefs.edit { putBoolean(AUTO_LAUNCH_SINGLE, value).apply() }

    /** Web links shown in the drawer, stored as a JSON array, see [LinkEntry.toJson]. */
    var links: List<LinkEntry>
        get() = LinkEntry.listFromJson(prefs.getString(WEB_LINKS, null))
        set(value) = prefs.edit { putString(WEB_LINKS, LinkEntry.toJson(value)) }

    /** The user's website shortcuts plus Yoke's built-in ones (Cockpit board/calendar when Obsidian is installed). */
    fun allLinks(): List<LinkEntry> = LinkEntry.builtIns(context) + links

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var pendingYokeLevel: Boolean
        get() = prefs.getBoolean(PENDING_YOKE_LEVEL, false)
        set(value) = prefs.edit { putBoolean(PENDING_YOKE_LEVEL, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    var boldFont: Boolean
        get() = prefs.getBoolean(BOLD_FONT, false)
        set(value) = prefs.edit { putBoolean(BOLD_FONT, value).apply() }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    private fun isHomeSlot(location: Int) = location in 1..Constants.MAX_HOME_APPS

    fun getAppName(location: Int): String =
        if (isHomeSlot(location)) prefs.getString("APP_NAME_$location", "").toString() else ""

    fun getAppPackage(location: Int): String =
        if (isHomeSlot(location)) prefs.getString("APP_PACKAGE_$location", "").toString() else ""

    fun getAppActivityClassName(location: Int): String =
        if (isHomeSlot(location)) prefs.getString("APP_ACTIVITY_CLASS_NAME_$location", "").toString() else ""

    fun getAppUser(location: Int): String =
        if (isHomeSlot(location)) prefs.getString("APP_USER_$location", "").toString() else ""

    fun getShortcutId(location: Int): String =
        if (isHomeSlot(location)) prefs.getString("SHORTCUT_ID_$location", "").toString() else ""

    fun getIsShortcut(location: Int): Boolean =
        isHomeSlot(location) && prefs.getBoolean("IS_SHORTCUT_$location", false)

    fun setAppActivityClassName(location: Int, activityClassName: String) {
        if (isHomeSlot(location)) prefs.edit { putString("APP_ACTIVITY_CLASS_NAME_$location", activityClassName) }
    }

    fun setAppName(location: Int, name: String) {
        if (isHomeSlot(location)) prefs.edit { putString("APP_NAME_$location", name) }
    }

    /** Empties a home screen slot whose app or shortcut is gone. */
    fun clearHomeSlotApp(location: Int) {
        if (isHomeSlot(location)) prefs.edit { putString("APP_NAME_$location", ""); putString("APP_PACKAGE_$location", "") }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..Constants.MAX_HOME_APPS) {
            if (getAppPackage(i) == packageName) setAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    /**
     * Keeps the names pinned on the home screen and swipe slots in sync with a rename,
     * so the alias shows everywhere. Pinned shortcuts keep their own label.
     */
    fun applyRenameToPinnedApps(appPackage: String, label: String) {
        prefs.edit {
            for (i in 1..Constants.MAX_HOME_APPS) {
                if (getAppPackage(i) == appPackage && !getIsShortcut(i)) putString("APP_NAME_$i", label)
            }
            if (appPackageSwipeLeft == appPackage && !isShortcutSwipeLeft) putString(APP_NAME_SWIPE_LEFT, label)
            if (appPackageSwipeRight == appPackage && !isShortcutSwipeRight) putString(APP_NAME_SWIPE_RIGHT, label)
        }
    }

    /** Writes every field of a home screen slot (1..[Constants.MAX_HOME_APPS]) in one go. */
    fun setHomeSlot(
        location: Int,
        name: String,
        appPackage: String,
        user: String,
        activityClassName: String?,
        isShortcut: Boolean,
        shortcutId: String,
    ) {
        if (!isHomeSlot(location)) return
        prefs.edit {
            putString("APP_NAME_$location", name)
            putString("APP_PACKAGE_$location", appPackage)
            putString("APP_USER_$location", user)
            putString("APP_ACTIVITY_CLASS_NAME_$location", activityClassName)
            putBoolean("IS_SHORTCUT_$location", isShortcut)
            putString("SHORTCUT_ID_$location", shortcutId)
        }
    }
}
