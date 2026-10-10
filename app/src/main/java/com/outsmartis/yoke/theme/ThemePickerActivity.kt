package com.outsmartis.yoke.theme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.helper.getColorFromAttr

/**
 * Lists System default, every Omarchy theme and "Follow my Omarchy PC", each in
 * its own colours (name plus background / foreground / accent swatches).
 * Tapping a row applies it immediately.
 *
 * Entry point for the settings row and for the gesture system's "ThemePicker"
 * action: [open].
 */
class ThemePickerActivity : AppCompatActivity() {

    private var observer: ThemeStore.Registration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        val resolved = ThemeStore.resolved(this)
        val theme = resolved.theme
        val dp = resources.displayMetrics.density

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (32 * dp).toInt())
        }
        val title = TextView(this).apply {
            setText(R.string.theme_picker_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(getColorFromAttr(R.attr.primaryColor))
            setPadding((8 * dp).toInt(), 0, 0, (12 * dp).toInt())
        }
        list.addView(title)

        val conductore = ConductoreTheme.read(this)
        val conductoreTheme = (conductore as? ConductoreTheme.Read.Available)?.theme?.let(YokeTheme::from)
            ?: if (resolved.id == YokeTheme.PC_ID) theme else null

        list.addView(row(YokeTheme.SYSTEM_ID, getString(R.string.theme_system_default), null, resolved.id))
        ThemeCatalog.omarchy.values.forEach { list.addView(row(it.id, it.name, it, resolved.id)) }
        val conductoreLabel = if (conductore is ConductoreTheme.Read.Available) {
            val synced = if (conductore.updatedAt > 0) {
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    conductore.updatedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            } else null
            val detail = listOfNotNull(conductore.machine, synced?.let { "synced $it" }).joinToString(" \u00b7 ")
            val base = (conductoreTheme?.name?.let { "${getString(R.string.theme_follow_pc)}: $it" }
                ?: getString(R.string.theme_follow_pc))
            if (detail.isEmpty()) base else "$base\n$detail"
        } else getString(R.string.theme_follow_pc_unavailable)
        list.addView(row(YokeTheme.PC_ID, conductoreLabel, conductoreTheme, resolved.id))

        val wallpaperLink = TextView(this).apply {
            setText(R.string.theme_picker_wallpaper)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(theme?.accentText ?: getColorFromAttr(R.attr.primaryColor))
            setPadding((8 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            isClickable = true
            setOnClickListener { com.outsmartis.yoke.wallpaper.WallpaperActivity.open(this@ThemePickerActivity) }
        }
        list.addView(wallpaperLink)

        val scroll = ScrollView(this).apply {
            id = R.id.themePickerScroll
            isFillViewport = true
            addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            fitsSystemWindows = true
            setBackgroundColor(theme?.background ?: getColorFromAttr(R.attr.primaryColorInverseTrans90))
        }
        setContentView(scroll)
        ThemeApplier.applyWindow(this)
        // Only the title takes the theme here; rows carry their own palette.
        theme?.let { title.setTextColor(it.foreground) }
        ThemeApplier.fonts(this)?.let { f ->
            fun font(v: View) {
                if (v is TextView) v.typeface = f.regular
                if (v is ViewGroup) for (i in 0 until v.childCount) font(v.getChildAt(i))
            }
            font(list)
        }
        observer = ThemeStore.observeConductore(this) { recreate() }
    }

    private fun row(id: String, label: String, preview: YokeTheme?, selectedId: String): View {
        val dp = resources.displayMetrics.density
        val selected = id == selectedId
        // Rows are drawn in their own theme; System default (no palette) uses the launcher's light/dark colours.
        val bg = preview?.background ?: getColorFromAttr(R.attr.primaryInverseColor)
        val fg = preview?.foreground ?: getColorFromAttr(R.attr.primaryColor)
        val accent = preview?.accent ?: fg
        val border = if (selected) preview?.accentText ?: fg else preview?.muted ?: getColorFromAttr(R.attr.primaryColorTrans50)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = ThemeApplier.roundedRect(context, 14f, bg, border).also {
                if (selected) it.setStroke((3 * dp).toInt(), border)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (6 * dp).toInt() }
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(TextView(context).apply {
                text = if (selected) "$label  ✓" else label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(fg)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            listOf(bg, fg, accent).forEach { addView(swatch(it, fg)) }
            setOnClickListener {
                ThemeStore.setThemeId(this@ThemePickerActivity, id)
                setResult(Activity.RESULT_OK)
                recreate()
            }
        }
    }

    private fun swatch(color: Int, outline: Int): View {
        val dp = resources.displayMetrics.density
        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((1 * dp).toInt().coerceAtLeast(1), (outline and 0x00FFFFFF) or (0x66 shl 24))
            }
            layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
            }
        }
    }

    override fun onDestroy() {
        observer?.unregister()
        super.onDestroy()
    }

    companion object {
        /** Opens the picker; safe from any context (adds NEW_TASK when it is not an Activity). */
        fun open(context: Context) {
            val intent = Intent(context, ThemePickerActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
