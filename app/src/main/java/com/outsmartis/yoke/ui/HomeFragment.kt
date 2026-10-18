package com.outsmartis.yoke.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.outsmartis.yoke.MainActivity
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.AppModel
import com.outsmartis.yoke.iconrow.IconPosition
import com.outsmartis.yoke.iconrow.IconRenderer
import com.outsmartis.yoke.iconrow.HomeAppPlan
import com.outsmartis.yoke.iconrow.HomeAppStyle
import com.outsmartis.yoke.iconrow.HomeIconSource
import com.outsmartis.yoke.iconrow.IconRowPrefs
import com.outsmartis.yoke.iconrow.IconRowView
import com.outsmartis.yoke.iconrow.IconSlot
import com.outsmartis.yoke.iconrow.IconSlots
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.details.SheetAction
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.LinkEntry
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.FragmentHomeBinding
import com.outsmartis.yoke.helper.hideStatusBar
import com.outsmartis.yoke.helper.showStatusBar
import com.outsmartis.yoke.helper.appUsagePermissionGranted
import com.outsmartis.yoke.helper.dpToPx
import com.outsmartis.yoke.helper.getUserHandleFromString
import com.outsmartis.yoke.helper.isPackageInstalled
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.gestures.GestureGeometry
import com.outsmartis.yoke.gestures.Trigger
import com.outsmartis.yoke.listener.OnSwipeTouchListener
import com.outsmartis.yoke.listener.ViewSwipeTouchListener
import com.outsmartis.yoke.cockpit.CockpitAgendaRepository
import com.outsmartis.yoke.cockpit.CockpitLinks
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.cockpit.CockpitTodaySheet
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.notifications.HomeNotifications
import com.outsmartis.yoke.notifications.NotificationLogic
import com.outsmartis.yoke.notifications.NotificationStore
import com.outsmartis.yoke.theme.ThemeStore
import com.outsmartis.yoke.weather.WeatherFormat
import com.outsmartis.yoke.weather.WeatherPrefs
import com.outsmartis.yoke.weather.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var mainTouchListener: OnSwipeTouchListener? = null
    private var notes: HomeNotifications? = null
    private var lastIconCounts: Map<String, Int> = emptyMap()
    private lateinit var iconPrefs: IconRowPrefs
    private var iconRow: IconRowView? = null
    private var appsBasePaddingBottom = 0
    /** Bottom space the first-run tips need, so the home apps never run into them. */
    private var tipsReserve = 0
    private val iconPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (_binding != null && isAdded) {
            // YOK-031: the home app style lives in the same prefs; only its keys redraw the home apps
            if (HomeAppPlan.affectsHomeApps(key, iconPrefs.homeAppStyle)) populateHomeScreen(false) else renderIconRow()
        }
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        iconPrefs = IconRowPrefs(requireContext())
        appsBasePaddingBottom = binding.homeAppsLayout.paddingBottom
        iconPrefs.registerListener(iconPrefsListener)
        keepAppsBelowTopBlock()
        keepAppsAboveTips()
        settleFirstLayout()
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        notes = HomeNotifications(requireContext(), binding.homeNotifications, binding.homeNowPlaying).also { it.bindNowPlaying() }
        NotificationStore.entries.observe(viewLifecycleOwner) {
            renderNotifications()
            // Re-render the icons only when the per-app counts changed (progress updates repost often)
            val counts = notes?.counts().orEmpty()
            if (counts != lastIconCounts) renderHomeIcons()
        }
        NotificationStore.nowPlaying.observe(viewLifecycleOwner) { notes?.render() }
        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        NotificationStore.refresh()
        populateHomeScreen(false)
        // The first fetches on a fresh home decide when the held layout transition is released.
        val hold = layoutHold
        layoutHold = null
        var started = 0
        if (WeatherRepository.refreshIfNeeded(requireContext()) {
                if (_binding != null) populateWeather()
                hold?.oneDone()
            }) started++
        if (CockpitAgendaRepository.refreshIfNeeded(requireContext()) {
                if (_binding != null) populateAgenda()
                hold?.oneDone()
            }) started++
        hold?.expect(started)
        // Gestures may have been edited; only wait for a second tap when double tap is bound
        mainTouchListener?.setDoubleTapEnabled(gestureRunner().isBound(Trigger.DOUBLE_TAP))
        viewModel.isYokeDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock -> gestureRunner().run(Trigger.TAP_CLOCK)
            R.id.date -> gestureRunner().run(Trigger.TAP_DATE)
            R.id.weather -> gestureRunner().run(Trigger.TAP_WEATHER)
            R.id.agenda -> CockpitTodaySheet.show(requireContext()) { if (_binding != null) populateAgenda() }
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1 -> homeAppLongPressed(1, Constants.FLAG_SET_HOME_APP_1)
            R.id.homeApp2 -> homeAppLongPressed(2, Constants.FLAG_SET_HOME_APP_2)
            R.id.homeApp3 -> homeAppLongPressed(3, Constants.FLAG_SET_HOME_APP_3)
            R.id.homeApp4 -> homeAppLongPressed(4, Constants.FLAG_SET_HOME_APP_4)
            R.id.homeApp5 -> homeAppLongPressed(5, Constants.FLAG_SET_HOME_APP_5)
            R.id.homeApp6 -> homeAppLongPressed(6, Constants.FLAG_SET_HOME_APP_6)
            R.id.homeApp7 -> homeAppLongPressed(7, Constants.FLAG_SET_HOME_APP_7)
            R.id.homeApp8 -> homeAppLongPressed(8, Constants.FLAG_SET_HOME_APP_8)
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.weather -> showAppList(Constants.FLAG_SET_WEATHER_APP)
            R.id.agenda -> CockpitLinks.openBoard(requireContext())

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isYokeDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isYokeDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        mainTouchListener = getSwipeGestureListener(context)
        binding.mainLayout.setOnTouchListener(mainTouchListener)
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.weather.setOnClickListener(this)
        binding.weather.setOnLongClickListener(this)
        binding.agenda.setOnClickListener(this)
        binding.agenda.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        binding.homeApp1.setOnClickListener(this)
        binding.homeApp2.setOnClickListener(this)
        binding.homeApp3.setOnClickListener(this)
        binding.homeApp4.setOnClickListener(this)
        binding.homeApp5.setOnClickListener(this)
        binding.homeApp6.setOnClickListener(this)
        binding.homeApp7.setOnClickListener(this)
        binding.homeApp8.setOnClickListener(this)
        binding.homeApp1.setOnLongClickListener(this)
        binding.homeApp2.setOnLongClickListener(this)
        binding.homeApp3.setOnLongClickListener(this)
        binding.homeApp4.setOnLongClickListener(this)
        binding.homeApp5.setOnLongClickListener(this)
        binding.homeApp6.setOnLongClickListener(this)
        binding.homeApp7.setOnLongClickListener(this)
        binding.homeApp8.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
        // Icon only padding depends on the alignment edge
        if (iconPrefs.homeAppStyle == HomeAppStyle.ICON) renderHomeIcons()
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
        populateWeather()
        populateAgenda()
    }

    /** One line under the date: nothing when off, no data yet, stale, or the date is hidden. */
    private fun populateWeather() {
        val weatherPrefs = WeatherPrefs(requireContext())
        val text = if (weatherPrefs.enabled && Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)) {
            WeatherFormat.line(weatherPrefs.reading, weatherPrefs.units, weatherPrefs.showHighLow, System.currentTimeMillis())
        } else ""
        binding.weather.text = text
        binding.weather.isVisible = text.isNotEmpty()
    }

    /** The cached Cockpit agenda line under the weather: nothing when off, empty, or the date is hidden. */
    private fun populateAgenda() {
        val cockpit = CockpitPrefs(requireContext())
        val text = if (cockpit.agendaEnabled && Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)) cockpit.agendaLine else ""
        binding.agenda.text = text
        binding.agenda.isVisible = text.isNotEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not() || !prefs.showScreenTime) {
            binding.tvScreenTime.visibility = View.GONE
            return
        }

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        populateHomeScreenCore(appCountUpdated)
        renderNotifications()
        renderHomeIcons()
        renderIconRow()
    }

    // YOK-031
    /**
     * Applies the home app style after the labels are set: a round icon (tinted with the theme text colour,
     * or the original one when the icon row uses original colours) before the name, or instead of it.
     * Icon only blanks the label, so it keeps a contentDescription (name, profile, notifications), an accent
     * dot for pending notifications and a minimum touch width. Pinned shortcuts use their own icon, pinned web
     * links and apps whose icon cannot be loaded get a letter tile; work profile and Private Space apps keep
     * their badge in every style. Tap, long press and swipes stay on the TextView.
     */
    private fun renderHomeIcons() {
        val ctx = requireContext()
        val style = iconPrefs.homeAppStyle
        val color = ThemeApplier.textColor(ctx, secondary = false)
        val original = iconPrefs.style == com.outsmartis.yoke.iconrow.IconStyle.ORIGINAL
        val counts = notes?.counts().orEmpty()
        lastIconCounts = counts
        val accent = ThemeStore.current(ctx)?.accentText ?: ctx.getColorFromAttr(R.attr.primaryColor)
        val views = listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
        )
        for ((i, tv) in views.withIndex()) {
            val slot = i + 1
            val pkg = prefs.getAppPackage(slot)
            val name = prefs.getAppName(slot)
            if (!style.showsIcon || pkg.isEmpty() || tv.visibility != View.VISIBLE) {
                tv.setCompoundDrawablesRelative(null, null, null, null)
                tv.compoundDrawablePadding = 0
                tv.contentDescription = null
                tv.minWidth = 0
                tv.setPaddingRelative(0, tv.paddingTop, 0, tv.paddingBottom)
                tv.hint = getString(R.string.app)
                continue
            }
            val sizePx = (tv.textSize * 1.25f).toInt().coerceAtLeast(16.dpToPx())
            val userString = prefs.getAppUser(slot)
            val handle = getUserHandleFromString(ctx, userString)
            val count = counts[pkg] ?: 0
            val plan = HomeAppPlan.plan(
                style = style,
                name = name,
                isPin = LinkEntry.idFromPinToken(pkg) != null,
                isShortcut = prefs.getIsShortcut(slot) && prefs.getShortcutId(slot).isNotEmpty(),
                otherProfile = handle != android.os.Process.myUserHandle(),
                profileLabel = getString(R.string.home_app_profile),
                notifications = count,
                notificationsLabel = resources.getQuantityString(R.plurals.home_app_notifications, count, count),
            )
            var base: Bitmap? = when (plan.icon) {
                HomeIconSource.APP -> IconRenderer.render(ctx, IconSlot(pkg, null, userString), iconPrefs.style, sizePx, color)
                HomeIconSource.SHORTCUT -> shortcutIcon(ctx, pkg, prefs.getShortcutId(slot), handle)?.let { drawableToBitmap(it, sizePx) }
                else -> null
            }
            // Letter tiles are already framed; real icons are cropped round unless they are tinted monochrome
            val round = base != null && (original || plan.icon == HomeIconSource.SHORTCUT)
            if (base == null) base = IconRenderer.letterTile(name.firstOrNull() ?: '?', sizePx, color)
            var drawable: Drawable = BitmapDrawable(resources, base)
            if (round) drawable = RoundedBitmapDrawableFactory.create(resources, base).apply { isCircular = true }
            if (plan.badge) drawable = ctx.packageManager.getUserBadgedIcon(drawable, handle)
            val out = drawableToBitmap(drawable, sizePx)
            if (plan.dot) {
                val r = sizePx * 0.16f
                Canvas(out).drawCircle(sizePx - r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = accent })
            }
            val icon = BitmapDrawable(resources, out).apply { setBounds(0, 0, sizePx, sizePx) }
            tv.setCompoundDrawablesRelative(icon, null, null, null)
            tv.contentDescription = plan.contentDescription
            if (plan.showsText) {
                tv.compoundDrawablePadding = 14.dpToPx()
                tv.minWidth = 0
                tv.setPaddingRelative(0, tv.paddingTop, 0, tv.paddingBottom)
            } else {
                tv.text = ""
                tv.hint = null
                tv.compoundDrawablePadding = 0
                // A start drawable sits at the start padding edge and the slot is wrap_content in a column placed
                // by the home alignment: the spare width of the 48dp touch box goes on the far side, so the icon
                // lines up with the alignment edge exactly like text (centred when the alignment is centre).
                tv.minWidth = 0
                val (start, end) = HomeAppPlan.iconPadding(prefs.homeAlignment, 48.dpToPx(), sizePx)
                tv.setPaddingRelative(start, tv.paddingTop, end, tv.paddingBottom)
            }
        }
    }

    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** The launcher icon of one pinned shortcut, or null when it is gone or cannot be read. */
    private fun shortcutIcon(ctx: Context, pkg: String, shortcutId: String, handle: android.os.UserHandle): Drawable? = try {
        val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(pkg)
            setShortcutIds(listOf(shortcutId))
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        launcherApps.getShortcuts(query, handle)?.firstOrNull()
            ?.let { launcherApps.getShortcutIconDrawable(it, ctx.resources.displayMetrics.densityDpi) }
    } catch (e: Exception) {
        null
    }

    /**
     * The optional icon row: at the bottom (the home text slots get that much extra bottom padding, plus
     * the navigation bar inset) or as the last child of the top block, whose layout listener then moves
     * the slots down. Rebuilt whenever its prefs, the theme or the home screen change.
     */
    private fun renderIconRow() {
        val b = _binding ?: return
        iconRow?.let { (it.parent as? ViewGroup)?.removeView(it) }
        iconRow = null
        val apps = b.homeAppsLayout
        fun setBottomPadding(px: Int) {
            val wanted = maxOf(px, tipsReserve)
            if (apps.paddingBottom != wanted) apps.setPadding(apps.paddingLeft, apps.paddingTop, apps.paddingRight, wanted)
        }
        if (!iconPrefs.enabled || iconPrefs.slots.isEmpty()) {
            setBottomPadding(appsBasePaddingBottom)
            return
        }
        val row = IconRowView(requireContext())
        row.bind(iconPrefs, ::iconRowTap, ::iconRowLongPressed)
        iconRow = row
        val underClock = iconPrefs.position == IconPosition.UNDER_CLOCK && b.dateTimeLayout.visibility == View.VISIBLE
        if (underClock) {
            b.dateTimeLayout.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setBottomPadding(appsBasePaddingBottom)
        } else {
            val navInset = androidx.core.view.ViewCompat.getRootWindowInsets(b.root)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            lp.marginStart = 24.dpToPx()
            lp.marginEnd = 24.dpToPx()
            lp.bottomMargin = navInset + 8.dpToPx()
            b.mainLayout.addView(row, lp)
            setBottomPadding(appsBasePaddingBottom + IconRowView.heightPx(requireContext(), iconPrefs) + navInset)
        }
    }

    private fun iconRowTap(slot: IconSlot) = launchApp(slot.packageName, slot.packageName, slot.activityClassName, slot.user)

    private fun iconRowLongPressed(index: Int, slot: IconSlot) {
        val ctx = requireContext()
        val replaceFlag = Constants.FLAG_SET_ICON_ROW_APP_1 + index
        DetailsSheet.show(
            ctx, slot.packageName, getUserHandleFromString(ctx, slot.user), IconRenderer.label(ctx, slot),
            listOf(
                SheetAction(getString(R.string.details_replace)) { showAppList(replaceFlag, false, true) },
                SheetAction(getString(R.string.icon_row_remove)) {
                    iconPrefs.slots = IconSlots.remove(iconPrefs.slots, index)
                },
            ),
        )
    }

    /** The notification block, now playing line and the counts after the home app labels. */
    private fun renderNotifications() {
        val n = notes ?: return
        n.render()
        val counts = n.counts()
        val accent = ThemeStore.current(requireContext())?.accentText
            ?: requireContext().getColorFromAttr(R.attr.primaryColor)
        val slots = listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
        )
        for ((i, tv) in slots.withIndex()) {
            val slot = i + 1
            if (tv.text.isEmpty()) continue
            val pkg = prefs.getAppPackage(slot)
            val suffix = NotificationLogic.labelCountSuffix(counts[pkg] ?: 0, true)
            val base = prefs.getAppName(slot)
            tv.text = if (suffix.isEmpty()) base else SpannableStringBuilder(base).apply {
                val start = length
                append("  ").append(suffix)
                setSpan(ForegroundColorSpan(accent), start, length, 0)
                setSpan(RelativeSizeSpan(0.6f), start, length, 0)
            }
        }
    }

    private fun populateHomeScreenCore(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

        binding.homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp6, prefs.appName6, prefs.appPackage6, prefs.appUser6, prefs.isShortcut6, prefs.shortcutId6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        binding.homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp7, prefs.appName7, prefs.appPackage7, prefs.appUser7, prefs.isShortcut7, prefs.shortcutId7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        binding.homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp8, prefs.appName8, prefs.appPackage8, prefs.appUser8, prefs.isShortcut8, prefs.shortcutId8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
    }

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        // A pinned web link has no package to check, only a stored link to look up
        if (LinkEntry.idFromPinToken(packageName) != null) {
            val exists = prefs.allLinks().any { it.pinToken == packageName }
            textView.text = if (exists) appName else ""
            return exists
        }

        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    return true
                }
                textView.text = ""
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        // An empty slot opens the picker straight away instead of asking for a long press
        if (prefs.getAppPackage(location).isEmpty()) {
            showAppList(Constants.FLAG_SET_HOME_APP_1 + location - 1, rename = false, includeHiddenApps = true)
            return
        }
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun showStatusBar() {
        activity?.window?.showStatusBar()
    }

    private fun hideStatusBar() {
        activity?.window?.hideStatusBar()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Long press on a home slot: the details sheet, with "Replace..." keeping the old pick-an-app flow. */
    private fun homeAppLongPressed(location: Int, flag: Int) {
        val pkg = prefs.getAppPackage(location)
        val name = prefs.getAppName(location)
        if (pkg.isEmpty() || name.isEmpty()) {
            showAppList(flag, name.isNotEmpty(), true)
            return
        }
        DetailsSheet.show(
            requireContext(), pkg, getUserHandleFromString(requireContext(), prefs.getAppUser(location)), name,
            listOf(SheetAction(getString(R.string.details_replace)) { showAppList(flag, true, true) }),
        )
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun gestureRunner() = (requireActivity() as MainActivity).gestureRunner

    // Edge swipes fall back to the plain swipe when the edge trigger itself is not bound
    private fun runGesture(trigger: Trigger) {
        val runner = gestureRunner()
        if (runner.run(trigger)) return
        GestureGeometry.plainSwipeFor(trigger)?.let { runner.run(it) }
    }

    private fun getSwipeGestureListener(context: Context): OnSwipeTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onGesture(trigger: Trigger) {
                super.onGesture(trigger)
                runGesture(trigger)
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onGesture(trigger: Trigger) {
                super.onGesture(trigger)
                runGesture(trigger)
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    /**
     * Clock, date, weather, agenda and notifications grow the top block; the home apps are
     * centred under a fixed top padding, so push that padding down whenever the block is taller.
     */
    private fun keepAppsBelowTopBlock() {
        // Landscape lays the block out differently and has no fixed top padding to adjust.
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
        val minTop = 112.dpToPx()
        binding.dateTimeLayout.addOnLayoutChangeListener { block, _, _, _, _, _, _, _, _ ->
            val apps = _binding?.homeAppsLayout ?: return@addOnLayoutChangeListener
            val wanted = if (block.visibility == View.VISIBLE) maxOf(minTop, block.bottom + 16.dpToPx()) else minTop
            // Applied in the same traversal (the view root re-lays out before drawing), not posted:
            // a posted change showed one frame of apps over the glance card.
            if (apps.paddingTop != wanted) apps.setPadding(apps.paddingLeft, wanted, apps.paddingRight, apps.paddingBottom)
        }
    }

    /** The first-run hint sits at the bottom; reserve its height so the 5th/6th apps do not overlap it. */
    private fun keepAppsAboveTips() {
        binding.firstRunTips.addOnLayoutChangeListener { tips, _, top, _, bottom, _, _, _, _ ->
            val b = _binding ?: return@addOnLayoutChangeListener
            val wanted = if (tips.visibility == View.VISIBLE) (bottom - top) + 56.dpToPx() + 8.dpToPx() else 0
            if (wanted != tipsReserve) {
                tipsReserve = wanted
                b.homeAppsLayout.post {
                    if (_binding != null) renderIconRow()
                }
            }
        }
    }

    /** Holds back mainLayout's layout transition until the first content results are applied. */
    private inner class LayoutHold(private val main: ViewGroup, private val transition: android.animation.LayoutTransition) {
        private var waiting = -1 // -1 until onResume has said how many fetches it started
        private var done = 0
        private var released = false
        private val release = Runnable { releaseNow() }

        private fun releaseNow() {
            if (released) return
            released = true
            main.removeCallbacks(release)
            main.layoutTransition = transition
        }

        fun start() {
            main.layoutTransition = null
            main.postDelayed(release, MAX_HOLD_MS)
        }

        fun expect(count: Int) {
            waiting = count
            check()
        }

        fun oneDone() {
            done++
            check()
        }

        private fun check() {
            // Let the traversal that lays out the new content run before the transition returns.
            if (waiting >= 0 && done >= waiting) main.postDelayed(release, SETTLE_MS)
        }
    }

    private var layoutHold: LayoutHold? = null

    /**
     * mainLayout animates layout changes, so on a fresh home (recreate after a theme change) the glance
     * block fading in and the apps sliding below it played out as a visible jump. Suspend that
     * animation until the first weather/agenda fetches have been applied (nothing to wait for: right
     * after the first layout), capped at [MAX_HOLD_MS] so a slow fetch cannot keep it off for long.
     */
    private fun settleFirstLayout() {
        val main = binding.mainLayout
        val transition = main.layoutTransition ?: return
        layoutHold = LayoutHold(main, transition).also { it.start() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::iconPrefs.isInitialized) iconPrefs.unregisterListener(iconPrefsListener)
        iconRow = null
        mainTouchListener = null
        notes?.release()
        notes = null
        _binding = null
    }

    private companion object {
        const val MAX_HOLD_MS = 4000L
        const val SETTLE_MS = 150L
    }
}
