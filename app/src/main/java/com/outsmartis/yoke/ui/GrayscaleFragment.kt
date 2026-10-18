package com.outsmartis.yoke.ui

import com.outsmartis.yoke.helper.asTextField
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
        binding.grayscaleCarColour.setOnClickListener {
            prefs.carColour = !prefs.carColour
            GrayscaleController.evaluate(requireContext())
            refresh()
        }
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
            "${getString(R.string.grayscale_exceptions)}: ${resources.getQuantityString(R.plurals.grayscale_exceptions_count, visibleExceptionCount(), visibleExceptionCount())}"
        renderRecent()
        val inCar = prefs.carMirrorUntil > System.currentTimeMillis()
        binding.grayscaleCarColour.text = "${getString(R.string.grayscale_car_colour)}: ${getString(if (prefs.carColour) R.string.on else R.string.off)}" +
            if (inCar) " (${getString(R.string.grayscale_car_colour_now)})" else ""
        val pausedUntil = prefs.pausedUntil
        binding.grayscalePause.text = if (pausedUntil > System.currentTimeMillis()) {
            getString(R.string.grayscale_resume, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntil)))
        } else getString(R.string.grayscale_pause)
    }

    /**
     * Exceptions the user can see and untick: in the list or in the recent apps. The saved set
     * also holds packages that are not installed (the unused debug/release twin of Yoke), which
     * would otherwise count as colour apps with nothing ticked.
     */
    private fun visibleExceptionCount(): Int {
        return visibleGrayscaleExceptionCount(requireContext(), prefs)
    }

    /**
     * The apps seen in front lately, each tappable to toggle its exception. This is how helpers
     * without a launcher icon (a gallery's video player or editor) get exempted.
     */
    private fun renderRecent() {
        val ctx = requireContext()
        val box = binding.grayscaleRecent
        box.removeAllViews()
        val recent = prefs.recent.filter { it != ctx.packageName }
        if (recent.isEmpty()) return
        val density = resources.displayMetrics.density
        box.addView(TextView(ctx, null, 0, R.style.TextSmall).apply {
            setText(R.string.grayscale_recent)
            alpha = 0.7f
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        })
        val pm = ctx.packageManager
        for (pkg in recent) {
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            box.addView(TextView(ctx, null, 0, R.style.TextSmall).apply {
                val mark = if (pkg in prefs.exceptions) "[x] " else "[ ] "
                text = if (label == pkg) mark + pkg else "$mark$label  ·  $pkg"
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                setOnClickListener {
                    val ex = prefs.exceptions.toMutableSet()
                    if (!ex.add(pkg)) ex.remove(pkg)
                    prefs.exceptions = ex
                    GrayscaleController.evaluate(ctx)
                    refresh()
                }
            })
        }
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

    private fun launchableApps(): List<GrayscaleAppEntry> = grayscaleExceptionCandidates(requireContext(), prefs)

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
                val search = EditText(ctx, null, 0, R.style.TextSmall).asTextField().apply {
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
                                // Saved on every tap: closing with back or outside must not lose picks.
                                prefs.exceptions = selected.toSet()
                                GrayscaleController.evaluate(ctx)
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
                // Keep the keyboard closed until the user taps the search field.
                root.isFocusableInTouchMode = true
                root.addView(search)
                root.addView(scroll)
                root.requestFocus()
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

/**
 * The apps the Exceptions dialog lists: every launchable app, Yoke itself, and the recently seen
 * apps (helpers without a launcher icon), alphabetical. The row count uses the same list so the
 * number always matches the ticks.
 */
internal fun grayscaleExceptionCandidates(ctx: android.content.Context, prefs: GrayscalePrefs): List<GrayscaleAppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { GrayscaleAppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.packageName }
    val known = apps.map { it.packageName }.toSet()
    val extra = (prefs.recent + ctx.packageName).distinct().filter { it !in known }.mapNotNull { pkg ->
        try {
            GrayscaleAppEntry(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    return (apps + extra).sortedBy { it.label.lowercase() }
}

/** Saved exceptions that are ticked in the dialog; packages not installed (Yoke's debug/release twin) are left out. */
internal fun visibleGrayscaleExceptionCount(ctx: android.content.Context, prefs: GrayscalePrefs): Int {
    val shown = grayscaleExceptionCandidates(ctx, prefs).map { it.packageName }.toSet()
    return prefs.exceptions.count { it in shown }
}

internal class GrayscaleAppEntry(val label: String, val packageName: String)
