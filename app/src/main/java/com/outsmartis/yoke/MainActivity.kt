package com.outsmartis.yoke

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.ActivityMainBinding
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.helper.hasBeenHours
import com.outsmartis.yoke.helper.isDefaultLauncher
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.isEinkDisplay
import com.outsmartis.yoke.helper.isSystemAnimationsDisabled
import com.outsmartis.yoke.helper.isTablet
import com.outsmartis.yoke.helper.resetLauncherViaFakeActivity
import com.outsmartis.yoke.helper.showLauncherSelector
import com.outsmartis.yoke.helper.showMessageDialog
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var launcherAppsCallback: LauncherApps.Callback? = null
    private var messageDialog: YokeDialog? = null
    private var themeSignature: String? = null
    private var themeObserver: ThemeStore.Registration? = null

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, prefs.appTheme))
        themeSignature = ThemeStore.signature(this)
        super.onCreate(savedInstanceState)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
        if (isEinkDisplay() || isSystemAnimationsDisabled()) theme.applyStyle(R.style.NoAnimationOverlay, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeApplier.applyWindow(this)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back never leaves the home screen; elsewhere it pops the nav stack
                if (navController.currentDestination?.id != R.id.mainFragment)
                    navController.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        viewModel.getAppList()
        registerShortcutCallback()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            profileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    viewModel.isPrivateSpaceToggling = false
                    viewModel.getPrivateSpaceAppList()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            registerReceiver(profileReceiver, filter)
        }
    }

    override fun onStart() {
        super.onStart()
        // The picker or a "next theme" gesture may have changed the theme while we were stopped.
        if (ThemeStore.signature(this) != themeSignature) {
            recreate()
            return
        }
        themeObserver?.unregister()
        themeObserver = ThemeStore.observeConductore(this) { recreate() }
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false
        viewModel.getAppList()
    }

    private fun registerShortcutCallback() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        launcherAppsCallback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = Unit

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = Unit

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: android.os.UserHandle,
            ) {
                viewModel.getAppList()
            }
        }
        launcherApps.registerCallback(launcherAppsCallback!!)
    }

    override fun onStop() {
        themeObserver?.unregister()
        themeObserver = null
        isResumed = false
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        // Home button for recents feature disabled
        // val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        backToHomeScreen()
        // if (alreadyHome && isResumed && prefs.homeButtonShowRecents)
        //     viewModel.showRecentApps.call()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, prefs.appTheme))
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.HIDDEN -> {
                    showMessage(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay) {
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessage(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessage(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            }
        }
    }

    private fun showMessage(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        messageDialog?.dismiss()
        messageDialog = showMessageDialog(title, message, action, clickListener)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        messageDialog?.dismiss()
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            val nightMode = ThemeStore.nightMode(this@MainActivity, prefs.appTheme)
            if ((nightMode == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (nightMode == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

    override fun onDestroy() {
        messageDialog?.dismiss()
        messageDialog = null
        launcherAppsCallback?.let {
            getSystemService(LauncherApps::class.java).unregisterCallback(it)
        }
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }
}
