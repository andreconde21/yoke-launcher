package com.outsmartis.yoke.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.system.ShizukuAccess

/** Settings → Connections → Shizuku: what it adds, and the next step to set it up. */
object ShizukuDialog {

    fun statusText(context: Context): Int = when (ShizukuAccess.state(context)) {
        ShizukuAccess.State.NotInstalled -> R.string.shizuku_not_installed
        ShizukuAccess.State.NotRunning -> R.string.shizuku_not_running
        ShizukuAccess.State.NeedsPermission -> R.string.shizuku_needs_permission
        ShizukuAccess.State.Ready -> R.string.shizuku_ready
    }

    fun show(context: Context, onChanged: () -> Unit): YokeDialog {
        val state = ShizukuAccess.state(context)
        val action = when (state) {
            ShizukuAccess.State.NotInstalled -> R.string.shizuku_get
            ShizukuAccess.State.NotRunning -> R.string.shizuku_open
            ShizukuAccess.State.NeedsPermission -> R.string.shizuku_allow
            ShizukuAccess.State.Ready -> R.string.okay
        }
        val d = context.createDialog(R.string.shizuku_title, action, message = R.string.shizuku_message, onAction = {
            when (state) {
                ShizukuAccess.State.NotInstalled -> openListing(context)
                ShizukuAccess.State.NotRunning ->
                    context.packageManager.getLaunchIntentForPackage(ShizukuAccess.PACKAGE)?.let { context.startActivity(it) }
                ShizukuAccess.State.NeedsPermission -> ShizukuAccess.requestPermission { onChanged() }
                ShizukuAccess.State.Ready -> Unit
            }
        })
        d.showRespectingStatusBar()
        return d
    }

    private fun openListing(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuAccess.PACKAGE}")))
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        }
    }
}
