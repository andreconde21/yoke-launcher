package com.outsmartis.yoke.cockpit

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.details.FlowLayout
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Bottom sheet with today's Cockpit cards: Overdue, Today, In progress. A row opens the card
 * in Obsidian; its actions (Done, Start, Tomorrow, Pick date) edit the card's frontmatter in
 * the vault, shown at once and reverted with a toast if the write fails.
 */
class CockpitTodaySheet private constructor(
    private val context: Context,
    private val onChanged: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val dialog = Dialog(context)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val density = context.resources.displayMetrics.density
    private val prefs = CockpitPrefs(context)

    private val theme = ThemeStore.current(context)
    private val fg = theme?.foreground ?: context.getColorFromAttr(R.attr.primaryColor)
    private val secondary = theme?.secondary ?: context.getColorFromAttr(R.attr.primaryColorTrans50)
    private val accent = theme?.accentText ?: fg

    private var cards: List<TodayCard> = emptyList()
    private var cfg = CockpitBoardConfig("", CockpitBoardConfig.DEFAULT_COLUMNS)
    private var vaultName = ""
    private var loaded = false
    private var noVault = false
    private var menuFor: String? = null
    private val pending = mutableSetOf<String>()
    @Volatile private var dismissed = false
    private var loadSeq = 0

    private fun dp(v: Int) = (v * density).toInt()

    private fun show() {
        val scroll = ScrollView(context).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.setPadding(dp(24), dp(20), dp(24), dp(20))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            content.setPadding(dp(24) + bars.left, dp(20), dp(24) + bars.right, dp(20) + maxOf(bars.bottom, ime.bottom))
            insets
        }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(GradientDrawable().apply {
                val r = 24 * density
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(theme?.surface ?: context.getColorFromAttr(R.attr.dialogShadeColor).let { it or (0xFF shl 24) })
            })
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.5f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        dialog.setOnDismissListener { dismissed = true }
        render()
        dialog.show()
        reload()
    }

    /** Lists the cards and reads their heads off the main thread, then renders. */
    private fun reload() {
        val seq = ++loadSeq
        io.execute {
            val vault = prefs.vault(context)
            var config = cfg
            var name = vaultName
            val result = if (vault == null) emptyList() else runCatching {
                config = vault.readText(VaultAccess.SETTINGS_PATH)?.let { CockpitBoardConfig.parse(it) } ?: config
                name = vault.displayName()
                vault.listMarkdown(config.folder).mapNotNull { f ->
                    val head = vault.readHead(f) ?: return@mapNotNull null
                    val h = CockpitAgenda.parseHead(head) ?: return@mapNotNull null
                    TodayCard(f.path, f.id, h.title, h.status, h.due, h.time, CockpitCards.parseLabels(head), CockpitToday.headHash(head))
                }
            }.getOrNull()
            main.post {
                if (dismissed || seq != loadSeq) return@post
                noVault = vault == null
                if (result != null) { cards = result; cfg = config; vaultName = name }
                loaded = true
                render()
            }
        }
    }

    private fun render() {
        content.removeAllViews()
        val today = LocalDate.now()
        val dateText = today.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(context.getString(R.string.today_title, dateText), 18f, fg, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(context.getString(R.string.today_open_board), 14f, secondary).apply {
                setPadding(dp(12), dp(8), 0, dp(8))
                setOnClickListener { dialog.dismiss(); CockpitLinks.openBoard(context) }
            })
        })
        if (noVault) {
            content.addView(note(context.getString(R.string.search_needs_vault)))
        } else if (loaded) {
            val s = CockpitToday.sections(cards, today)
            if (s.isEmpty) {
                content.addView(note(context.getString(R.string.today_empty)))
                content.addView(text(context.getString(R.string.today_add), 16f, fg).apply {
                    setPadding(0, dp(12), 0, dp(10))
                    setOnClickListener {
                        dialog.dismiss()
                        context.startActivity(Intent(context, QuickAddActivity::class.java))
                    }
                })
            } else {
                section(R.string.today_overdue, s.overdue, accent, today)
                section(R.string.today_section_today, s.today, secondary, today)
                section(R.string.today_in_progress, s.inProgress, secondary, today)
            }
        }
        ThemeApplier.fonts(context)?.let { applyFont(content, it) }
    }

    private fun section(titleRes: Int, rows: List<TodayCard>, headColor: Int, today: LocalDate) {
        if (rows.isEmpty()) return
        content.addView(text(context.getString(titleRes), 13f, headColor, bold = true).apply { setPadding(0, dp(18), 0, dp(4)) })
        rows.forEach { content.addView(row(it, today, if (headColor == accent) accent else fg)) }
    }

    private fun row(card: TodayCard, today: LocalDate, titleColor: Int): View {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(card.title, 16f, titleColor))
        secondaryLine(card, today).takeIf { it.isNotEmpty() }?.let { left.addView(text(it, 13f, secondary)) }
        line.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(text("⋯", 20f, secondary).apply {
            setPadding(dp(14), dp(8), 0, dp(8))
            setOnClickListener { toggleMenu(card) }
        })
        line.setPadding(0, dp(8), 0, dp(8))
        line.setOnClickListener { openCard(card) }
        line.setOnLongClickListener { toggleMenu(card); true }
        box.addView(line)
        if (menuFor == card.path) box.addView(menu(card))
        return box
    }

    private fun toggleMenu(card: TodayCard) {
        menuFor = if (menuFor == card.path) null else card.path
        render()
    }

    private fun menu(card: TodayCard): View = FlowLayout(context).apply {
        fun chip(label: Int, onClick: () -> Unit) = addView(text(context.getString(label), 14f, fg).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { cornerRadius = 16 * density; setStroke(dp(1), secondary) }
            setOnClickListener { onClick() }
        }, android.view.ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(4), dp(8), dp(4))
        })
        chip(R.string.today_act_done) { act(card, TodayAction.DONE) }
        chip(R.string.today_act_start) { act(card, TodayAction.START) }
        chip(R.string.today_act_tomorrow) { act(card, TodayAction.TOMORROW) }
        chip(R.string.today_act_pick) { pickDate(card) }
        chip(R.string.today_act_open) { openCard(card) }
    }

    private fun secondaryLine(card: TodayCard, today: LocalDate): String {
        val column = when {
            card.status == "in-progress" -> cfg.columns.firstOrNull { it.rule.orEmpty().contains("status:in-progress") }
            card.due != null -> CockpitCards.columnForDue(cfg.columns, card.due, today)
            else -> null
        }
        val time = if (card.due != null && card.due.isBefore(today)) card.due.toString() + (if (card.time.isNotEmpty()) " ${card.time}" else "") else card.time
        return listOf(time, card.labels.joinToString(" ") { "#$it" }, column?.label.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    private fun pickDate(card: TodayCard) {
        val d = card.due ?: LocalDate.now()
        DatePickerDialog(DetailsSheet.unwrap(context), { _, y, m, day ->
            act(card, TodayAction.PICK_DATE, LocalDate.of(y, m + 1, day))
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    private fun openCard(card: TodayCard) {
        val uri = CockpitToday.obsidianUri(vaultName, card.path)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            dialog.dismiss()
        } catch (e: ActivityNotFoundException) {
            context.showToast(R.string.obsidian_missing)
        }
    }

    /** Shows the change at once, writes it in the background, and puts the old row back if that fails. */
    private fun act(card: TodayCard, action: TodayAction, picked: LocalDate? = null) {
        if (!pending.add(card.path)) return
        val changes = CockpitToday.changesFor(action, card, LocalDate.now(), cfg.clearDateOnInProgress, picked)
        menuFor = null
        cards = cards.map { if (it.path == card.path) CockpitToday.applyChanges(it, changes) else it }
        render()
        io.execute {
            val outcome = write(card, changes)
            main.post {
                pending.remove(card.path)
                when (outcome) {
                    Outcome.OK -> {
                        CockpitAgendaRepository.refreshIfNeeded(context, force = true) { onChanged() }
                        if (pending.isEmpty()) reload()
                    }
                    Outcome.CHANGED -> {
                        context.showToast(R.string.today_changed)
                        CockpitAgendaRepository.refreshIfNeeded(context, force = true) { onChanged() }
                        reload()
                    }
                    Outcome.FAILED -> {
                        context.showToast(R.string.today_failed)
                        cards = cards.map { if (it.path == card.path) card else it }
                        if (!dismissed) render()
                    }
                }
            }
        }
    }

    private enum class Outcome { OK, CHANGED, FAILED }

    private fun write(card: TodayCard, changes: Map<String, String?>): Outcome = try {
        val vault = prefs.vault(context)
        val head = vault?.readHead(VaultAccess.VaultFile(card.path, card.fileId))
        when {
            vault == null -> Outcome.FAILED
            head == null || CockpitToday.headHash(head) != card.headHash -> Outcome.CHANGED
            else -> {
                val updated = vault.readText(card.path)?.let { CockpitFrontmatter.update(it, changes) }
                if (updated == null) Outcome.FAILED else { vault.writeText(card.path, updated); Outcome.OK }
            }
        }
    } catch (e: Exception) {
        Outcome.FAILED
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun note(s: String) = text(s, 14f, secondary).apply { setPadding(0, dp(12), 0, dp(4)) }

    private fun applyFont(v: View, f: ThemeApplier.Fonts) {
        if (v is TextView) v.typeface = if (f.allBold || v.typeface?.isBold == true) f.bold else f.regular
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFont(v.getChildAt(i), f)
    }

    companion object {
        private val io = Executors.newSingleThreadExecutor()

        /** [onChanged] runs on the main thread after the home agenda line was refreshed. */
        fun show(context: Context, onChanged: () -> Unit = {}) {
            CockpitTodaySheet(DetailsSheet.unwrap(context), onChanged).show()
        }
    }
}
