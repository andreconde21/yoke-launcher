package com.outsmartis.yoke.details

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Gravity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.helper.openAppInfo
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import java.util.concurrent.Executors

/** An extra footer action for the sheet (Replace..., Rename, Hide, Uninstall). */
class SheetAction(val label: String, val onClick: () -> Unit)

/**
 * Long-press sheet for an app: live agent details when the app ships a launcher details
 * provider (Conductore), then its launcher shortcuts and footer actions. Minimal text,
 * coloured from the active theme.
 */
class DetailsSheet private constructor(
    private val context: Context,
    private val packageName: String,
    private val user: UserHandle,
    private val label: String,
    private val extraActions: List<SheetAction>,
) {
    private val client = AppDetailsClient(context)
    private val main = Handler(Looper.getMainLooper())
    private val dialog = Dialog(context)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val density = context.resources.displayMetrics.density

    private val theme = ThemeStore.current(context)
    private val fg = theme?.foreground ?: context.getColorFromAttr(R.attr.primaryColor)
    private val secondary = theme?.secondary ?: context.getColorFromAttr(R.attr.primaryColorTrans50)
    private val accent = theme?.accentText ?: fg
    private val fonts = ThemeApplier.fonts(context)

    private var authority: String? = null
    private var observer: ContentObserver? = null
    @Volatile private var dismissed = false
    private var loadSeq = 0

    private fun dp(v: Int) = (v * density).toInt()

    private fun show() {
        val scroll = ScrollView(context).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.setPadding(dp(24), dp(20), dp(24), dp(20))
        // The sheet's background runs behind the navigation bar; its content stays above it.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(24) + bars.left, dp(20), dp(24) + bars.right, dp(20) + bars.bottom)
            insets
        }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(GradientDrawable().apply {
                val r = 24 * density
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(theme?.surface ?: context.getColorFromAttr(R.attr.dialogShadeColor).let { it or (0xFF shl 24) })
            })
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.5f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        dialog.setOnDismissListener {
            dismissed = true
            observer?.let { client.unobserve(it) }
            observer = null
        }
        render(header = label, body = emptyList(), hint = null, shortcuts = emptyList())
        dialog.show()
        authority = client.authorityOf(packageName)
        reload()
    }

    /** Loads details and shortcuts off the main thread, then renders; called again on provider change. */
    private fun reload() {
        val seq = ++loadSeq
        io.execute {
            val shortcuts = loadShortcuts()
            val auth = authority
            val result = auth?.let { client.load(it) }
            main.post {
                if (dismissed || seq != loadSeq) return@post
                if (auth != null && observer == null && result is AppDetailsClient.Result.Ok) {
                    observer = client.observe(auth) { if (!dismissed) reload() }
                }
                present(result, shortcuts)
            }
        }
    }

    private fun present(result: AppDetailsClient.Result?, shortcuts: List<ShortcutInfo>) {
        val isConductore = packageName == CONDUCTORE_PACKAGE
        when (result) {
            is AppDetailsClient.Result.Ok -> {
                val d = result.details
                val now = System.currentTimeMillis()
                val s = d.summary
                val summaryText = DetailsText.summaryLine(s, d.items.size)
                val updated = if (s != null && s.monitoring) DetailsText.updated(s.updatedAt, now) else ""
                val head = listOf(summaryText, updated).filter { it.isNotEmpty() }.joinToString(" · ")
                val rows = when {
                    s != null && !s.monitoring -> listOf(note(context.getString(R.string.details_not_watching)))
                    d.items.isEmpty() -> listOf(note(context.getString(R.string.details_no_agents)))
                    else -> d.items.map { itemRow(it, now) }
                }
                render(label, head, rows, null, shortcuts)
            }
            AppDetailsClient.Result.NeedsNewerAndroid ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_needs_android_12) else null, shortcuts)
            AppDetailsClient.Result.Unavailable ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_update_conductore) else null, shortcuts)
            null ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_update_conductore) else null, shortcuts)
        }
    }

    private fun render(header: String, body: List<View>, hint: String?, shortcuts: List<ShortcutInfo>) =
        render(header, "", body, hint, shortcuts)

    private fun render(header: String, summary: String, body: List<View>, hint: String?, shortcuts: List<ShortcutInfo>) {
        content.removeAllViews()
        content.addView(text(header, 22f, fg, bold = true))
        if (summary.isNotEmpty()) content.addView(text(summary, 13f, secondary).apply { setPadding(0, dp(4), 0, 0) })
        if (hint != null) content.addView(note(hint))
        if (body.isNotEmpty()) {
            content.addView(spacer(12))
            body.forEach { content.addView(it) }
        }
        content.addView(spacer(16))
        shortcuts.forEach { sc ->
            content.addView(action(sc.shortLabel?.toString() ?: sc.id) { startShortcut(sc) })
        }
        content.addView(action(context.getString(R.string.details_open_app, label)) { openApp() })
        content.addView(action(context.getString(R.string.details_app_info)) { dismissThen { openAppInfo(context, user, packageName) } })
        extraActions.forEach { a -> content.addView(action(a.label) { dismissThen(a.onClick) }) }
        ThemeApplier.fonts(context)?.let { applyFont(content, it) }
    }

    private fun applyFont(v: View, f: ThemeApplier.Fonts) {
        if (v is TextView) v.typeface = if (f.allBold || v.typeface?.isBold == true) f.bold else f.regular
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFont(v.getChildAt(i), f)
    }

    private fun itemRow(item: DetailsItem, now: Long): View {
        val color = if (item.state.urgent) accent else fg
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { openItem(item) }
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(
            text((if (item.state.urgent) "● " else "") + item.title, 16f, color, bold = item.state.urgent),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        DetailsText.relativeTime(item.updatedAt, now).takeIf { it.isNotEmpty() }?.let { top.addView(text(it, 12f, secondary)) }
        row.addView(top)
        item.subtitle?.let { row.addView(text(it, 13f, if (item.state.urgent) accent else secondary)) }
        item.progress?.let { p ->
            row.addView(android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = p
                progressTintList = android.content.res.ColorStateList.valueOf(color)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(4) })
        }
        return row
    }

    private fun openItem(item: DetailsItem) {
        val link = item.deepLink
        if (link == null) return openApp()
        try {
            val intent = Intent.parseUri(link, Intent.URI_INTENT_SCHEME).apply {
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            dismissThen { context.startActivity(intent) }
        } catch (e: Exception) {
            openApp()
        }
    }

    private fun openApp() = dismissThen {
        try {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val component = launcher.getActivityList(packageName, user).firstOrNull()?.componentName
            if (component != null) launcher.startMainActivity(component, user, null, null)
            else context.startActivity(
                context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            context.showToast(context.getString(R.string.unable_to_open_app))
        }
    }

    private fun startShortcut(sc: ShortcutInfo) = dismissThen {
        try {
            (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc, null, null)
        } catch (e: Exception) {
            context.showToast(context.getString(R.string.unable_to_open_shortcut))
        }
    }

    private fun loadShortcuts(): List<ShortcutInfo> = try {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val q = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcher.getShortcuts(q, user)?.filter { it.isEnabled }?.sortedBy { it.rank } ?: emptyList()
    } catch (_: Exception) {
        emptyList() // not the default launcher, or the profile is locked
    }

    private fun dismissThen(block: () -> Unit) {
        dialog.dismiss()
        block()
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun note(s: String) = text(s, 14f, secondary).apply { setPadding(0, dp(8), 0, dp(4)) }

    private fun action(s: String, onClick: () -> Unit) = text(s, 16f, fg).apply {
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { onClick() }
    }

    private fun spacer(dp: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(dp)); setBackgroundColor(Color.TRANSPARENT) }

    companion object {
        const val CONDUCTORE_PACKAGE = "com.outsmartis.conductore"
        private val io = Executors.newSingleThreadExecutor()

        fun unwrap(context: Context): Context =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }.firstOrNull { it is Activity } ?: context

        /** [extraActions] are shown under the standard footer; each dismisses the sheet first. */
        fun show(context: Context, packageName: String, user: UserHandle, label: String, extraActions: List<SheetAction> = emptyList()) {
            val ctx = unwrap(context)
            DetailsSheet(ctx, packageName, user, label, extraActions).show()
        }

        /** Gesture entry point: the Conductore sheet, or a toast when it is not installed. */
        fun showConductore(context: Context) {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val user = android.os.Process.myUserHandle()
            val activity = launcher.getActivityList(CONDUCTORE_PACKAGE, user).firstOrNull()
            if (activity == null) {
                context.showToast(context.getString(R.string.details_conductore_missing))
                return
            }
            show(context, CONDUCTORE_PACKAGE, user, activity.label.toString())
        }
    }
}
