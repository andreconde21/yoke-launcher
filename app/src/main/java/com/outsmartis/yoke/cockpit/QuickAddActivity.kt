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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.app.DatePickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    /** Due date for the new card; today unless the user picks otherwise, null for "No date". */
    private var due: LocalDate? = null
    private var dueChoice = DueChoice.NONE
    /** The label picked in the label row; null is "No label" (the default). */
    private var selectedLabel: String? = null
    private var defaultChipText: ColorStateList? = null

    private val pickVault = 1

    private var theme: YokeTheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Cold start from a share: the launcher activity has not set the night mode yet.
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        binding = ActivityQuickAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Android 15+ draws edge to edge and ignores adjustResize, so the sheet keeps itself
        // above the keyboard and the navigation bar from the window insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        theme = ThemeStore.current(this)
        theme?.let { binding.card.setBackgroundColor(it.surface) }
        ThemeApplier.apply(binding.card)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        prefs = CockpitPrefs(this)

        binding.root.setOnClickListener { finish() }
        binding.card.setOnClickListener { }
        binding.openBoard.setTextColor(theme?.secondary ?: binding.openBoard.currentTextColor)
        binding.openBoard.setOnClickListener { CockpitLinks.openBoard(this); finish() }
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
        binding.datesScroll.visibility = View.GONE
        binding.columnsScroll.visibility = View.GONE
        binding.labelsScroll.visibility = View.GONE
    }

    private fun showBoard(cfg: CockpitBoardConfig) {
        config = cfg
        binding.setup.visibility = View.GONE
        binding.input.visibility = View.VISIBLE
        binding.datesScroll.visibility = View.VISIBLE
        binding.columnsScroll.visibility = View.VISIBLE

        binding.dates.removeAllViews()
        DueChoice.entries.forEach { choice ->
            addChip(binding.dates, dueLabel(choice), choice) { pickDue(choice) }
        }
        renderDates()

        // Every column is offered. A date column (Today / Soon / Scheduled) and the date row
        // stay in step: picking one sets the other, so a card never says two things.
        binding.columns.removeAllViews()
        cfg.columns.forEach { column -> addChip(binding.columns, column.label, column) { select(column, fromUser = true) } }
        CockpitCards.defaultColumn(cfg.columns)?.let { select(it) }

        renderLabels(cfg)
        scanLabels(cfg)
        binding.input.requestFocus()
    }

    /** "No label" plus every label the board knows: custom colours, column rules, and labels seen on cards. */
    private fun renderLabels(cfg: CockpitBoardConfig) {
        val known = (prefs.knownLabels + cfg.labelColors.keys + cfg.columns.mapNotNull { CockpitCards.columnLabel(it) })
            .filter { it.isNotBlank() }.toSortedSet(String.CASE_INSENSITIVE_ORDER)
        binding.labelsScroll.visibility = if (known.isEmpty()) View.GONE else View.VISIBLE
        binding.labels.removeAllViews()
        addChip(binding.labels, getString(R.string.quick_add_no_label), NO_LABEL) { pickLabel(null) }
        known.forEach { label -> addChip(binding.labels, label, label) { pickLabel(label) } }
        if (selectedLabel != null && selectedLabel !in known) selectedLabel = null
        styleLabels()
    }

    private fun pickLabel(label: String?) {
        selectedLabel = label
        styleLabels()
    }

    private fun styleLabels() {
        val cfg = config
        val accent = selectedLabel?.let { cfg?.labelColors?.get(it) }
            ?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: theme?.accent ?: Color.GRAY
        styleChips(binding.labels, accent) { it == (selectedLabel ?: NO_LABEL) }
    }

    /** Reads the labels on the board's cards in the background and refreshes the row if they changed. */
    private fun scanLabels(cfg: CockpitBoardConfig) {
        val vault = prefs.vault(this) ?: return
        io.execute {
            val found = runCatching {
                vault.readHeads(cfg.folder).flatMap { CockpitCards.parseLabels(it) }.toSet()
            }.getOrNull() ?: return@execute
            if (found == prefs.knownLabels) return@execute
            prefs.knownLabels = found
            runOnUiThread { if (!isDestroyed && config == cfg) renderLabels(cfg) }
        }
    }

    private fun addChip(row: android.widget.LinearLayout, label: String, tag: Any, onClick: () -> Unit) {
        val chip = layoutInflater.inflate(R.layout.item_quick_add_column, row, false) as TextView
        chip.text = label
        chip.tag = tag
        chip.setOnClickListener { onClick() }
        ThemeApplier.apply(chip)
        if (defaultChipText == null) defaultChipText = chip.textColors
        row.addView(chip)
    }

    private fun dueLabel(choice: DueChoice): String = when (choice) {
        DueChoice.TODAY -> getString(R.string.quick_add_today)
        DueChoice.TOMORROW -> getString(R.string.quick_add_tomorrow)
        DueChoice.CUSTOM -> if (dueChoice == DueChoice.CUSTOM && due != null)
            due!!.format(DateTimeFormatter.ofPattern("EEE d MMM")) else getString(R.string.quick_add_pick_date)
        DueChoice.NONE -> getString(R.string.quick_add_no_date)
    }

    private fun pickDue(choice: DueChoice) {
        val today = LocalDate.now()
        when (choice) {
            DueChoice.TODAY -> setDue(choice, today)
            DueChoice.TOMORROW -> setDue(choice, today.plusDays(1))
            DueChoice.NONE -> setDue(choice, null)
            DueChoice.CUSTOM -> {
                val start = due ?: today
                DatePickerDialog(this, { _, y, m, d -> setDue(DueChoice.CUSTOM, LocalDate.of(y, m + 1, d)) },
                    start.year, start.monthValue - 1, start.dayOfMonth).show()
            }
        }
    }

    private fun setDue(choice: DueChoice, date: LocalDate?) {
        dueChoice = choice
        due = date
        renderDates()
        // A date column follows the date; "No date" falls back to the default (Pending) column.
        val cfg = config ?: return
        val current = selected ?: return
        if (!CockpitCards.isDateColumn(current)) return
        val target = if (date == null) CockpitCards.defaultColumn(cfg.columns)
        else CockpitCards.columnForDue(cfg.columns, date, LocalDate.now())
        target?.let { select(it) }
    }

    private fun renderDates() {
        for (i in 0 until binding.dates.childCount) {
            val chip = binding.dates.getChildAt(i) as TextView
            chip.text = dueLabel(chip.tag as DueChoice)
        }
        styleChips(binding.dates, theme?.accent ?: Color.GRAY) { it == dueChoice }
    }

    private fun select(column: CockpitColumn, fromUser: Boolean = false) {
        selected = column
        if (fromUser) {
            // Tapping a date column picks its date.
            val today = LocalDate.now()
            val rule = column.rule.orEmpty()
            when {
                rule.contains("date:today") -> { dueChoice = DueChoice.TODAY; due = today; renderDates() }
                rule.contains("date:tomorrow") -> { dueChoice = DueChoice.TOMORROW; due = today.plusDays(1); renderDates() }
                rule.contains("date:future") && (due == null || !due!!.isAfter(today.plusDays(1))) -> pickDue(DueChoice.CUSTOM)
            }
        }
        // A column's own colour wins; without one the theme's accent stands in for the old grey.
        val accent = runCatching { Color.parseColor(column.color) }.getOrNull()
            ?: theme?.accent ?: Color.GRAY
        styleChips(binding.columns, accent) { it == column }
    }

    private fun styleChips(row: android.widget.LinearLayout, accent: Int, isOn: (Any?) -> Boolean) {
        val onAccent = if (theme != null) com.outsmartis.yoke.theme.ThemeContrast.onColor(accent) else Color.WHITE
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as TextView
            val on = isOn(chip.tag)
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
        val pickedDue = due
        val pickedLabels = listOfNotNull(selectedLabel)
        val savedTo = if (pickedDue != null) dueLabel(dueChoice) else column.label
        io.execute {
            val today = LocalDate.now()
            val result = runCatching {
                val path = CockpitCards.pathFor(cfg.folder, title) { vault.exists(it) }
                val fields = CockpitCards.withDue(CockpitCards.fieldsFor(column, today), pickedDue)
                vault.createText(path, CockpitCards.content(title, fields, today, pickedLabels))
            }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, getString(R.string.quick_add_saved, savedTo), Toast.LENGTH_SHORT).show()
                    CockpitAgendaRepository.refreshIfNeeded(applicationContext, force = true)
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

/** The date chips, in display order. */
private enum class DueChoice { TODAY, TOMORROW, CUSTOM, NONE }

/** Tag of the "No label" chip. */
private const val NO_LABEL = "\u0000no-label"
