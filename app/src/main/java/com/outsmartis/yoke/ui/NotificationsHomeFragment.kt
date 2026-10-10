package com.outsmartis.yoke.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.databinding.FragmentNotificationsHomeBinding
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.asTextField
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.notifications.NotificationLogic
import com.outsmartis.yoke.notifications.NotificationPrefs
import com.outsmartis.yoke.notifications.NotificationStore

/** Notifications on home: master switch, access status, picked apps and the display options. */
class NotificationsHomeFragment : BaseFragment() {

    private lateinit var prefs: NotificationPrefs
    private var dialog: YokeDialog? = null

    private var _binding: FragmentNotificationsHomeBinding? = null
    private val binding get() = _binding!!

    private class AppEntry(val label: String, val packageName: String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = NotificationPrefs(requireContext())

        binding.notesMaster.setOnClickListener { prefs.enabled = !prefs.enabled; changed() }
        binding.notesAccessButton.setOnClickListener { openAccessSettings() }
        binding.notesApps.setOnClickListener { showAppPicker() }
        binding.notesMaxLines.setOnClickListener {
            prefs.maxLines = if (prefs.maxLines == NotificationLogic.MAX_LINES_SMALL) NotificationLogic.MAX_LINES_LARGE
            else NotificationLogic.MAX_LINES_SMALL
            changed()
        }
        binding.notesShowText.setOnClickListener { prefs.showText = !prefs.showText; changed() }
        binding.notesHideDnd.setOnClickListener { prefs.hideDuringDnd = !prefs.hideDuringDnd; changed() }
        binding.notesCounts.setOnClickListener { prefs.countsOnApps = !prefs.countsOnApps; changed() }
        // Now playing lives in Settings' home screen section; it doesn't depend on this screen.
        binding.notesNowPlaying.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun changed() {
        NotificationStore.refresh()
        refresh()
    }

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(requireContext()).contains(requireContext().packageName)

    private fun refresh() {
        binding.notesMaster.text = "${getString(R.string.notes_master)}: ${onOff(prefs.enabled)}"
        val access = hasAccess()
        binding.notesAccessStatus.setText(if (access) R.string.notes_access_ok else R.string.notes_access_missing)
        binding.notesAccessButton.setText(R.string.notes_access_button)
        val picked = prefs.allowed.size
        binding.notesApps.text = "${getString(R.string.notes_apps)}: " +
            if (picked == 0) getString(R.string.notes_apps_empty) else getString(R.string.notes_apps_count, picked)
        binding.notesMaxLines.text = "${getString(R.string.notes_max_lines)}: ${prefs.maxLines}"
        binding.notesShowText.text = "${getString(R.string.notes_show_text)}: ${onOff(prefs.showText)}"
        binding.notesHideDnd.text = "${getString(R.string.notes_hide_dnd)}: ${onOff(prefs.hideDuringDnd)}"
        binding.notesCounts.text = "${getString(R.string.notes_counts)}: ${onOff(prefs.countsOnApps)}"
        binding.notesNowPlaying.text = "${getString(R.string.notes_now_playing)}: ${onOff(prefs.nowPlaying)}"
    }

    private fun openAccessSettings() {
        val ctx = requireContext()
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(ctx, com.outsmartis.yoke.notifications.YokeNotificationListener::class.java).flattenToString(),
        )
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            try { startActivity(detail) } catch (_: ActivityNotFoundException) {}
        }
    }

    private fun launchableApps(): List<AppEntry> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun showAppPicker() {
        val ctx = requireContext()
        val apps = launchableApps()
        val selected = prefs.allowed.toMutableSet()
        dialog?.dismiss()
        val d = ctx.createDialog(R.string.notes_apps, R.string.okay, onAction = {
            prefs.allowed = selected
            changed()
        }) {
            val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val density = resources.displayMetrics.density
            val hint = TextView(ctx, null, 0, R.style.TextSmall).apply {
                setText(R.string.notes_apps_hint)
                alpha = 0.7f
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val search = EditText(ctx, null, 0, R.style.TextSmall).asTextField().apply {
                setHint(R.string.notes_apps_search)
                setSingleLine()
            }
            val (scroll, column) = scrollColumn(ctx)
            fun fill(query: String) {
                column.removeAllViews()
                val q = query.trim().lowercase()
                for (app in apps.filter { q.isEmpty() || it.label.lowercase().contains(q) }) {
                    column.addView(TextView(ctx, null, 0, R.style.TextSmall).apply {
                        fun render() {
                            text = (if (app.packageName in selected) "[x] " else "[ ] ") + app.label
                        }
                        render()
                        setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                        setOnClickListener {
                            if (!selected.add(app.packageName)) selected.remove(app.packageName)
                            render()
                            // Saved on every tap: closing the dialog with back or outside must not lose picks.
                            prefs.allowed = selected.toSet()
                            changed()
                        }
                    })
                }
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = fill(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            fill("")
            root.addView(hint)
            root.addView(search)
            root.addView(scroll)
            root
        }
        dialog = d
        d.showRespectingStatusBar()
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
        _binding = null
    }
}
