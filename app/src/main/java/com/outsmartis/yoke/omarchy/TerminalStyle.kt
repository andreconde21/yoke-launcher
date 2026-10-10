package com.outsmartis.yoke.omarchy

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.TextView

/** Terminal mode applied to views: home labels and the drawer search's block cursor. */
object TerminalStyle {

    /** A home app label as terminal mode shows it (unchanged when the mode is off). */
    fun homeLabel(context: Context, name: String): String {
        val p = OmarchyStylePrefs(context)
        if (!p.terminalEnabled) return name
        return TerminalLabel.format(name, if (p.terminalPrompt) TerminalLabel.prompt(p.terminalPromptText) else null, p.terminalLowercase)
    }

    /** A blinking block cursor in the accent colour on [field] (Android 10+; older keeps its line cursor). */
    fun applyCursor(field: TextView) {
        val p = OmarchyStylePrefs(field.context)
        if (!p.terminalEnabled || !p.terminalCursor || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        field.textCursorDrawable = GradientDrawable().apply {
            setColor(OmarchyColors.of(field.context).accent)
            setSize(TerminalLabel.cursorWidthPx(field.textSize), 0)
        }
        field.isCursorVisible = true
    }
}
