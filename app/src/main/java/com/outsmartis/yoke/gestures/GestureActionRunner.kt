package com.outsmartis.yoke.gestures

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.theme.ThemePickerActivity
import com.outsmartis.yoke.cockpit.QuickAddActivity
import com.outsmartis.yoke.data.AppModel
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.helper.MyAccessibilityService
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.expandNotificationDrawer
import com.outsmartis.yoke.helper.getUserHandleFromString
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.ui.showGestureCheatSheet

/**
 * The single place where gesture actions execute. Triggers (touch listeners, volume keys, home and
 * back on home) all end up in [run]. One instance lives in MainActivity; call [close] from onDestroy.
 */
class GestureActionRunner(private val activity: AppCompatActivity) {

    private val prefs = Prefs(activity)
    private val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
    private val navController: NavController get() = activity.findNavController(R.id.nav_host_fragment)

    private var torchOn = false
    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
        }
    }

    init {
        try {
            cameraManager?.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun config(): GestureConfig = prefs.loadGestures()

    fun isBound(trigger: Trigger): Boolean = config().isBound(trigger)

    /** Runs the action bound to [trigger]. Returns false when nothing is bound (nothing consumed). */
    fun run(trigger: Trigger): Boolean {
        val action = config()[trigger]
        if (action == GestureAction.None) return false
        run(action)
        return true
    }

    fun run(action: GestureAction) {
        try {
            when (action) {
                GestureAction.None -> {}
                is GestureAction.OpenApp -> openApp(action)
                is GestureAction.OpenShortcut -> openShortcut(action)
                is GestureAction.OpenUri -> openUri(action.uri)
                GestureAction.AppDrawer -> openDrawer(search = false)
                GestureAction.Search -> openDrawer(search = true)
                GestureAction.Notifications -> expandNotificationDrawer(activity)
                GestureAction.QuickSettings -> expandQuickSettings()
                GestureAction.Lock -> lockScreen()
                GestureAction.RecentApps -> recentApps()
                GestureAction.Torch -> toggleTorch()
                GestureAction.MediaPlayPause -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                GestureAction.MediaNext -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                GestureAction.MediaPrevious -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                GestureAction.CockpitQuickAdd -> activity.startActivity(Intent(activity, QuickAddActivity::class.java))
                GestureAction.ConductoreSheet -> conductoreSheet()
                GestureAction.CommandPalette -> commandPalette()
                GestureAction.ThemePicker -> themePicker()
                GestureAction.GestureCheatSheet -> showGestureCheatSheet(activity, config())
                GestureAction.Settings -> openSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Hooks for features built elsewhere. Wire them at merge time; until then they show a toast
    // (the command palette opens Settings so Settings stays reachable from long press).

    private fun conductoreSheet() = DetailsSheet.showConductore(activity)

    // TODO(merge): open the command palette here and delete the Settings fallback.
    private fun commandPalette() = openSettings()

    private fun themePicker() = ThemePickerActivity.open(activity)

    private fun comingSoon() = activity.showToast(activity.getString(R.string.coming_soon))

    private fun openSettings() {
        try {
            navController.navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openDrawer(search: Boolean) {
        viewModel.getAppList(false)
        val args = bundleOf(
            Constants.Key.FLAG to Constants.FLAG_LAUNCH_APP,
            Constants.Key.SEARCH to search,
        )
        try {
            navController.navigate(R.id.action_mainFragment_to_appListFragment, args)
        } catch (e: Exception) {
            navController.navigate(R.id.appListFragment, args)
        }
    }

    private fun openApp(action: GestureAction.OpenApp) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = action.packageName,
                key = null,
                appPackage = action.packageName,
                activityClassName = action.activityClassName,
                isNew = false,
                user = getUserHandleFromString(activity, action.user),
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun openShortcut(action: GestureAction.OpenShortcut) {
        val launcher = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            launcher.startShortcut(action.packageName, action.shortcutId, null, null, getUserHandleFromString(activity, action.user))
        } catch (e: Exception) {
            activity.showToast(activity.getString(R.string.unable_to_open_shortcut))
        }
    }

    private fun openUri(uri: String) {
        try {
            val intent = Intent.parseUri(uri, 0).apply {
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            activity.showToast(activity.getString(R.string.gesture_nothing_to_open))
        } catch (e: Exception) {
            activity.showToast(activity.getString(R.string.gesture_nothing_to_open))
        }
    }

    private fun expandQuickSettings() {
        try {
            val statusBarService = activity.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandSettingsPanel")
                .invoke(statusBarService)
        } catch (e: Exception) {
            e.printStackTrace()
            activity.showToast(activity.getString(R.string.gesture_quick_settings_unavailable))
        }
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val service = MyAccessibilityService.instance
            if (service != null) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            else lockNeedsSetup()
        } else {
            val manager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try {
                manager.lockNow()
            } catch (e: SecurityException) {
                lockNeedsSetup()
            } catch (e: Exception) {
                activity.showToast(activity.getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
            }
        }
    }

    private fun lockNeedsSetup() {
        activity.showToast(activity.getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
        openSettings()
    }

    private fun recentApps() {
        val service = MyAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            return
        }
        // Same prominent disclosure as the lock gesture before sending the user to accessibility settings
        activity.createDialog(
            title = R.string.gestures,
            action = R.string.enable,
            message = R.string.accessibility_disclosure,
            onAction = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        ).showRespectingStatusBar()
    }

    private fun toggleTorch() {
        val manager = cameraManager ?: return
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                activity.showToast(activity.getString(R.string.gesture_no_torch))
                return
            }
            manager.setTorchMode(id, !torchOn)
        } catch (e: CameraAccessException) {
            activity.showToast(activity.getString(R.string.gesture_no_torch))
        }
    }

    private fun mediaKey(code: Int) {
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
