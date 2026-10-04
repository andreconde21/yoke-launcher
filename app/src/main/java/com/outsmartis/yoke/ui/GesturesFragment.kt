package com.outsmartis.yoke.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.outsmartis.yoke.MainViewModel
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.AppModel
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.FragmentGesturesBinding
import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.gestures.GestureConfig
import com.outsmartis.yoke.gestures.GestureDefaults
import com.outsmartis.yoke.gestures.GestureParse
import com.outsmartis.yoke.gestures.Trigger
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.copyToClipboard
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.showToast

/** Every trigger with its current action; tap one to pick another. */
class GesturesFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var dialog: YokeDialog? = null

    private var _binding: FragmentGesturesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGesturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        binding.gestureCheatSheet.setOnClickListener {
            showGestureCheatSheet(requireActivity() as AppCompatActivity, prefs.loadGestures())
        }
        binding.gestureExport.setOnClickListener { exportGestures(share = false) }
        binding.gestureExport.setOnLongClickListener { exportGestures(share = true); true }
        binding.gestureImport.setOnClickListener { importGestures() }
        binding.gestureReset.setOnClickListener { resetGestures() }

        viewModel.updateSwipeApps.observe(viewLifecycleOwner) { populate() }
        viewModel.gestureShortcutApp.observe(viewLifecycleOwner) { app ->
            if (app != null) {
                viewModel.gestureShortcutApp.value = null
                pickShortcut(app)
            }
        }
        populate()
    }

    private fun populate() {
        val config = prefs.loadGestures()
        binding.gestureList.removeAllViews()
        for (trigger in Trigger.entries) {
            val row = layoutInflater.inflate(R.layout.item_gesture_row, binding.gestureList, false)
            row.findViewById<TextView>(R.id.gestureTrigger).text = trigger.label
            row.findViewById<TextView>(R.id.gestureAction).text = config[trigger].describe(requireContext())
            row.setOnClickListener { showActionPicker(trigger) }
            binding.gestureList.addView(row)
        }
    }

    private fun showDialog(newDialog: YokeDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private fun setAction(trigger: Trigger, action: GestureAction) {
        prefs.saveGestures(prefs.loadGestures().with(trigger, action))
        populate()
    }

    // Action picker

    private fun showActionPicker(trigger: Trigger) {
        val ctx = requireContext()
        val entries = GestureAction.simple.map { it.label to { setAction(trigger, it) } } +
                listOf<Pair<String, () -> Unit>>(
                    "Open app..." to { pickApp(trigger, Constants.FLAG_SET_GESTURE_APP) },
                    "Open app shortcut..." to { pickApp(trigger, Constants.FLAG_SET_GESTURE_SHORTCUT_APP) },
                    "Open link..." to { askUri(trigger) },
                )
        var picker: YokeDialog? = null
        picker = ctx.createDialog(R.string.gesture_choose_action, R.string.close) { container ->
            val (scroll, column) = scrollColumn(ctx)
            for ((text, onPick) in entries) {
                column.addView(TextView(ctx, null, 0, R.style.TextSmall).apply {
                    this.text = text
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener { picker?.dismiss(); onPick() }
                })
            }
            scroll
        }
        showDialog(picker)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun pickApp(trigger: Trigger, flag: Int) {
        viewModel.pendingGestureTrigger = trigger
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_gesturesFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun pickShortcut(app: AppModel.App) {
        val trigger = viewModel.pendingGestureTrigger ?: return
        val ctx = requireContext()
        val shortcuts = try {
            val launcher = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(app.appPackage)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            launcher.getShortcuts(query, app.user).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        if (shortcuts.isEmpty()) {
            ctx.showToast(getString(R.string.gesture_no_shortcuts))
            return
        }
        var picker: YokeDialog? = null
        picker = ctx.createDialog(R.string.gesture_choose_shortcut, R.string.close) {
            val (scroll, column) = scrollColumn(ctx)
            for (shortcut in shortcuts) {
                column.addView(TextView(ctx, null, 0, R.style.TextSmall).apply {
                    text = (shortcut.longLabel ?: shortcut.shortLabel ?: shortcut.id).toString()
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener {
                        picker?.dismiss()
                        setAction(trigger, GestureAction.OpenShortcut(app.appPackage, shortcut.id, app.user.toString()))
                    }
                })
            }
            scroll
        }
        showDialog(picker)
    }

    private fun askUri(trigger: Trigger) {
        val ctx = requireContext()
        lateinit var input: EditText
        showDialog(
            ctx.createDialog(R.string.gesture_enter_uri, R.string.okay, onAction = {
                val uri = input.text.toString().trim()
                val valid = uri.isNotEmpty() && try {
                    Intent.parseUri(uri, 0)
                    true
                } catch (e: Exception) {
                    false
                }
                if (valid) setAction(trigger, GestureAction.OpenUri(uri))
                else ctx.showToast(getString(R.string.gesture_uri_invalid))
            }) { container ->
                EditText(ctx, null, 0, R.style.TextSmall).also {
                    input = it
                    it.setHint(R.string.gesture_uri_hint)
                    it.inputType = InputType.TYPE_TEXT_VARIATION_URI
                    it.setSingleLine()
                }
            }
        )
    }

    // Export / import / reset

    private fun exportGestures(share: Boolean) {
        val json = prefs.loadGestures().toJson()
        if (share) {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, json)
                    },
                    getString(R.string.gesture_export)
                )
            )
        } else {
            requireContext().copyToClipboard(json)
            requireContext().showToast(getString(R.string.gesture_export_copied))
        }
    }

    private fun importGestures() {
        val ctx = requireContext()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (text.isNullOrBlank()) {
            ctx.showToast(getString(R.string.gesture_import_empty))
            return
        }
        when (val result = GestureConfig.parse(text)) {
            is GestureParse.Ok -> {
                prefs.saveGestures(result.config)
                populate()
                ctx.showToast(getString(R.string.gesture_import_done))
            }
            is GestureParse.Error ->
                ctx.showToast(getString(R.string.gesture_import_failed, result.message), android.widget.Toast.LENGTH_LONG)
        }
    }

    private fun resetGestures() {
        prefs.saveGestures(GestureDefaults.create())
        populate()
        requireContext().showToast(getString(R.string.gesture_reset_done))
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
        _binding = null
    }
}
