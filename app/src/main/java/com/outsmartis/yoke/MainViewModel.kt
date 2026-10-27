package com.outsmartis.yoke

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.outsmartis.yoke.data.AppModel
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.LinkEntry
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.iconrow.IconRowPrefs
import com.outsmartis.yoke.iconrow.IconSlot
import com.outsmartis.yoke.iconrow.IconSlots
import com.outsmartis.yoke.gestures.Trigger
import com.outsmartis.yoke.helper.SingleLiveEvent
import com.outsmartis.yoke.helper.formattedTimeSpent
import com.outsmartis.yoke.helper.getAppsList
import com.outsmartis.yoke.helper.getPrivateSpaceApps
import com.outsmartis.yoke.helper.getPrivateSpaceUserHandle
import com.outsmartis.yoke.helper.hasBeenMinutes
import com.outsmartis.yoke.helper.isYokeDefault
import com.outsmartis.yoke.helper.isPackageInstalled
import com.outsmartis.yoke.helper.isPrivateSpaceLocked
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.launch
import java.util.Calendar


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    // Gesture being edited while the app list is open, and the app chosen to pick a shortcut from
    var pendingGestureTrigger: Trigger? = null
    val gestureShortcutApp = MutableLiveData<AppModel.App?>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isYokeDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()

    val privateSpaceApps = MutableLiveData<List<AppModel>?>()
    val privateSpaceLocked = MutableLiveData<Boolean>()
    val privateSpaceAvailable = MutableLiveData<Boolean>()

    // Suppress backToHomeScreen during Private Space lock/unlock auth
    var isPrivateSpaceToggling = false

    val showDialog = SingleLiveEvent<String>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()
    // Home button for recents feature disabled
    // val showRecentApps = SingleLiveEvent<Unit?>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        if (appModel is AppModel.PrivateSpaceHeader) return
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                when (appModel) {
                    is AppModel.PinnedShortcut -> launchShortcut(appModel)
                    is AppModel.App ->
                        launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)

                    is AppModel.Link -> openLink(appModel.entry.url)

                    else -> {}
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel is AppModel.App) {
                    launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
                }
            }

            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_10 ->
                saveHomeApp(appModel, flag - Constants.FLAG_SET_HOME_APP_1 + 1)

            in Constants.FLAG_SET_ICON_ROW_APP_1..Constants.FLAG_SET_ICON_ROW_APP_6 ->
                saveIconRowApp(appModel, flag - Constants.FLAG_SET_ICON_ROW_APP_1)

            Constants.FLAG_SET_GESTURE_APP -> saveGestureApp(appModel)
            Constants.FLAG_SET_GESTURE_SHORTCUT_APP -> if (appModel is AppModel.App) gestureShortcutApp.value = appModel
            Constants.FLAG_SET_CLOCK_APP -> saveClockApp(appModel)
            Constants.FLAG_SET_CALENDAR_APP -> saveCalendarApp(appModel)
            Constants.FLAG_SET_WEATHER_APP -> saveWeatherApp(appModel)
            Constants.FLAG_SET_SCREEN_TIME_APP -> saveScreenTimeApp(appModel)
        }
    }

    private fun launchShortcut(appModel: AppModel.PinnedShortcut) {
        if (com.outsmartis.yoke.limits.LimitGate.intercept(
                appContext, appModel.appPackage,
                com.outsmartis.yoke.limits.LimitGate.Target.Shortcut(appModel.shortcutId, appModel.user),
            )
        ) return
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(appModel.appPackage)
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        try {
            val shortcut = launcher.getShortcuts(query, appModel.user)
                ?.find { it.id == appModel.shortcutId }
            if (shortcut == null) {
                appContext.showToast(appContext.getString(R.string.shortcut_not_found))
                return
            }
            launcher.startShortcut(shortcut, null, null)
        } catch (_: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_shortcut))
        }
    }

    private fun saveHomeApp(appModel: AppModel, position: Int) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.PaletteResult -> return
            is AppModel.Link -> prefs.setHomeSlot(
                position,
                appModel.entry.name,
                appModel.entry.pinToken,
                appModel.user.toString(),
                activityClassName = null,
                isShortcut = false,
                shortcutId = "",
            )
            is AppModel.App -> prefs.setHomeSlot(
                position,
                appModel.appLabel,
                appModel.appPackage,
                appModel.user.toString(),
                appModel.activityClassName,
                isShortcut = false,
                shortcutId = "",
            )
            is AppModel.PinnedShortcut -> prefs.setHomeSlot(
                position,
                appModel.appLabel,
                appModel.appPackage,
                appModel.user.toString(),
                activityClassName = null,
                isShortcut = true,
                shortcutId = appModel.shortcutId,
            )
        }
        refreshHome(false)
    }

    private fun saveIconRowApp(appModel: AppModel, index: Int) {
        if (appModel !is AppModel.App) return
        val iconPrefs = IconRowPrefs(appContext)
        iconPrefs.slots = IconSlots.set(
            iconPrefs.slots, index,
            IconSlot(appModel.appPackage, appModel.activityClassName, appModel.user.toString()),
        )
    }

    private fun saveGestureApp(appModel: AppModel) {
        val trigger = pendingGestureTrigger ?: return
        val action = when (appModel) {
            is AppModel.App -> GestureAction.OpenApp(appModel.appPackage, appModel.activityClassName, appModel.user.toString())
            is AppModel.PinnedShortcut -> GestureAction.OpenShortcut(appModel.appPackage, appModel.shortcutId, appModel.user.toString())
            else -> return
        }
        prefs.saveGestures(prefs.loadGestures().with(trigger, action))
        pendingGestureTrigger = null
        updateSwipeApps()
    }

    private fun saveClockApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.clockAppPackage = appModel.appPackage
            prefs.clockAppUser = appModel.user.toString()
            prefs.clockAppClassName = appModel.activityClassName
            // Tap clock runs the gesture map now; keep long press on the clock choosing its app
            prefs.saveGestures(
                prefs.loadGestures().with(
                    Trigger.TAP_CLOCK,
                    GestureAction.OpenApp(appModel.appPackage, appModel.activityClassName, appModel.user.toString())
                )
            )
        }
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.calendarAppPackage = appModel.appPackage
            prefs.calendarAppUser = appModel.user.toString()
            prefs.calendarAppClassName = appModel.activityClassName
            prefs.saveGestures(
                prefs.loadGestures().with(
                    Trigger.TAP_DATE,
                    GestureAction.OpenApp(appModel.appPackage, appModel.activityClassName, appModel.user.toString())
                )
            )
        }
    }

    private fun saveWeatherApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            // Tap weather runs the gesture map; long press on the weather line chooses its app
            prefs.saveGestures(
                prefs.loadGestures().with(
                    Trigger.TAP_WEATHER,
                    GestureAction.OpenApp(appModel.appPackage, appModel.activityClassName, appModel.user.toString())
                )
            )
        }
    }

    private fun saveScreenTimeApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.screenTimeAppPackage = appModel.appPackage
            prefs.screenTimeAppUser = appModel.user.toString()
            prefs.screenTimeAppClassName = appModel.activityClassName
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.value = appCountUpdated
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    /** Opens a web link with the system's default handler. */
    fun openLink(url: String) {
        try {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        // A link pinned to a home slot or gesture is stored as a pseudo package, see LinkEntry.pinToken
        LinkEntry.idFromPinToken(packageName)?.let { id ->
            prefs.allLinks().find { it.id == id }?.let { openLink(it.url) }
                ?: appContext.showToast(appContext.getString(R.string.app_not_found))
            return
        }
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val isActivityValid = activityClassName.isNullOrBlank().not()
                && activityInfo.any { it.componentName.className == activityClassName }

        val component = if (isActivityValid)
            ComponentName(packageName, activityClassName)
        else {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }

                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }.also { prefs.updateAppActivityClassName(packageName, it.className) }
        }

        if (com.outsmartis.yoke.limits.LimitGate.intercept(
                appContext, packageName, com.outsmartis.yoke.limits.LimitGate.Target.App(component, userHandle)
            )
        ) return
        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch {
            val apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            appList.value = apps
        }
        getPrivateSpaceAppList()
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value =
                getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun isYokeDefault() {
        isYokeDefault.value = isYokeDefault(appContext)
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        val eventLogWrapper = EventLogWrapper(
            appContext
        )
        // Start of today in millis
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
            eventLogWrapper.aggregateForegroundStats(
                eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
            )
        )
        val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
        screenTimeValue.postValue(viewTimeSpent)
        prefs.screenTimeLastUpdated = endTime
    }

    fun getPrivateSpaceAppList() {
        viewModelScope.launch {
            val handle = getPrivateSpaceUserHandle(appContext)
            privateSpaceAvailable.value = handle != null
            if (handle != null) {
                privateSpaceLocked.value = isPrivateSpaceLocked(appContext, handle)
                privateSpaceApps.value = getPrivateSpaceApps(appContext, prefs)
            } else {
                privateSpaceLocked.value = true
                privateSpaceApps.value = emptyList()
            }
        }
    }

    fun openPrivateSpaceSettings() {
        try {
            val intent = Intent("android.settings.PRIVATE_SPACE_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun togglePrivateSpaceLock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val handle = getPrivateSpaceUserHandle(appContext) ?: return
        try {
            isPrivateSpaceToggling = true
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            val currentlyLocked = userManager.isQuietModeEnabled(handle)
            userManager.requestQuietModeEnabled(!currentlyLocked, handle)
        } catch (e: Exception) {
            isPrivateSpaceToggling = false
            e.printStackTrace()
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
