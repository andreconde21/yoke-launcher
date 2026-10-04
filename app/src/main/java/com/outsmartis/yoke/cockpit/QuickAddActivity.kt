package com.outsmartis.yoke.cockpit

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.databinding.ActivityQuickAddBinding
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import com.outsmartis.yoke.theme.YokeTheme
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Quick-add a card to the Obsidian Cockpit Board: type, pick a column,
 * Enter. The card is a new file in the board's folder, in the format the
 * plugin itself writes, so Syncthing carries it to the desktop vault and
 * the board picks it up like a card made in Obsidian.
 *
 * Opened from the home-screen gesture, and as a share target for text.
 */
class QuickAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickAddBinding
    private lateinit var prefs: CockpitPrefs
    private val io = Executors.newSingleThreadExecutor()
    private var config: CockpitBoardConfig? = null
    private var selected: CockpitColumn? = null
    private var defaultChipText: ColorStateList? = null

    private val pickVault = 1

    private var theme: YokeTheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Cold start from a share: the launcher activity has not set the night mode yet.
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        binding = ActivityQuickAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        theme = ThemeStore.current(this)
        theme?.let { binding.card.setBackgroundColor(it.surface) }
        ThemeApplier.apply(binding.card)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        prefs = CockpitPrefs(this)

        binding.root.setOnClickListener { finish() }
        binding.card.setOnClickListener { }
        binding.pickVault.setOnClickListener { openVaultPicker() }
        binding.input.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (enter) save()
            enter
        }
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { binding.input.setText(it.lines().first().trim()) }
        }
        loadBoard()
    }

    private fun loadBoard() {
        val vault = prefs.vault(this)
        if (vault == null) {
            showSetup()
            return
        }
        io.execute {
            val result = runCatching {
                vault.readText(VaultAccess.SETTINGS_PATH)?.let { CockpitBoardConfig.parse(it) }
                    ?: CockpitBoardConfig("", CockpitBoardConfig.DEFAULT_COLUMNS)
            }
            runOnUiThread {
                result.onSuccess { showBoard(it) }.onFailure {
                    // Permission revoked or the folder moved: ask again.
                    showSetup()
                }
            }
        }
    }

    private fun showSetup() {
        binding.setup.visibility = View.VISIBLE
        binding.input.visibility = View.GONE
        binding.columns.visibility = View.GONE
    }

    private fun showBoard(cfg: CockpitBoardConfig) {
        config = cfg
        binding.setup.visibility = View.GONE
        binding.input.visibility = View.VISIBLE
        binding.columns.visibility = View.VISIBLE
        binding.columns.removeAllViews()
        val preferred = cfg.columns.firstOrNull { it.id == prefs.defaultColumn } ?: cfg.columns.first()
        cfg.columns.filter { it.rule?.contains("status:done") != true }.forEach { column ->
            val chip = layoutInflater.inflate(R.layout.item_quick_add_column, binding.columns, false) as TextView
            chip.text = column.label
            chip.tag = column
            chip.setOnClickListener { select(column) }
            ThemeApplier.apply(chip)
            if (defaultChipText == null) defaultChipText = chip.textColors
            binding.columns.addView(chip)
        }
        select(preferred)
        binding.input.requestFocus()
    }

    private fun select(column: CockpitColumn) {
        selected = column
        // A column's own colour wins; without one the theme's accent stands in for the old grey.
        val accent = runCatching { Color.parseColor(column.color) }.getOrNull()
            ?: theme?.accent ?: Color.GRAY
        val onAccent = if (theme != null) com.outsmartis.yoke.theme.ThemeContrast.onColor(accent) else Color.WHITE
        for (i in 0 until binding.columns.childCount) {
            val chip = binding.columns.getChildAt(i) as TextView
            val on = chip.tag == column
            chip.isSelected = on
            val t = theme
            if (t != null) {
                chip.background = if (on) ThemeApplier.roundedRect(this, 16f, accent)
                else ThemeApplier.roundedRect(this, 16f, Color.TRANSPARENT, t.secondary)
                chip.backgroundTintList = null
            } else {
                chip.setBackgroundResource(if (on) R.drawable.bg_quick_add_chip_on else R.drawable.bg_quick_add_chip)
                chip.backgroundTintList = if (on) ColorStateList.valueOf(accent) else null
            }
            if (on) chip.setTextColor(onAccent) else chip.setTextColor(defaultChipText)
        }
    }

    private fun save() {
        val title = binding.input.text.toString().trim()
        val cfg = config ?: return
        val column = selected ?: return
        if (title.isEmpty()) return
        val vault = prefs.vault(this) ?: return showSetup()
        binding.input.isEnabled = false
        io.execute {
            val today = LocalDate.now()
            val result = runCatching {
                val path = CockpitCards.pathFor(cfg.folder, title) { vault.exists(it) }
                vault.createText(path, CockpitCards.content(title, CockpitCards.fieldsFor(column, today), today))
            }
            runOnUiThread {
                result.onSuccess {
                    prefs.defaultColumn = column.id
                    Toast.makeText(this, getString(R.string.quick_add_saved, column.label), Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure {
                    binding.input.isEnabled = true
                    Toast.makeText(this, it.message ?: getString(R.string.quick_add_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openVaultPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), pickVault)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != pickVault || resultCode != Activity.RESULT_OK) return
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.vaultUri = uri.toString()
        loadBoard()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
