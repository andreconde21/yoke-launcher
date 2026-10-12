package com.outsmartis.yoke.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.assist.Assistant
import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.createDialog

/**
 * Settings → Long-press power: Off or one of [Assistant.ACTIONS]. Turning it on offers Yoke as
 * an assistant; the action opens the system screen where the user actually picks it.
 */
object AssistantDialog {

    fun summary(context: Context): String {
        val action = Assistant.action(context) ?: return context.getString(R.string.off)
        return if (Assistant.isAssistant(context)) label(context, action) else context.getString(R.string.assistant_not_picked)
    }

    fun label(context: Context, action: GestureAction): String = when (action) {
        GestureAction.CockpitVoiceAdd -> context.getString(R.string.assistant_voice_add)
        GestureAction.CockpitQuickAdd -> context.getString(R.string.assistant_quick_add)
        GestureAction.CommandPalette -> context.getString(R.string.assistant_palette)
        GestureAction.CockpitToday -> context.getString(R.string.assistant_today)
        GestureAction.ConductoreSheet -> context.getString(R.string.assistant_conductore)
        else -> action.label
    }

    fun show(context: Context, onChanged: () -> Unit): YokeDialog {
        var choice: GestureAction? = Assistant.action(context)
        val d = context.createDialog(
            R.string.assistant_title, R.string.assistant_open_default_apps,
            onAction = { openDefaultApps(context) },
        ) {
            val density = context.resources.displayMetrics.density
            val pad = (10 * density).toInt()
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            root.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                setText(R.string.assistant_message)
                alpha = 0.7f
                setPadding(0, 0, 0, pad)
            })
            root.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                setText(if (Assistant.isAssistant(context)) R.string.assistant_is_yoke else R.string.assistant_is_other)
                setPadding(0, 0, 0, pad)
            })
            val rows = mutableListOf<Pair<TextView, GestureAction?>>()
            fun render() = rows.forEach { (tv, a) ->
                val name = a?.let { label(context, it) } ?: context.getString(R.string.off)
                tv.text = (if (a == choice) "(•) " else "( ) ") + name
            }
            (listOf<GestureAction?>(null) + Assistant.ACTIONS).forEach { a ->
                val tv = TextView(context, null, 0, R.style.TextSmall).apply {
                    setPadding(0, pad, 0, pad)
                    setOnClickListener {
                        choice = a
                        // Saved on every tap, like Yoke's other pickers.
                        Assistant.setAction(context, a)
                        render()
                        onChanged()
                    }
                }
                rows += tv to a
                root.addView(tv)
            }
            render()
            root
        }
        d.showRespectingStatusBar()
        return d
    }

    /** Default apps → Digital assistant app. Samsung also needs Side button → Digital assistant. */
    private fun openDefaultApps(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
    }
}
