package com.outsmartis.yoke.cockpit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.outsmartis.yoke.R

/** Opens the Cockpit Board plugin's views in Obsidian through its `obsidian://cockpit-board` handler. */
object CockpitLinks {
    const val BOARD_URI = "obsidian://cockpit-board"
    const val CALENDAR_URI = "obsidian://cockpit-board?view=calendar"

    fun openBoard(context: Context) = open(context, BOARD_URI)

    fun openCalendar(context: Context) = open(context, CALENDAR_URI)

    private fun open(context: Context, uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.obsidian_missing, Toast.LENGTH_SHORT).show()
        }
    }
}
