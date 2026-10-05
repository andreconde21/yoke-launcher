package com.outsmartis.yoke.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.core.view.isVisible
import com.outsmartis.yoke.R
import com.outsmartis.yoke.databinding.FragmentGrayscaleBinding
import com.outsmartis.yoke.grayscale.GrayscaleController
import com.outsmartis.yoke.grayscale.GrayscalePrefs
import com.outsmartis.yoke.grayscale.ShizukuGrant
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.copyToClipboard
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.isAccessServiceEnabled
import com.outsmartis.yoke.helper.showToast
import java.text.DateFormat
import java.util.Date

/** Smart grayscale: master switch, exceptions, setup status, pause. */
class GrayscaleFragment : BaseFragment() {

    private lateinit var prefs: GrayscalePrefs
    private var dialog: YokeDialog? = null

    private var _binding: FragmentGrayscaleBinding? = null
    private val binding get() = _binding!!

    private class AppEntry(val label: String, val packageName: String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGrayscaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = GrayscalePrefs(requireContext())

        binding.grayscaleMaster.setOnClickListener {
            GrayscaleController.setFeatureOn(requireContext(), !prefs.featureOn)
            refresh()
        }
        binding.grayscaleCommand.setOnClickListener {
            requireContext().copyToClipboard(GrayscaleController.grantCommand(requireContext()))
            requireContext().showToast(getString(R.string.grayscale_copied))
        }
        binding.grayscaleHowTitle.setOnClickListener {
            binding.grayscaleHowBody.isVisible = !binding.grayscaleHowBody.isVisible
        }
        binding.grayscaleExceptions.setOnClickListener { showExceptions() }
        binding.grayscalePause.setOnClickListener {
            if (prefs.pausedUntil > System.currentTimeMillis()) GrayscaleController.resume(requireContext())
            else GrayscaleController.pause(requireContext())
            refresh()
        }
        binding.grayscaleOpenAccessibility.setOnClickListener { showAccessibilityDisclosure() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ctx = requireContext()
        val on = prefs.featureOn
        binding.grayscaleMaster.text = "${getString(R.string.grayscale_master)}: ${getString(if (on) R.string.on else R.string.off)}"
        val permission = GrayscaleController.hasPermission(ctx)
        binding.grayscaleSetupCard.isVisible = !permission
        binding.grayscaleCommand.text = GrayscaleController.grantCommand(ctx)
        if (!permission) refreshShizuku(ctx)
        binding.grayscaleStatusPermission.setText(
            if (permission) R.string.grayscale_status_permission_ok else R.string.grayscale_status_permission_missing
        )
        binding.grayscaleStatusService.setText(
            if (isAccessServiceEnabled(ctx)) R.string.grayscale_status_service_ok else R.string.grayscale_status_service_off
        )
        binding.grayscaleExceptions.text =
            "${getString(R.string.grayscale_exceptions)}: ${getString(R.string.grayscale_exceptions_count, prefs.exceptions.size)}"
        val pausedUntil = prefs.pausedUntil
        binding.grayscalePause.text = if (pausedUntil > System.currentTimeMillis()) {
            getString(R.string.grayscale_resume, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntil)))
        } else getString(R.string.grayscale_pause)
    }

    private fun refreshShizuku(ctx: android.content.Context) {
        val status = binding.grayscaleShizukuStatus
        val button = binding.grayscaleShizukuButton
        button.isVisible = true
        when (ShizukuGrant.state(ctx)) {
            ShizukuGrant.State.NotInstalled -> {
                status.setText(R.string.grayscale_shizuku_not_installed)
                button.setText(R.string.grayscale_shizuku_install)
                button.setOnClickListener { openShizukuListing() }
            }
            ShizukuGrant.State.NotRunning -> {
                status.setText(R.string.grayscale_shizuku_not_running)
                button.setText(R.string.grayscale_shizuku_open)
                button.setOnClickListener {
                    val launch = ctx.packageManager.getLaunchIntentForPackage(ShizukuGrant.SHIZUKU_PACKAGE)
                    if (launch != null) startActivity(launch)
                }
            }
            ShizukuGrant.State.NeedsPermission, ShizukuGrant.State.Ready -> {
                status.setText(R.string.grayscale_shizuku_ready)
                button.setText(R.string.grayscale_shizuku_grant)
                button.setOnClickListener { grantWithShizuku() }
            }
            ShizukuGrant.State.Granted -> button.isVisible = false
        }
    }

    private fun openShizukuListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuGrant.SHIZUKU_PACKAGE}")))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun grantWithShizuku() {
        val ctx = requireContext()
        ShizukuGrant.requestAndGrant(ctx) { error ->
            if (_binding == null) return@requestAndGrant
            if (error == null) {
                ctx.showToast(getString(R.string.grayscale_granted))
            } else if (error == "denied") {
                ctx.showToast(getString(R.string.grayscale_shizuku_denied))
            } else {
                binding.grayscaleShizukuStatus.text = getString(R.string.grayscale_shizuku_failed, error)
            }
            refresh()
        }
    }

    private fun showAccessibilityDisclosure() {
        val ctx = requireContext()
        showDialog(
            ctx.createDialog(
                title = R.string.grayscale_title,
                action = R.string.enable,
                message = R.string.accessibility_disclosure,
                onAction = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        )
    }

    private fun showDialog(newDialog: YokeDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    // Exceptions: every launchable app, alphabetical, tap to tick

    private fun launchableApps(): List<AppEntry> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun showExceptions() {
        val ctx = requireContext()
        val apps = launchableApps()
        val selected = prefs.exceptions.toMutableSet()
        showDialog(
            ctx.createDialog(R.string.grayscale_exceptions, R.string.okay, onAction = {
                prefs.exceptions = selected
                GrayscaleController.evaluate(ctx)
                refresh()
            }) {
                val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                val search = EditText(ctx, null, 0, R.style.TextSmall).apply {
                    setHint(R.string.grayscale_exceptions_search)
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
                            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt())
                            setOnClickListener {
                                if (!selected.add(app.packageName)) selected.remove(app.packageName)
                                render()
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
                root.addView(search)
                root.addView(scroll)
                root
            }
        )
    }

    override fun onDestroyView() {
        ShizukuGrant.release()
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
        _binding = null
    }
}
