package com.outsmartis.yoke.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.phone.PhoneRole

/**
 * Settings → Use Yoke as phone app. Shows who really holds the phone role. On asks the system
 * for the role ([onUse], which needs the fragment's result launchers); Switch back opens Default
 * apps, since only the user can hand the role to another phone app.
 */
object PhoneRoleDialog {

    fun summary(context: Context): String {
        val held = PhoneRole.isRoleHeld(context)
        val wanted = PhoneRole.wanted(context)
        return context.getString(
            when {
                held && wanted -> R.string.on
                held -> R.string.phone_switching
                wanted -> R.string.phone_not_picked
                else -> R.string.off
            }
        )
    }

    fun show(context: Context, onUse: () -> Unit, onChanged: () -> Unit): YokeDialog {
        val held = PhoneRole.isRoleHeld(context)
        val wanted = PhoneRole.wanted(context)
        val d = context.createDialog(
            R.string.phone_title,
            if (held) R.string.phone_switch_back else R.string.phone_use,
            neutral = if (wanted && !held) R.string.phone_turn_off else 0,
            onNeutral = {
                PhoneRole.setWanted(context, false)
                onChanged()
            },
            onAction = {
                if (held) {
                    PhoneRole.setWanted(context, false)
                    PhoneRole.openDefaultApps(context)
                    onChanged()
                } else {
                    onUse()
                }
            },
        ) {
            val pad = (10 * context.resources.displayMetrics.density).toInt()
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            root.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                setText(R.string.phone_message)
                alpha = 0.7f
                setPadding(0, 0, 0, pad)
            })
            root.addView(TextView(context, null, 0, R.style.TextSmall).apply {
                text = when {
                    held -> context.getString(R.string.phone_status_yoke)
                    else -> PhoneRole.holderLabel(context)?.let { context.getString(R.string.phone_status_other, it) }
                        ?: context.getString(R.string.phone_status_none)
                }
                setPadding(0, 0, 0, pad)
            })
            root
        }
        d.showRespectingStatusBar()
        return d
    }
}
