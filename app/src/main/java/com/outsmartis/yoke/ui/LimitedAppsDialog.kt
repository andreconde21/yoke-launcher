package com.outsmartis.yoke.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.asTextField
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.limits.LimitPrefs
import com.outsmartis.yoke.limits.LimitedApps
import com.outsmartis.yoke.system.ShizukuAccess

/**
 * Settings → Limited apps: tick apps to limit; a limited app shows its hard-pause switch,
 * which works only while Shizuku is ready. Saved on every tap.
 */
object LimitedAppsDialog {

    private class AppEntry(val label: String, val packageName: String)

    fun show(context: Context, onChanged: () -> Unit): YokeDialog {
        val prefs = LimitPrefs(context)
        val shizuku = ShizukuAccess.isReady(context)
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            // Limited apps first, so the list doubles as the overview.
            .sortedWith(compareBy({ it.packageName !in prefs.limited }, { it.label.lowercase() }))
        val d = context.createDialog(R.string.limits_settings_row, R.string.okay, onAction = onChanged) {
            val density = context.resources.displayMetrics.density
            val pad = (10 * density).toInt()
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            // The long hint is folded away: it would squeeze the list to a few rows (none in a wide font).
            val hint = TextView(context, null, 0, R.style.TextSmall).apply {
                setText(R.string.limits_dialog_hint)
                alpha = 0.7f
                setPadding(0, 0, 0, (8 * density).toInt())
                visibility = TextView.GONE
            }
            val more = TextView(context, null, 0, R.style.TextSmall).apply {
                setText(R.string.limits_hint_more)
                alpha = 0.7f
                setPadding(0, 0, 0, (8 * density).toInt())
                setOnClickListener {
                    val show = hint.visibility != TextView.VISIBLE
                    hint.visibility = if (show) TextView.VISIBLE else TextView.GONE
                    setText(if (show) R.string.limits_hint_less else R.string.limits_hint_more)
                }
            }
            val search = EditText(context, null, 0, R.style.TextSmall).asTextField().apply {
                setHint(R.string.notes_apps_search)
                setSingleLine()
            }
            val (scroll, column) = scrollColumn(context)
            fun fill(query: String) {
                column.removeAllViews()
                val q = query.trim().lowercase()
                for (app in apps.filter { q.isEmpty() || it.label.lowercase().contains(q) }) {
                    val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    val name = TextView(context, null, 0, R.style.TextSmall).apply { setPadding(0, pad, 0, pad / 2) }
                    val hard = TextView(context, null, 0, R.style.TextSmall).apply {
                        alpha = 0.7f
                        gravity = Gravity.START
                        setPadding((24 * density).toInt(), 0, 0, pad)
                    }
                    fun render() {
                        val limited = app.packageName in prefs.limited
                        name.text = (if (limited) "[x] " else "[ ] ") + app.label
                        hard.visibility = if (limited) TextView.VISIBLE else TextView.GONE
                        hard.setText(when {
                            app.packageName in prefs.hard -> R.string.limits_hard_on
                            shizuku -> R.string.limits_hard_off
                            else -> R.string.limits_hard_needs_shizuku
                        })
                    }
                    render()
                    name.setOnClickListener {
                        LimitedApps.setLimited(context, app.packageName, app.packageName !in prefs.limited)
                        render()
                        onChanged()
                    }
                    hard.setOnClickListener {
                        val on = app.packageName in prefs.hard
                        // Turning it off always works (it lifts the pause when Shizuku runs); on needs Shizuku.
                        if (!on && !ShizukuAccess.isReady(context)) {
                            context.showToast(context.getString(R.string.limits_hard_needs_shizuku))
                            return@setOnClickListener
                        }
                        LimitedApps.setHard(context, app.packageName, !on)
                        render()
                    }
                    row.addView(name)
                    row.addView(hard)
                    column.addView(row)
                }
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = fill(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            fill("")
            root.addView(more)
            root.addView(hint)
            root.addView(search)
            root.addView(scroll)
            root
        }
        d.autoShowKeyboard = false
        d.showRespectingStatusBar()
        return d
    }
}
