package com.outsmartis.yoke.details

import android.content.Context
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.theme.ThemeStore
import java.util.concurrent.Executors

/**
 * Fills the home screen's status dot row: loads each picked app's `/summary` off the main thread,
 * draws "● 2" in the level's colour, and redraws when a provider reports a change while home is
 * shown ([start]/[stop] follow the fragment). A tap opens that app's details sheet.
 */
class HomeStatusDots(private val context: Context, private val row: LinearLayout) {

    private val client = AppDetailsClient(context)
    private val main = Handler(Looper.getMainLooper())
    private val observers = mutableListOf<ContentObserver>()
    private var seq = 0
    private var started = false

    /** Loads and draws now; call from onResume. Also starts watching the providers. */
    fun start() {
        started = true
        refresh()
    }

    fun stop() {
        started = false
        seq++
        observers.forEach { client.unobserve(it) }
        observers.clear()
    }

    fun refresh() {
        val prefs = StatusDotPrefs(context)
        val apps = prefs.apps
        val quiet = prefs.onlyWhenSomethingIsWrong
        val mySeq = ++seq
        if (apps.isEmpty()) {
            row.removeAllViews()
            row.isVisible = false
            return
        }
        io.execute {
            val dots = apps.mapNotNull { pkg ->
                val authority = client.authorityOf(pkg) ?: return@mapNotNull null
                val summary = (client.loadSummary(authority) as? AppDetailsClient.SummaryResult.Ok)?.summary
                Triple(pkg, authority, StatusDotLogic.dot(summary))
            }
            main.post {
                if (mySeq != seq) return@post
                draw(dots.filter { StatusDotLogic.visible(it.third, quiet) })
                if (started && observers.isEmpty()) {
                    dots.forEach { (_, authority, _) -> observers += client.observe(authority) { if (started) refresh() } }
                }
            }
        }
    }

    private fun draw(dots: List<Triple<String, String, Dot>>) {
        row.removeAllViews()
        row.isVisible = dots.isNotEmpty()
        val theme = ThemeStore.current(context)
        val fg = theme?.foreground ?: context.getColorFromAttr(R.attr.primaryColor)
        val secondary = theme?.secondary ?: context.getColorFromAttr(R.attr.primaryColorTrans50)
        val size = context.resources.getDimension(R.dimen.date_size)
        val pad = (6 * context.resources.displayMetrics.density).toInt()
        for ((pkg, _, dot) in dots) {
            val color = when (dot.level) {
                DotLevel.Alert -> ALERT
                DotLevel.Warn -> theme?.attention ?: WARN
                DotLevel.Ok -> theme?.success ?: OK
                DotLevel.Stale -> secondary
            }
            val text = SpannableStringBuilder(StatusDotLogic.text(dot)).apply {
                setSpan(ForegroundColorSpan(color), 0, 1, 0)
                if (length > 1) setSpan(ForegroundColorSpan(fg), 1, length, 0)
            }
            row.addView(TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                setPadding(if (row.childCount == 0) pad / 2 else pad, pad, pad, pad)
                val label = labelOf(pkg)
                contentDescription = label + (dot.count?.let { ": $it" } ?: "")
                setOnClickListener { DetailsSheet.show(context, pkg, Process.myUserHandle(), label) }
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun labelOf(pkg: String): String = try {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcher.getActivityList(pkg, Process.myUserHandle()).firstOrNull()?.label?.toString() ?: pkg
    } catch (_: Exception) {
        pkg
    }

    companion object {
        /** Red like a desktop bar's alert dot; the themes carry no red of their own. */
        private const val ALERT = 0xFFE5534B.toInt()
        private const val WARN = 0xFFE69875.toInt()
        private const val OK = 0xFFA7C080.toInt()
        private val io = Executors.newSingleThreadExecutor()

        /** Installed apps that serve the details contract, for the Settings picker: package to label. */
        fun candidates(context: Context): List<Pair<String, String>> {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val client = AppDetailsClient(context)
            return launcher.getActivityList(null, Process.myUserHandle())
                .map { it.applicationInfo.packageName to it.label.toString() }
                .distinctBy { it.first }
                .filter { client.authorityOf(it.first) != null }
                .sortedBy { it.second.lowercase() }
        }
    }
}
