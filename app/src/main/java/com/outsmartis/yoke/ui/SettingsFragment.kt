package com.outsmartis.yoke.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.outsmartis.yoke.BuildConfig
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.DialogTextSizeBinding
import com.outsmartis.yoke.databinding.FragmentSettingsBinding
import com.outsmartis.yoke.helper.appUsagePermissionGranted
import com.outsmartis.yoke.helper.asTextField
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemePickerActivity
import com.outsmartis.yoke.theme.ThemePrefs
import com.outsmartis.yoke.theme.ThemeStore
import com.outsmartis.yoke.theme.SystemPaletteSync
import com.outsmartis.yoke.helper.hideStatusBar
import com.outsmartis.yoke.helper.isAccessServiceEnabled
import com.outsmartis.yoke.helper.isTablet
import com.outsmartis.yoke.helper.openAppInfo
import com.outsmartis.yoke.helper.openUrl
import com.outsmartis.yoke.helper.LinkDialogs
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.showPopupMenu
import com.outsmartis.yoke.helper.showStatusBar
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.listener.DeviceAdmin

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var dialog: YokeDialog? = null
    private var weatherSettings: WeatherSettings? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        weatherSettings?.onPermissionResult(granted)
    }

    // YOK-032: READ_CALENDAR is asked only when the glance card's events are switched to "Calendar"
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (_binding == null) return@registerForActivityResult
        if (granted) {
            setGlanceSource(com.outsmartis.yoke.glance.GlanceSource.CALENDAR)
        } else {
            requireContext().showToast(getString(R.string.glance_calendar_denied))
            populateGlance()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isYokeDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateAutoLaunchSingle()
        populateScreenTimeOnOff()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateAppThemeText()
        populateWallpaper()
        populateTextSize()
        populateBoldFont()
        populateFont()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        weatherSettings = WeatherSettings(
            this, binding.weatherToggle, binding.weatherLocation, binding.weatherUnits, binding.weatherHighLow, locationPermission,
        )
        populateCockpitAgenda()
        binding.cockpitAgendaToggle.setOnClickListener {
            val cockpit = com.outsmartis.yoke.cockpit.CockpitPrefs(requireContext())
            cockpit.agendaEnabled = !cockpit.agendaEnabled
            if (cockpit.agendaEnabled) {
                cockpit.agendaAt = 0L
                if (cockpit.vaultUri == null) requireContext().showToast(getString(R.string.cockpit_agenda_needs_vault))
            }
            populateCockpitAgenda()
        }
        initGlance() // YOK-032
        initClickListeners()
        initObservers()
        SettingsOrganizer(requireContext(), listOf(
            SettingsOrganizer.Section("home", binding.sectionHome, binding.sectionHomeTitle, binding.sectionHomeRows),
            SettingsOrganizer.Section("look", binding.sectionLook, binding.sectionLookTitle, binding.sectionLookRows),
            SettingsOrganizer.Section("behaviour", binding.sectionBehaviour, binding.sectionBehaviourTitle, binding.sectionBehaviourRows),
            SettingsOrganizer.Section("focus", binding.sectionFocus, binding.sectionFocusTitle, binding.sectionFocusRows),
            SettingsOrganizer.Section("connections", binding.sectionConnections, binding.sectionConnectionsTitle, binding.sectionConnectionsRows),
            SettingsOrganizer.Section("data", binding.sectionData, binding.sectionDataTitle, binding.sectionDataRows),
        ), binding.settingsSearch.asTextField()).install()
    }

    private val pickVault = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        com.outsmartis.yoke.cockpit.CockpitPrefs(ctx).apply { vaultUri = uri.toString(); agendaAt = 0L }
        if (_binding != null) populateConnections()
    }

    /** The values shown at the end of rows that open their own screen. */
    private fun populateSummaries() {
        val ctx = requireContext()
        val gestures = prefs.loadGestures().bound().size
        binding.gesturesRow.text = getString(R.string.settings_count_set, gestures)
        val icons = com.outsmartis.yoke.iconrow.IconRowPrefs(ctx)
        binding.iconRowRow.text = if (icons.enabled) getString(R.string.settings_count_apps, icons.slots.size) else getString(R.string.off)
        val search = com.outsmartis.yoke.palette.SearchPrefs(ctx)
        val sources = listOf(search.cards, search.notes, search.contacts, search.settings).count { it }
        binding.searchRow.text = getString(R.string.settings_count_on, sources, 4)
        binding.webLinks.text = prefs.links.size.toString()
        com.outsmartis.yoke.assist.Assistant.reconcile(ctx)
        binding.assistantRow.text = AssistantDialog.summary(ctx)
        val grayscale = com.outsmartis.yoke.grayscale.GrayscalePrefs(ctx)
        val exceptions = (grayscale.exceptions - com.outsmartis.yoke.grayscale.GrayscalePrefs.DEFAULT_EXCEPTIONS).size
        binding.grayscaleRow.text = if (grayscale.featureOn) getString(R.string.settings_on_exceptions, exceptions) else getString(R.string.off)
        val notes = com.outsmartis.yoke.notifications.NotificationPrefs(ctx)
        binding.notesRow.text = if (notes.enabled) getString(R.string.settings_on_apps, notes.allowed.size) else getString(R.string.off)
        val limited = com.outsmartis.yoke.limits.LimitPrefs(ctx).limited.size
        binding.limitsRow.text = if (limited == 0) getString(R.string.off) else getString(R.string.limits_count, limited)
        binding.backupRow.text = getString(
            if (com.outsmartis.yoke.backup.VaultBackup.keepCopy(ctx)) R.string.settings_backup_auto else R.string.settings_backup_manual
        )
    }

    /** Vault, Conductore and the secure-settings permission: whether each is set up. */
    private fun populateConnections() {
        val ctx = requireContext().applicationContext
        binding.secureRow.setText(
            if (com.outsmartis.yoke.grayscale.GrayscaleController.hasPermission(ctx)) R.string.settings_status_granted
            else R.string.settings_status_missing
        )
        binding.accessibilityRow.setText(when {
            com.outsmartis.yoke.helper.MyAccessibilityService.instance != null -> R.string.settings_status_running
            isAccessServiceEnabled(ctx) -> R.string.settings_status_not_running
            else -> R.string.off
        })
        binding.shizukuRow.setText(ShizukuDialog.statusText(ctx))
        binding.vaultRow.setText(R.string.settings_status_checking)
        binding.conductoreRow.setText(R.string.settings_status_checking)
        Thread {
            val vault = runCatching { com.outsmartis.yoke.cockpit.CockpitPrefs(ctx).vault(ctx)?.displayName() }.getOrNull()
            val conductore = conductoreStatus(ctx)
            binding.root.post {
                if (_binding == null) return@post
                if (vault != null) binding.vaultRow.text = vault else binding.vaultRow.setText(R.string.settings_status_not_linked)
                binding.conductoreRow.setText(conductore)
            }
        }.start()
    }

    private fun conductoreStatus(ctx: Context): Int {
        val pkg = com.outsmartis.yoke.details.DetailsSheet.CONDUCTORE_PACKAGE
        val installed = runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        if (!installed) return R.string.settings_status_not_installed
        val client = com.outsmartis.yoke.details.AppDetailsClient(ctx)
        val authority = client.authorityOf(pkg) ?: return R.string.settings_status_update_needed
        return when (val r = client.load(authority)) {
            is com.outsmartis.yoke.details.AppDetailsClient.Result.Ok ->
                if ((r.details.summary?.contractVersion ?: 1) >= 2) R.string.settings_status_connected else R.string.settings_status_update_needed
            else -> R.string.settings_status_no_access
        }
    }

    private fun openConductore() {
        val launch = requireContext().packageManager.getLaunchIntentForPackage(com.outsmartis.yoke.details.DetailsSheet.CONDUCTORE_PACKAGE)
        if (launch != null) startActivity(launch) else requireContext().showToast(getString(R.string.settings_status_not_installed))
    }

    // YOK-032: the glance card rows
    private fun initGlance() {
        populateGlance()
        binding.glanceToggle.setOnClickListener {
            val glance = com.outsmartis.yoke.glance.GlancePrefs(requireContext())
            glance.enabled = !glance.enabled
            populateGlance()
        }
        binding.glanceSource.setOnClickListener { chooseGlanceSource() }
        binding.glanceWeatherToggle.setOnClickListener {
            val glance = com.outsmartis.yoke.glance.GlancePrefs(requireContext())
            glance.showWeather = !glance.showWeather
            populateGlance()
        }
    }

    /** One list with every source, so a denied calendar never blocks Cockpit; picking Calendar also re-asks when access is missing. */
    private fun chooseGlanceSource() {
        val sources = com.outsmartis.yoke.glance.GlanceSource.entries
        val labels = arrayOf(R.string.glance_source_none, R.string.glance_source_calendar, R.string.glance_source_cockpit)
            .map { getString(it) }.toTypedArray()
        val current = com.outsmartis.yoke.glance.GlancePrefs(requireContext()).source
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.glance_source_setting)
            .setSingleChoiceItems(labels, sources.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                val picked = sources[which]
                when {
                    picked == com.outsmartis.yoke.glance.GlanceSource.CALENDAR &&
                        !com.outsmartis.yoke.glance.GlanceRepository.hasCalendarPermission(requireContext()) ->
                        calendarPermission.launch(com.outsmartis.yoke.glance.GlanceRepository.PERMISSION)
                    picked == com.outsmartis.yoke.glance.GlanceSource.COCKPIT &&
                        com.outsmartis.yoke.cockpit.CockpitPrefs(requireContext()).vaultUri == null -> {
                        setGlanceSource(picked)
                        requireContext().showToast(getString(R.string.cockpit_agenda_needs_vault))
                    }
                    else -> setGlanceSource(picked)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setGlanceSource(source: com.outsmartis.yoke.glance.GlanceSource) {
        com.outsmartis.yoke.glance.GlancePrefs(requireContext()).source = source
        com.outsmartis.yoke.glance.GlanceRepository.clear()
        populateGlance()
    }

    private fun populateGlance() {
        val glance = com.outsmartis.yoke.glance.GlancePrefs(requireContext())
        binding.glanceToggle.text = getString(if (glance.enabled) R.string.on else R.string.off)
        binding.glanceSource.setText(when (glance.source) {
            com.outsmartis.yoke.glance.GlanceSource.NONE -> R.string.glance_source_none
            com.outsmartis.yoke.glance.GlanceSource.CALENDAR ->
                if (com.outsmartis.yoke.glance.GlanceRepository.hasCalendarPermission(requireContext())) R.string.glance_source_calendar
                else R.string.glance_source_calendar_no_access
            com.outsmartis.yoke.glance.GlanceSource.COCKPIT -> R.string.glance_source_cockpit
        })
        binding.glanceWeatherToggle.text = getString(if (glance.showWeather) R.string.on else R.string.off)
    }

    private fun populateCockpitAgenda() {
        val cockpit = com.outsmartis.yoke.cockpit.CockpitPrefs(requireContext())
        binding.cockpitAgendaToggle.text = getString(if (cockpit.agendaEnabled) R.string.on else R.string.off)
        val language = DICTATION_LANGUAGES.firstOrNull { it.first == cockpit.dictationLanguage } ?: DICTATION_LANGUAGES[0]
        binding.dictationLanguage.setText(language.second)
    }

    /** Phone → Português → English → Phone. */
    private fun cycleDictationLanguage() {
        val cockpit = com.outsmartis.yoke.cockpit.CockpitPrefs(requireContext())
        val i = DICTATION_LANGUAGES.indexOfFirst { it.first == cockpit.dictationLanguage }
        cockpit.dictationLanguage = DICTATION_LANGUAGES[(i + 1) % DICTATION_LANGUAGES.size].first
        populateCockpitAgenda()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.yokeHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> toggleScreenTime()
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.autoLaunchSingle -> toggleAutoLaunchSingle()
            R.id.webLinks -> LinkDialogs.showList(requireContext(), prefs)
            R.id.homeAppsNum -> showHomeAppsNumMenu(view)
            R.id.alignment -> showAlignmentMenu(view)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> showDateTimeMenu(view)
            R.id.appThemeText -> ThemePickerActivity.open(requireContext())
            R.id.wallpaperRow -> com.outsmartis.yoke.wallpaper.WallpaperActivity.open(requireContext())
            R.id.systemColoursRow -> showSystemColoursDialog()
            R.id.textSizeValue -> showTextSizeDialog()
            R.id.boldFont -> toggleBoldFont()
            R.id.fontText -> toggleFont()

            R.id.iconRowRow -> findNavController().navigate(R.id.action_settingsFragment_to_iconRowFragment)
            R.id.gesturesRow -> findNavController().navigate(R.id.action_settingsFragment_to_gesturesFragment)
            R.id.grayscaleRow -> findNavController().navigate(R.id.action_settingsFragment_to_grayscaleFragment)
            R.id.notesRow -> findNavController().navigate(R.id.action_settingsFragment_to_notificationsHomeFragment)
            R.id.searchRow -> findNavController().navigate(R.id.action_settingsFragment_to_searchSettingsFragment)
            R.id.backupRow -> findNavController().navigate(R.id.action_settingsFragment_to_backupFragment)
            R.id.nowPlayingRow -> toggleNowPlaying()
            R.id.assistantRow -> {
                dialog?.dismiss()
                dialog = AssistantDialog.show(requireContext()) { if (_binding != null) populateSummaries() }
            }
            R.id.dictationLanguage -> cycleDictationLanguage()
            R.id.aboutYoke -> requireContext().openUrl(Constants.URL_YOKE_GITHUB)
            R.id.vaultRow -> pickVault.launch(null)
            R.id.conductoreRow -> openConductore()
            R.id.secureRow -> findNavController().navigate(R.id.action_settingsFragment_to_grayscaleFragment)
            R.id.accessibilityRow -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            R.id.shizukuRow -> { dialog?.dismiss(); dialog = ShizukuDialog.show(requireContext()) { if (_binding != null) populateConnections() } }
            R.id.limitsRow -> { dialog?.dismiss(); dialog = LimitedAppsDialog.show(requireContext()) { if (_binding != null) populateSummaries() } }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.appThemeText -> showAppThemeMenu(view, showSystem = true)
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.yokeHiddenApps.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutYoke.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.autoLaunchSingle.setOnClickListener(this)
        binding.webLinks.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.gesturesRow.setOnClickListener(this)
        binding.iconRowRow.setOnClickListener(this)
        binding.grayscaleRow.setOnClickListener(this)
        binding.nowPlayingRow.setOnClickListener(this)
        binding.assistantRow.setOnClickListener(this)
        binding.dictationLanguage.setOnClickListener(this)
        populateNowPlaying()
        binding.notesRow.setOnClickListener(this)
        binding.searchRow.setOnClickListener(this)
        binding.backupRow.setOnClickListener(this)
        binding.vaultRow.setOnClickListener(this)
        binding.conductoreRow.setOnClickListener(this)
        binding.secureRow.setOnClickListener(this)
        binding.accessibilityRow.setOnClickListener(this)
        binding.shizukuRow.setOnClickListener(this)
        binding.limitsRow.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.wallpaperRow.setOnClickListener(this)
        binding.systemColoursRow.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.fontText.setOnClickListener(this)

        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) prefs.firstSettingsOpen = false
        viewModel.isYokeDefault.observe(viewLifecycleOwner) {
            if (it) binding.setLauncher.text = getString(R.string.change_default_launcher)
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
    }

    // Popup menus

    private fun showHomeAppsNumMenu(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
            }
        ) { item -> updateHomeAppsNum(item.itemId) }
    }

    private fun showDateTimeMenu(anchor: View) {
        anchor.showPopupMenu(R.menu.date_time) { item ->
            when (item.itemId) {
                R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
                R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
                R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            }
        }
    }

    private fun showAlignmentMenu(anchor: View) {
        anchor.showPopupMenu(
            R.menu.alignment,
            configure = { menu ->
                menu.findItem(R.id.alignmentBottom).setTitle(
                    if (prefs.homeBottomAlignment) R.string.bottom_on else R.string.bottom_off
                )
            }
        ) { item ->
            when (item.itemId) {
                R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
                R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
                R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
                R.id.alignmentBottom -> updateHomeBottomAlignment()
            }
        }
    }

    // "System" stays hidden unless the row is long pressed
    private fun showAppThemeMenu(anchor: View, showSystem: Boolean) {
        anchor.showPopupMenu(
            R.menu.app_theme,
            configure = { menu -> menu.findItem(R.id.themeSystem).isVisible = showSystem }
        ) { item ->
            when (item.itemId) {
                R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
                R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
                R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    // Dialogs

    private fun showDialog(newDialog: YokeDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private fun showTextSizeDialog() {
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(R.string.text_size, R.string.okay) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeCurrent.text = formatScale(pendingOrCurrentTextSizeScale())
            textSizeMinus.setOnClickListener { adjustTextSizePreview(-0.1f, this) }
            textSizePlus.setOnClickListener { adjustTextSizePreview(0.1f, this) }
        }
        dialog.setOnDismissListener { applyTextSizeScale() }
        showDialog(dialog)
    }

    // Prominent disclosure before sending the user to accessibility settings
    private fun showAccessibilityDialog() {
        val serviceEnabled = isAccessServiceEnabled(requireContext())
        showDialog(
            requireContext().createDialog(
                title = R.string.gestures,
                action = if (serviceEnabled) R.string.disable else R.string.enable,
                message = R.string.accessibility_disclosure,
                onAction = { openAccessibilityService() },
            )
        )
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            requireActivity().window.showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            requireActivity().window.hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun openAccessibilityService() {
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
                showAccessibilityDialog()
                return
            }
            prefs.lockModeOn = !prefs.lockModeOn
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun pendingOrCurrentTextSizeScale(): Float =
        if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale

    private fun formatScale(scale: Float): String = String.format("%.1f", scale)

    private fun adjustTextSizePreview(delta: Float, dialogBinding: DialogTextSizeBinding) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = pendingOrCurrentTextSizeScale()
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = formatScale(clamped)
        binding.textSizeValue.text = formatted
        dialogBinding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        val activity = activity ?: return
        if (activity.isChangingConfigurations.not())
            activity.recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        requireActivity().recreate()
    }

    private fun populateWallpaper() {
        binding.wallpaperRow.text = com.outsmartis.yoke.wallpaper.WallpaperActivity.summary(requireContext())
        populateSystemColours()
    }

    private fun populateSystemColours() {
        binding.systemColoursRow.setText(when (SystemPaletteSync.status(requireContext())) {
            SystemPaletteSync.Status.On -> R.string.on
            SystemPaletteSync.Status.Off -> R.string.off
            SystemPaletteSync.Status.NeedsPermission -> R.string.system_colours_needs_permission
            SystemPaletteSync.Status.Unsupported -> R.string.system_colours_unsupported
        })
    }

    /** Explains the setting; without the permission the action leads to the grant screen. */
    private fun showSystemColoursDialog() {
        val ctx = requireContext()
        val status = SystemPaletteSync.status(ctx)
        if (status == SystemPaletteSync.Status.Unsupported) return
        val hasPermission = com.outsmartis.yoke.grayscale.GrayscaleController.hasPermission(ctx)
        val on = ThemePrefs(ctx).systemColours
        val action = when {
            on -> R.string.system_colours_turn_off
            hasPermission -> R.string.system_colours_turn_on
            else -> R.string.system_colours_set_up
        }
        showDialog(
            ctx.createDialog(
                title = R.string.system_colours_title,
                action = action,
                message = R.string.system_colours_message,
                onAction = {
                    when {
                        on -> SystemPaletteSync.setEnabled(ctx, false)
                        hasPermission -> SystemPaletteSync.setEnabled(ctx, true)
                        else -> {
                            // Remember the choice; it starts working once the permission is granted.
                            ThemePrefs(ctx).systemColours = true
                            findNavController().navigate(R.id.action_settingsFragment_to_grayscaleFragment)
                        }
                    }
                    if (_binding != null) populateSystemColours()
                },
            )
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            // Catches a permission granted since (Smart grayscale's screen, or adb).
            SystemPaletteSync.sync(requireContext())
            populateWallpaper()
            populateSummaries()
            populateConnections()
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        val active = ThemeStore.current(requireContext())
        if (active != null) {
            binding.appThemeText.text = active.name
            return
        }
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = formatScale(prefs.textSizeScale)
    }

    private fun toggleBoldFont() {
        prefs.boldFont = !prefs.boldFont
        populateBoldFont()
        requireActivity().recreate()
    }

    private fun toggleFont() {
        val themePrefs = ThemePrefs(requireContext())
        themePrefs.jetBrainsMono = !themePrefs.jetBrainsMono
        populateFont()
        requireActivity().recreate()
    }

    private fun populateFont() {
        binding.fontText.text = getString(
            if (ThemePrefs(requireContext()).jetBrainsMono) R.string.font_jetbrains_mono else R.string.font_system
        )
    }

    private fun populateBoldFont() {
        binding.boldFont.text = getString(if (prefs.boldFont) R.string.on else R.string.off)
    }

    private fun populateNowPlaying() {
        binding.nowPlayingRow.setText(
            if (com.outsmartis.yoke.notifications.NotificationPrefs(requireContext()).nowPlaying) R.string.on else R.string.off
        )
    }

    // Now playing reads media sessions through Yoke's notification listener, so it needs Notification access.
    private fun toggleNowPlaying() {
        val ctx = requireContext()
        val notes = com.outsmartis.yoke.notifications.NotificationPrefs(ctx)
        notes.nowPlaying = !notes.nowPlaying
        populateNowPlaying()
        com.outsmartis.yoke.notifications.NotificationStore.refresh()
        val granted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (notes.nowPlaying && !granted) {
            ctx.showToast(getString(R.string.now_playing_needs_access))
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val on = requireContext().appUsagePermissionGranted() && prefs.showScreenTime
            binding.screenTimeOnOff.text = getString(if (on) R.string.on else R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    // Off just hides it; usage access is only asked for when turning it on without it.
    private fun toggleScreenTime() {
        val granted = requireContext().appUsagePermissionGranted()
        if (granted && prefs.showScreenTime) prefs.showScreenTime = false
        else {
            prefs.showScreenTime = true
            if (!granted) viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
        }
        populateScreenTimeOnOff()
    }

    private fun toggleAutoLaunchSingle() {
        prefs.autoLaunchSingle = !prefs.autoLaunchSingle
        populateAutoLaunchSingle()
    }

    private fun populateAutoLaunchSingle() {
        binding.autoLaunchSingle.text =
            getString(if (prefs.autoLaunchSingle) R.string.on else R.string.off)
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isYokeDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_yoke_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
    }

    // Home button for recents feature disabled
    // private fun toggleHomeButtonRecents() {
    //     if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
    //         showAccessibilityDialog()
    //         return
    //     }
    //     prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
    //     populateHomeButtonRecents()
    // }

    // private fun populateHomeButtonRecents() {
    //     binding.homeButtonRecents.text = getString(
    //         if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
    //         else R.string.off
    //     )
    // }

    private fun populateLockSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
                else R.string.off
            )
        } else {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn) R.string.on
                else R.string.off
            )
        }
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    override fun onDestroyView() {
        // Dismissing the text size dialog applies any pending scale via its dismiss listener
        dialog?.dismiss()
        dialog = null
        applyTextSizeScale()
        weatherSettings = null
        super.onDestroyView()
        _binding = null
    }
}

private val DICTATION_LANGUAGES = listOf(
    "" to R.string.dictation_language_phone,
    "pt-PT" to R.string.dictation_language_pt,
    "en-US" to R.string.dictation_language_en,
)
