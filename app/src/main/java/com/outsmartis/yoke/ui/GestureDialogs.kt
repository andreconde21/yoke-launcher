package com.outsmartis.yoke.ui

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.outsmartis.yoke.R
import com.outsmartis.yoke.gestures.GestureAction
import com.outsmartis.yoke.gestures.GestureConfig
import com.outsmartis.yoke.helper.createDialog

/** Human readable description of an action, resolving app labels where it can. */
fun GestureAction.describe(context: Context): String = when (this) {
    is GestureAction.OpenApp -> context.appLabel(packageName)
    is GestureAction.OpenShortcut -> "${context.appLabel(packageName)}: $shortcutId"
    is GestureAction.OpenUri -> uri.take(40)
    else -> label
}

private fun Context.appLabel(packageName: String): String = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (e: PackageManager.NameNotFoundException) {
    packageName
}

/** A scrolling column inside a dialog, capped so long lists do not run off the screen. */
fun scrollColumn(context: Context, heightFraction: Float = 0.55f): Pair<ScrollView, LinearLayout> {
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(column)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * heightFraction).toInt()
        )
    }
    return scroll to column
}

/** Lists every bound trigger and what it does. Opened from the Gestures screen or the cheat sheet action. */
fun showGestureCheatSheet(activity: AppCompatActivity, config: GestureConfig) {
    activity.createDialog(R.string.gesture_cheat_sheet, R.string.close) { container ->
        val (scroll, column) = scrollColumn(activity)
        val bound = config.bound()
        if (bound.isEmpty()) {
            column.addView(TextView(activity, null, 0, R.style.TextSmall).apply {
                setText(R.string.gesture_cheat_sheet_empty)
            })
        }
        for ((trigger, action) in bound) {
            val row = LayoutInflater.from(activity).inflate(R.layout.item_gesture_row, column, false)
            row.background = null
            row.findViewById<TextView>(R.id.gestureTrigger).text = trigger.label
            row.findViewById<TextView>(R.id.gestureAction).text = action.describe(activity)
            column.addView(row)
        }
        scroll as View
    }.showRespectingStatusBar()
}
