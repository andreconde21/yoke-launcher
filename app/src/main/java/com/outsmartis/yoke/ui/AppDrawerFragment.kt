package com.outsmartis.yoke.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.AppModel
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.FragmentAppDrawerBinding
import com.outsmartis.yoke.helper.deletePinnedShortcut
import com.outsmartis.yoke.helper.LinkDialogs
import com.outsmartis.yoke.helper.hideKeyboard
import com.outsmartis.yoke.helper.isEinkDisplay
import com.outsmartis.yoke.helper.isSystemAnimationsDisabled
import com.outsmartis.yoke.helper.isSystemApp
import com.outsmartis.yoke.helper.openAppInfo
import com.outsmartis.yoke.helper.openSearch
import com.outsmartis.yoke.helper.openUrl
import com.outsmartis.yoke.helper.showKeyboard
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.helper.uninstall
import com.outsmartis.yoke.palette.Calculator
import com.outsmartis.yoke.palette.ExtraSearch
import com.outsmartis.yoke.palette.SearchHit
import com.outsmartis.yoke.palette.SearchKind
import com.outsmartis.yoke.palette.SettingsPages
import com.outsmartis.yoke.palette.CommandPalette
import com.outsmartis.yoke.palette.DefaultPaletteActions
import com.outsmartis.yoke.palette.PaletteActions
import com.outsmartis.yoke.palette.PaletteContext
import com.outsmartis.yoke.palette.PaletteMode
import com.outsmartis.yoke.palette.PaletteQuery
import com.outsmartis.yoke.palette.ShortcutHit
import com.outsmartis.yoke.palette.ShortcutSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppDrawerFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var searchTextView: TextView? = null
    private var cachedIsCjkKeyboard: Boolean? = null

    companion object {
        private const val MAX_SHORTCUT_ROWS = 50
    }

    private var flag = Constants.FLAG_LAUNCH_APP
    private var forceKeyboard = false
    private var canRename = false
    private var paletteMode = false
    private val extraSearch by lazy { ExtraSearch(requireContext()) }
    private var shortcutHits: List<ShortcutHit>? = null
    private var shortcutsLoading = false
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        // The list scrolls under the navigation bar; its last row must still clear it.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { list, insets ->
            val bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                or androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, bottom)
            insets
        }
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
            forceKeyboard = it.getBoolean(Constants.Key.SEARCH, false)
            paletteMode = it.getBoolean(CommandPalette.ARG_PALETTE, false)
        }
        if (flag == Constants.FLAG_LAUNCH_APP) DefaultPaletteActions.register(requireContext())
        if (paletteMode && viewModel.appList.value == null) viewModel.getAppList()

        initViews()
        initSearch()
        initAdapter()
        if (flag == Constants.FLAG_LAUNCH_APP) {
            adapter.extraRows = { q -> extraSearch.search(q).map(::extraRow) }
            adapter.extrasReady = { extraSearch.ready }
            // The card/note/contact indexes build off the main thread; redo the search once they are in.
            extraSearch.warmUp {
                activity?.runOnUiThread { if (_binding != null && isAdded) applyQuery(binding.search.query) }
            }
        }
        initObservers()
        initClickListeners()
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP
            || flag == Constants.FLAG_SET_WEATHER_APP
            || flag in Constants.FLAG_SET_ICON_ROW_APP_1..Constants.FLAG_SET_ICON_ROW_APP_6
            || flag == Constants.FLAG_SET_GESTURE_APP || flag == Constants.FLAG_SET_GESTURE_SHORTCUT_APP)
            binding.search.queryHint = "Please select an app"
        else if (paletteMode)
            binding.search.queryHint = getString(R.string.palette_hint)
        try {
            searchTextView = binding.search.findViewById(R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.allowAutoLaunch = !isSearchComposing()
                    applyQuery(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    /** Routes the typed text: palette prefixes get their own rows, anything else filters apps and links. */
    private fun applyQuery(text: CharSequence?) {
        val raw = text?.toString().orEmpty()
        if (flag == Constants.FLAG_LAUNCH_APP) {
            val query = PaletteQuery.parse(raw)
            if (query.mode != PaletteMode.APPS) {
                adapter.showResults(paletteRows(query))
                return
            }
            if (paletteMode && raw.isBlank()) {
                adapter.showResults(hintRows())
                return
            }
        }
        adapter.clearResults()
        adapter.filter.filter(raw)
    }

    private fun extraRow(hit: SearchHit) = AppModel.PaletteResult(
        id = "extra_${hit.kind}_${hit.target}",
        appLabel = hit.title,
        detail = hit.subtitle,
        closeDrawer = hit.target != SettingsPages.YOKE_SETTINGS,
        run = { openExtra(hit) },
    )

    /** Never auto-launched: only a tap gets here. A missing Obsidian or settings page just toasts. */
    private fun openExtra(hit: SearchHit) {
        val context = requireContext()
        try {
            when {
                hit.target == SettingsPages.YOKE_SETTINGS ->
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                hit.target == SettingsPages.YOKE_APP_INFO ->
                    openAppInfo(context, Process.myUserHandle(), context.packageName)
                hit.kind == SearchKind.SETTING ->
                    context.startActivity(Intent(hit.target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: ActivityNotFoundException) {
            context.showToast(
                if (hit.kind == SearchKind.CARD || hit.kind == SearchKind.NOTE) R.string.search_no_obsidian
                else R.string.search_cannot_open
            )
        }
    }

    private fun paletteContext() = PaletteContext(
        requireContext(), findNavController(), viewModel, reload = ::updateCombinedAppList
    )

    private fun infoRow(id: String, label: String) =
        AppModel.PaletteResult(id = id, appLabel = label, closeDrawer = false)

    private fun hintRows(): List<AppModel> = listOf(
        PaletteMode.CALC to R.string.palette_hint_calc,
        PaletteMode.ACTIONS to R.string.palette_hint_actions,
        PaletteMode.SHORTCUTS to R.string.palette_hint_shortcuts,
    ).map { (mode, label) ->
        AppModel.PaletteResult(
            id = "hint_${mode.name}",
            appLabel = getString(label),
            closeDrawer = false,
            run = { binding.search.setQuery(mode.prefix.toString(), false) },
        )
    }

    private fun paletteRows(query: PaletteQuery): List<AppModel> = when (query.mode) {
        PaletteMode.CALC -> calcRows(query.text)
        PaletteMode.ACTIONS -> PaletteActions.search(query.text).map { action ->
            AppModel.PaletteResult(
                id = "action_${action.id}",
                appLabel = action.label,
                closeDrawer = action.closeDrawer,
                run = { action.run(paletteContext()) },
            )
        }.ifEmpty { listOf(infoRow("no_actions", getString(R.string.palette_no_actions))) }

        PaletteMode.SHORTCUTS -> shortcutRows(query.text)
        PaletteMode.APPS -> emptyList()
    }

    private fun calcRows(expression: String): List<AppModel> {
        return when (val result = Calculator.evaluate(expression)) {
            is Calculator.Result.Value -> listOf(
                AppModel.PaletteResult(
                    id = "calc",
                    appLabel = "= ${result.text}",
                    closeDrawer = false,
                    run = {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("result", result.text))
                        requireContext().showToast(getString(R.string.palette_copied, result.text))
                    },
                )
            )

            is Calculator.Result.Error -> listOf(
                infoRow(
                    "calc_error",
                    getString(
                        when (result.reason) {
                            Calculator.Reason.EMPTY -> R.string.palette_calc_empty
                            Calculator.Reason.DIVISION_BY_ZERO -> R.string.palette_calc_div_zero
                            else -> R.string.palette_calc_error
                        }
                    )
                )
            )
        }
    }

    private fun shortcutRows(text: String): List<AppModel> {
        val context = requireContext()
        if (!ShortcutSearch.canQuery(context))
            return listOf(infoRow("shortcuts_need_default", getString(R.string.palette_shortcuts_need_default)))
        val hits = shortcutHits
        if (hits == null) {
            loadShortcuts()
            return listOf(infoRow("shortcuts_loading", getString(R.string.palette_shortcuts_loading)))
        }
        return ShortcutSearch.filter(hits, text).take(MAX_SHORTCUT_ROWS).map { hit ->
            AppModel.PaletteResult(
                id = "shortcut_${hit.info.`package`}_${hit.info.id}_${hit.info.userHandle}",
                appLabel = "${hit.label} · ${hit.appLabel}",
                run = {
                    try {
                        ShortcutSearch.start(requireContext(), hit)
                    } catch (_: Exception) {
                        requireContext().showToast(getString(R.string.unable_to_open_shortcut))
                    }
                },
            )
        }.ifEmpty { listOf(infoRow("no_shortcuts", getString(R.string.palette_no_shortcuts))) }
    }

    private fun loadShortcuts() {
        if (shortcutsLoading) return
        shortcutsLoading = true
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { ShortcutSearch.loadAll(appContext) }
            shortcutsLoading = false
            shortcutHits = loaded
            if (_binding != null) applyQuery(binding.search.query)
        }
    }

    private fun isSearchComposing(): Boolean {
        val text = searchTextView?.text
        if (text !is Spannable) return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        if (start !in 0 until end) return false
        return isCjkKeyboard()
    }

    private fun isCjkKeyboard(): Boolean {
        cachedIsCjkKeyboard?.let { return it }
        val result = try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val subtype = imm.currentInputMethodSubtype
            val language = when {
                subtype == null -> ""
                subtype.languageTag.isNotEmpty() -> subtype.languageTag // e.g. "zh-CN", "ja-JP", "en-US"
                else -> subtype.locale // deprecated fallback, e.g. "zh_CN"
            }
            language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
        } catch (e: Exception) {
            false
        }
        cachedIsCjkKeyboard = result
        return result
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { appModel ->
                if (appModel is AppModel.PaletteResult) {
                    appModel.run()
                    if (appModel.closeDrawer) findNavController().popBackStack(R.id.mainFragment, false)
                    return@AppDrawerAdapter
                }
                viewModel.selectedApp(appModel, flag)
                if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                    findNavController().popBackStack(R.id.mainFragment, false)
                else
                    findNavController().popBackStack()
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.PrivateSpaceHeader, is AppModel.PaletteResult, is AppModel.Link -> {}
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        if (appModel.user != Process.myUserHandle()) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS)
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.identity
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                if (appModel is AppModel.App) {
                    prefs.applyRenameToPinnedApps(appModel.appPackage, renameLabel)
                    viewModel.refreshHome(false)
                }
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            linkLongClickListener = { link ->
                if (link.entry.id.startsWith(com.outsmartis.yoke.data.LinkEntry.BUILT_IN_PREFIX))
                    requireContext().showToast(getString(R.string.link_built_in))
                else LinkDialogs.showEdit(requireContext(), prefs, link.entry, onChanged = ::updateCombinedAppList)
            }
        )

        adapter.autoLaunchSingle = prefs.autoLaunchSingle
        if (flag == Constants.FLAG_LAUNCH_APP) {
            adapter.noMatchRow = { query ->
                AppModel.PaletteResult(
                    id = "web_search",
                    appLabel = getString(R.string.palette_search_web, query),
                    run = { requireContext().openUrl(PaletteQuery.webSearchUrl(query)) },
                )
            }
        }

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    closeDrawer()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay())
            binding.recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        else if (requireContext().isSystemAnimationsDisabled().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = apps.toMutableList()

        // Web links sit among the apps when launching or pinning to a home slot
        if (flag == Constants.FLAG_LAUNCH_APP || flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
            val links = prefs.allLinks()
            if (links.isNotEmpty()) {
                combined.addAll(links.map { AppModel.Link(it) })
                combined.sortWith(compareBy(Collator.getInstance()) { it.appLabel })
            }
        }

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        adapter.setAppList(combined)
        applyQuery(binding.search.query)
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not())
                                binding.search.showKeyboard(prefs.autoShowKeyboard || forceKeyboard)
                    }
                }
            }
        }
    }

    private fun closeDrawer() {
        findNavController().popBackStack()
    }

    override fun onStart() {
        super.onStart()
        cachedIsCjkKeyboard = null
        binding.search.showKeyboard(prefs.autoShowKeyboard || forceKeyboard)
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchTextView = null
        _binding = null
    }
}
