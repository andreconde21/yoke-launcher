package com.outsmartis.yoke.wallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import com.outsmartis.yoke.theme.YokeTheme
import java.util.concurrent.Executors

/**
 * Wallpaper screen: source (theme colour / Omarchy background / your image),
 * target (home / lock / both) and dimming. Built in code like the theme picker.
 */
class WallpaperActivity : AppCompatActivity() {

    private lateinit var prefs: WallpaperPrefs
    private lateinit var content: LinearLayout
    private var fg = 0
    private var secondary = 0
    private var accent = 0
    private val loader = Executors.newFixedThreadPool(2)

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            toast(R.string.wallpaper_applying)
            WallpaperApplier.applyPicked(this, uri) { finished(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        prefs = WallpaperPrefs(this)
        val theme = ThemeStore.current(this)
        fg = theme?.foreground ?: getColorFromAttr(R.attr.primaryColor)
        secondary = theme?.secondary ?: getColorFromAttr(R.attr.primaryColorTrans50)
        accent = theme?.accentText ?: fg
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, dp(8), p, dp(32))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            fitsSystemWindows = true
            setBackgroundColor(theme?.background ?: getColorFromAttr(R.attr.primaryColorInverseTrans90))
        }
        setContentView(scroll)
        ThemeApplier.applyWindow(this)
        build()
    }

    private fun build() {
        content.removeAllViews()
        val choice = prefs.choice
        content.addView(text(getString(R.string.wallpaper_title), 28f, fg).apply { setPadding(dp(8), 0, 0, dp(4)) })
        content.addView(text(getString(R.string.wallpaper_help), 13f, secondary).apply { setPadding(dp(8), 0, dp(8), dp(12)) })

        content.addView(header(R.string.wallpaper_source))
        listOf(
            WallpaperSource.THEME_COLOUR to R.string.wallpaper_source_colour,
            WallpaperSource.OMARCHY to R.string.wallpaper_source_omarchy,
            WallpaperSource.IMAGE to R.string.wallpaper_source_image,
        ).forEach { (source, label) ->
            content.addView(row(getString(label), choice.source == source) {
                selectSource(source)
            })
        }

        when (choice.source) {
            WallpaperSource.OMARCHY -> content.addView(omarchySection(choice))
            WallpaperSource.IMAGE -> content.addView(imageSection())
            WallpaperSource.THEME_COLOUR -> Unit
        }

        content.addView(header(R.string.wallpaper_target))
        content.addView(chips(
            listOf(R.string.wallpaper_target_home, R.string.wallpaper_target_lock, R.string.wallpaper_target_both),
            WallpaperTarget.entries.indexOf(choice.target),
        ) { i ->
            prefs.choice = prefs.choice.copy(target = WallpaperTarget.entries[i])
            if (prefs.applied) {
                toast(R.string.wallpaper_applying)
                WallpaperApplier.reapply(this) { finished(it) }
            }
            build()
        })

        content.addView(header(R.string.wallpaper_dim))
        content.addView(chips(
            listOf(R.string.wallpaper_dim_off, R.string.wallpaper_dim_light, R.string.wallpaper_dim_strong),
            DimLevel.entries.indexOf(choice.dim),
        ) { i ->
            prefs.choice = prefs.choice.copy(dim = DimLevel.entries[i])
            build()
        })
        content.addView(text(getString(R.string.wallpaper_dim_help), 12f, secondary).apply { setPadding(dp(8), dp(6), dp(8), 0) })
    }

    private fun selectSource(source: WallpaperSource) {
        prefs.choice = prefs.choice.copy(source = source)
        when (source) {
            WallpaperSource.THEME_COLOUR -> {
                toast(R.string.wallpaper_applying)
                WallpaperApplier.applyThemeColour(this) { finished(it) }
            }
            WallpaperSource.IMAGE -> if (WallpaperApplier.hasOwnImage(this)) {
                toast(R.string.wallpaper_applying)
                WallpaperApplier.reapply(this) { finished(it) }
            }
            WallpaperSource.OMARCHY -> Unit
        }
        // Not "applied" as an image until a picture is chosen; the grid / button below does that
        build()
    }

    private fun omarchySection(choice: WallpaperChoice): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val themeId = ThemeStore.resolved(this).id
        val files = WallpaperLogic.backgroundsFor(themeId)
        box.addView(header(R.string.wallpaper_pick))
        if (files.isEmpty()) {
            val msg = if (themeId == YokeTheme.SYSTEM_ID || themeId == YokeTheme.PC_ID)
                R.string.wallpaper_no_omarchy_theme else R.string.wallpaper_no_backgrounds
            box.addView(text(getString(msg), 15f, secondary).apply { setPadding(dp(8), dp(4), dp(8), dp(8)) })
            return box
        }
        val grid = GridLayout(this).apply { columnCount = 2 }
        val width = (resources.displayMetrics.widthPixels - dp(32) - dp(24)) / 2
        files.forEach { file ->
            val selected = prefs.applied && choice.source == WallpaperSource.OMARCHY &&
                choice.omarchyTheme == themeId && choice.omarchyFile == file
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.argb(40, 128, 128, 128))
                contentDescription = file
                background = ThemeApplier.roundedRect(context, 10f, Color.argb(40, 128, 128, 128),
                    if (selected) accent else secondary).also {
                    if (selected) it.setStroke(dp(3), accent)
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                layoutParams = GridLayout.LayoutParams().apply {
                    this.width = width
                    height = width * 16 / 9
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnClickListener {
                    prefs.choice = prefs.choice.copy(
                        source = WallpaperSource.OMARCHY, omarchyTheme = themeId, omarchyFile = file,
                    )
                    toast(R.string.wallpaper_downloading)
                    WallpaperApplier.applyOmarchy(this@WallpaperActivity, themeId, file) { finished(it) }
                }
            }
            grid.addView(image)
            loader.execute {
                val thumb = WallpaperApplier.thumbnail(this, themeId, file)
                val bmp = thumb?.let { BitmapFactory.decodeFile(it.path) }
                if (bmp != null) runOnUiThread { if (!isDestroyed) image.setImageBitmap(bmp) }
            }
        }
        box.addView(grid)
        return box
    }

    private fun imageSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(row(getString(R.string.wallpaper_choose_photo), false) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        return box
    }

    private fun finished(error: String?) {
        if (isDestroyed) return
        if (error == null) {
            toast(R.string.wallpaper_done)
            build()
        } else {
            val msg = if (error == "download") getString(R.string.wallpaper_download_failed)
            else getString(R.string.wallpaper_failed, error)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, sp: Float, colour: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(colour)
        ThemeApplier.fonts(context)?.let { typeface = it.regular }
    }

    private fun header(res: Int) = text(getString(res), 13f, secondary).apply {
        setPadding(dp(8), dp(18), 0, dp(4))
        isAllCaps = true
    }

    private fun row(label: String, selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = ThemeApplier.roundedRect(context, 14f, Color.TRANSPARENT, if (selected) accent else secondary)
            .also { if (selected) it.setStroke(dp(3), accent) }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }
        isClickable = true
        isFocusable = true
        addView(text(if (selected) "$label  ✓" else label, 18f, fg))
        setOnClickListener { onClick() }
    }

    private fun chips(labels: List<Int>, selected: Int, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        labels.forEachIndexed { i, label ->
            addView(text(getString(label), 16f, if (i == selected) accent else fg).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = ThemeApplier.roundedRect(context, 12f, Color.TRANSPARENT, if (i == selected) accent else secondary)
                    .also { if (i == selected) it.setStroke(dp(3), accent) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(6) }
                isClickable = true
                setOnClickListener { onPick(i) }
            })
        }
    }

    override fun onDestroy() {
        loader.shutdownNow()
        super.onDestroy()
    }

    companion object {
        /** Opens the Wallpaper screen from any context. */
        fun open(context: Context) {
            val intent = Intent(context, WallpaperActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** Settings row value: what is currently selected. */
        fun summary(context: Context): String {
            val prefs = WallpaperPrefs(context)
            val c = prefs.choice
            return context.getString(
                when (c.source) {
                    WallpaperSource.THEME_COLOUR -> R.string.wallpaper_source_colour
                    WallpaperSource.OMARCHY -> R.string.wallpaper_source_omarchy
                    WallpaperSource.IMAGE -> R.string.wallpaper_source_image
                }
            )
        }
    }
}
