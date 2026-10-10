package com.outsmartis.yoke.omarchy

import android.content.Context
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.theme.ThemeStore

/** The active theme's colours for the Omarchy style pieces; the launcher's light/dark colours under System default. */
class OmarchyColors(
    val foreground: Int,
    val secondary: Int,
    val accent: Int,
    val surface: Int,
    val background: Int,
) {
    companion object {
        fun of(context: Context): OmarchyColors {
            val t = ThemeStore.current(context)
            if (t != null) return OmarchyColors(t.foreground, t.secondary, t.accentText, t.surface, t.background)
            val fg = context.getColorFromAttr(R.attr.primaryColor)
            val bg = context.getColorFromAttr(R.attr.primaryInverseColor)
            return OmarchyColors(fg, context.getColorFromAttr(R.attr.primaryColorTrans50), fg, bg, bg)
        }
    }
}
