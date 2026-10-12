package com.outsmartis.yoke.details

import com.outsmartis.yoke.limits.LimitGate
import com.outsmartis.yoke.limits.LimitPrefs
import com.outsmartis.yoke.limits.LimitedApps
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Gravity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.helper.setSwipeDismissContent
import com.outsmartis.yoke.helper.asTextField
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.helper.openAppInfo
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import java.util.concurrent.Executors

/** An extra footer action for the sheet (Replace..., Rename, Hide, Uninstall). */
class SheetAction(val label: String, val onClick: () -> Unit)

/**
 * Long-press sheet for an app: live agent details when the app ships a launcher details
 * provider (Conductore), then its launcher shortcuts and footer actions. Minimal text,
 * coloured from the active theme.
 */
class DetailsSheet private constructor(
    private val context: Context,
    private val packageName: String,
    private val user: UserHandle,
    private val label: String,
    private val extraActions: List<SheetAction>,
) {
    private val client = AppDetailsClient(context)
    private val main = Handler(Looper.getMainLooper())
    private val dialog = Dialog(context)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val density = context.resources.displayMetrics.density

    private val theme = ThemeStore.current(context)
    private val fg = theme?.foreground ?: context.getColorFromAttr(R.attr.primaryColor)
    private val secondary = theme?.secondary ?: context.getColorFromAttr(R.attr.primaryColorTrans50)
    private val accent = theme?.accentText ?: fg
    private val muted = theme?.muted ?: secondary
    private val attention = theme?.attention ?: ATTENTION_FALLBACK
    private val success = theme?.success ?: SUCCESS_FALLBACK
    private var query = ""
    private var firstWaiting: View? = null
    private var keptFocus: Triple<String?, Int, Int>? = null
    private val fonts = ThemeApplier.fonts(context)

    private var authority: String? = null
    private var observer: ContentObserver? = null
    @Volatile private var dismissed = false
    private var loadSeq = 0

    // Reply state survives re-renders (the provider reloads the sheet while the user types).
    // Drafts outlive the sheet: closing it (e.g. to unlock Conductore) must not lose a half-written answer.
    private val drafts = sharedDrafts
    private val sending = mutableSetOf<String>()
    private val expanded = mutableSetOf<String>()
    private val fields = mutableMapOf<String, EditText>()
    private var lastResult: AppDetailsClient.Result? = null
    private var lastShortcuts: List<ShortcutInfo> = emptyList()

    private fun dp(v: Int) = (v * density).toInt()

    private fun show() {
        val scroll = ScrollView(context).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Above the navigation bar and the keyboard, measured as well as from insets.
        com.outsmartis.yoke.helper.SheetInsets.install(scroll, content, side = dp(24), top = dp(20), bottom = dp(20))
        // Swipe the sheet down to close it.
        dialog.setSwipeDismissContent(scroll,
            surfaceColor = theme?.surface ?: context.getColorFromAttr(R.attr.dialogShadeColor).let { it or (0xFF shl 24) },
            handleColor = secondary)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.5f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        dialog.setOnDismissListener {
            dismissed = true
            observer?.let { client.unobserve(it) }
            observer = null
        }
        render(header = label, body = emptyList(), hint = null, shortcuts = emptyList())
        dialog.show()
        authority = client.authorityOf(packageName)
        reload()
    }

    /** Loads details and shortcuts off the main thread, then renders; called again on provider change. */
    private fun reload() {
        val seq = ++loadSeq
        io.execute {
            val shortcuts = loadShortcuts()
            val auth = authority
            val result = auth?.let { client.load(it) }
            main.post {
                if (dismissed || seq != loadSeq) return@post
                if (auth != null && observer == null && result is AppDetailsClient.Result.Ok) {
                    observer = client.observe(auth) { if (!dismissed) reload() }
                }
                present(result, shortcuts)
            }
        }
    }

    private fun present(result: AppDetailsClient.Result?, shortcuts: List<ShortcutInfo>) {
        // Keep the caret where the user left it: read it from the fields still on screen before
        // the rows below build their replacements.
        val focused = fields.entries.firstOrNull { it.value.hasFocus() }
        keptFocus = focused?.let { Triple(it.key, it.value.selectionStart, it.value.selectionEnd) }
        fields.clear()
        lastResult = result
        lastShortcuts = shortcuts
        val isConductore = packageName == CONDUCTORE_PACKAGE
        when (result) {
            is AppDetailsClient.Result.Ok -> {
                val d = result.details
                val now = System.currentTimeMillis()
                val s = d.summary
                val summaryText = DetailsText.summaryLine(s, d.items.size)
                val updated = if (s != null && s.monitoring) DetailsText.updated(s.updatedAt, now) else ""
                val head = listOf(summaryText, updated).filter { it.isNotEmpty() }.joinToString(" · ")
                val rows = when {
                    s != null && !s.monitoring -> listOf(note(context.getString(R.string.details_not_watching)))
                    d.items.isEmpty() -> listOf(note(context.getString(R.string.details_no_agents)))
                    else -> agentsView(d.items, s?.contractVersion ?: 1, d.authority)
                }
                render(label, head, rows, null, shortcuts)
            }
            AppDetailsClient.Result.NeedsNewerAndroid ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_needs_android_12) else null, shortcuts)
            AppDetailsClient.Result.Unavailable ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_update_conductore) else null, shortcuts)
            null ->
                render(label, "", emptyList(), if (isConductore) context.getString(R.string.details_update_conductore) else null, shortcuts)
        }
    }

    private fun render(header: String, body: List<View>, hint: String?, shortcuts: List<ShortcutInfo>) =
        render(header, "", body, hint, shortcuts)

    private fun render(header: String, summary: String, body: List<View>, hint: String?, shortcuts: List<ShortcutInfo>) {
        val (focusedId, selStart, selEnd) = keptFocus ?: Triple(null, 0, 0)
        keptFocus = null
        content.removeAllViews()
        content.addView(text(header, 22f, fg, bold = true))
        if (summary.isNotEmpty()) content.addView(text(summary, 13f, secondary).apply { setPadding(0, dp(4), 0, 0) })
        if (hint != null) content.addView(note(hint))
        if (body.isNotEmpty()) {
            content.addView(spacer(12))
            body.forEach { content.addView(it) }
        }
        content.addView(spacer(16))
        shortcuts.forEach { sc ->
            content.addView(action(sc.shortLabel?.toString() ?: sc.id) { startShortcut(sc) })
        }
        content.addView(action(context.getString(R.string.details_open_app, label)) { openApp() })
        content.addView(action(context.getString(R.string.details_app_info)) { dismissThen { openAppInfo(context, user, packageName) } })
        if (packageName != context.packageName) {
            val limited = LimitPrefs(context).isLimited(packageName)
            content.addView(action(context.getString(if (limited) R.string.limits_remove else R.string.limits_add)) {
                dismissThen {
                    LimitedApps.setLimited(context, packageName, !limited)
                    context.showToast(context.getString(if (limited) R.string.limits_removed else R.string.limits_added, label))
                }
            })
        }
        extraActions.forEach { a -> content.addView(action(a.label) { dismissThen(a.onClick) }) }
        ThemeApplier.fonts(context)?.let { applyFont(content, it) }
        focusedId?.let { id ->
            fields[id]?.let { f ->
                f.requestFocus()
                val n = f.text.length
                f.setSelection(selStart.coerceIn(0, n), selEnd.coerceIn(0, n))
            }
        }
    }

    private fun applyFont(v: View, f: ThemeApplier.Fonts) {
        if (v is TextView) v.typeface = if (f.allBold || v.typeface?.isBold == true) f.bold else f.regular
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFont(v.getChildAt(i), f)
    }

    private fun itemRow(item: DetailsItem, compact: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(if (compact) 7 else 10), 0, dp(if (compact) 7 else 10))
            setOnClickListener { openItem(item) }
            if (AgentGroups.dimmed(item)) alpha = 0.5f
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(item.title, if (compact) 15f else 16f, fg, bold = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (!compact) item.subtitle?.let {
            texts.addView(text(it, 13f, secondary).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stateDot(item.state))
        return row
    }

    /** The row (tap = deep link) plus, for contract v2 needsInput/blocked rows, the question and reply controls. */
    private fun itemBlock(item: DetailsItem, compact: Boolean, version: Int, auth: String): View {
        val row = itemRow(item, compact)
        val ui = ReplyUi.of(item, version)
        if (ui == ReplyUi.None) return row
        val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        block.addView(row)
        item.question?.let { q ->
            val open = item.id in expanded
            block.addView(text(q, 14f, fg).apply {
                maxLines = if (open) Int.MAX_VALUE else 6
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dp(6))
                setOnClickListener {
                    if (!expanded.remove(item.id)) expanded.add(item.id)
                    maxLines = if (item.id in expanded) Int.MAX_VALUE else 6
                }
            })
        }
        val busy = item.id in sending
        when (ui) {
            is ReplyUi.Note -> block.addView(text(ui.text, 13f, secondary).apply { setPadding(0, 0, 0, dp(6)) })
            is ReplyUi.Choices -> block.addView(choiceRow(item, ui.options, busy, auth))
            ReplyUi.TextField -> block.addView(replyRow(item, busy, auth))
            ReplyUi.None -> {}
        }
        if (busy) block.addView(text(context.getString(R.string.details_sending), 12f, secondary))
        return block
    }

    private val sheetPrefs get() = context.getSharedPreferences("com.outsmartis.yoke", 0)

    /**
     * The agents laid out like Conductore's home page in Projects mode: an optional search, the
     * view bar (all agents / active, needs-you count, detailed / compact), then one box per project.
     */
    private fun agentsView(items: List<DetailsItem>, version: Int, auth: String): List<View> {
        val prefs = sheetPrefs
        val filter = if (prefs.getString(FILTER_KEY, null) == DetailsFilter.All.key) DetailsFilter.All else DetailsFilter.Active
        val compact = prefs.getBoolean(COMPACT_KEY, false)
        val folded = prefs.getStringSet(FOLDED_KEY, null) ?: emptySet()
        val out = mutableListOf<View>()
        if (items.size >= SEARCH_FROM) out += searchField()
        out += viewBar(items, filter, compact)
        firstWaiting = null
        val groups = AgentGroups.group(items, filter, query)
        if (groups.isEmpty()) out += note(context.getString(R.string.details_filter_empty))
        val headers = groups.size > 1 || groups.any { it.name != null }
        groups.forEach { out += box(it, headers, folded, compact, version, auth) }
        return out
    }

    private fun searchField(): View = EditText(context, null, 0, R.style.TextSmall).asTextField().apply {
        hint = context.getString(R.string.details_search_hint)
        setTextColor(fg)
        setHintTextColor(secondary)
        textSize = 14f
        setSingleLine(true)
        setText(query)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply { cornerRadius = 10 * density; setStroke(dp(1), muted) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(10) }
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                val q = e?.toString().orEmpty()
                if (q != query) { query = q; repaint() }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        fields[SEARCH_FIELD] = this
    }

    private fun viewBar(items: List<DetailsItem>, filter: DetailsFilter, compact: Boolean): View {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(10))
        }
        val all = filter == DetailsFilter.All
        bar.addView(text((if (all) "○ " else "● ") + context.getString(if (all) R.string.details_view_all else R.string.details_view_active), 14f, fg).apply {
            setPadding(0, dp(6), dp(14), dp(6))
            setOnClickListener {
                sheetPrefs.edit().putString(FILTER_KEY, if (all) DetailsFilter.Active.key else DetailsFilter.All.key).apply()
                repaint()
            }
        })
        val waiting = items.count { it.state.urgent }
        if (waiting > 0) bar.addView(text("● $waiting", 14f, attention, bold = true).apply {
            setPadding(0, dp(6), dp(14), dp(6))
            setOnClickListener { scrollToFirstWaiting() }
        })
        bar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(text(context.getString(if (compact) R.string.details_density_compact else R.string.details_density_detailed), 14f, secondary).apply {
            setPadding(dp(14), dp(6), 0, dp(6))
            setOnClickListener {
                sheetPrefs.edit().putBoolean(COMPACT_KEY, !compact).apply()
                repaint()
            }
        })
        return bar
    }

    private fun box(group: AgentGroup, header: Boolean, folded: Set<String>, compact: Boolean, version: Int, auth: String): View {
        val key = group.name ?: OTHER_KEY
        val open = key !in folded || query.isNotBlank()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(if (header) 6 else 8), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = 12 * density; setStroke(dp(1), muted) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) }
        }
        if (header) box.addView(text((if (open) "⌄  " else "›  ") + (group.name ?: context.getString(R.string.details_group_other)), 16f, fg, bold = true).apply {
            setPadding(0, dp(10), 0, dp(if (open) 6 else 10))
            setOnClickListener {
                val f = (sheetPrefs.getStringSet(FOLDED_KEY, null) ?: emptySet()).toMutableSet()
                if (!f.add(key)) f.remove(key)
                sheetPrefs.edit().putStringSet(FOLDED_KEY, f).apply()
                repaint()
            }
        })
        if (open) group.items.forEach { item ->
            val v = itemBlock(item, compact, version, auth)
            if (item.state.urgent && firstWaiting == null) firstWaiting = v
            box.addView(v)
        }
        return box
    }

    private fun scrollToFirstWaiting() {
        val target = firstWaiting ?: return
        val scroll = generateSequence(content.parent) { it.parent }.filterIsInstance<ScrollView>().firstOrNull() ?: return
        val a = IntArray(2).also { target.getLocationInWindow(it) }
        val b = IntArray(2).also { content.getLocationInWindow(it) }
        scroll.smoothScrollTo(0, (a[1] - b[1] - dp(12)).coerceAtLeast(0))
    }

    /** Conductore's state dot: needs you filled orange, working accent, done green, idle a hollow ring. */
    private fun stateDot(state: ItemState): View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when (state) {
                ItemState.NeedsInput, ItemState.Blocked -> setColor(attention)
                ItemState.Working -> setColor(accent)
                ItemState.Finished -> setColor(success)
                ItemState.Idle, ItemState.Unknown -> { setColor(Color.TRANSPARENT); setStroke(dp(2), secondary) }
            }
        }
        layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { leftMargin = dp(12) }
    }

    private fun choiceRow(item: DetailsItem, options: List<String>, busy: Boolean, auth: String): View {
        val flow = FlowLayout(context)
        options.forEachIndexed { i, o ->
            flow.addView(chip(o, ReplyUi.isSecondary(o), !busy) {
                send(item) { client.choose(auth, item.id, i) }
            })
        }
        return flow
    }

    private fun replyRow(item: DetailsItem, busy: Boolean, auth: String): View {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val field = EditText(context, null, 0, R.style.TextSmall).asTextField().apply {
            hint = context.getString(R.string.details_reply_hint, item.title)
            setTextColor(fg)
            setHintTextColor(secondary)
            textSize = 14f
            // Multi-line: Enter adds a line, the Send chip sends.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            minLines = 1
            maxLines = 6
            isVerticalScrollBarEnabled = true
            isEnabled = !busy
            setText(drafts[item.id].orEmpty())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { drafts[item.id] = e?.toString().orEmpty() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        fields[item.id] = field
        fun go() {
            val t = drafts[item.id].orEmpty().trim()
            if (t.isNotEmpty() && item.id !in sending) send(item) { client.reply(auth, item.id, t) }
        }
        line.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(chip(context.getString(R.string.details_send), false, !busy) { go() })
        return line
    }

    private fun chip(label: String, secondaryStyle: Boolean, enabled: Boolean, onClick: () -> Unit): TextView {
        val color = if (secondaryStyle) secondary else accent
        return text(label, 14f, color).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = 20 * density
                setStroke(dp(1), if (secondaryStyle) Color.TRANSPARENT else color)
                setColor(Color.TRANSPARENT)
            }
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { rightMargin = dp(6); bottomMargin = dp(6) }
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { onClick() }
        }
    }

    /** Runs a reply/choose call off the main thread and reports the outcome. */
    private fun send(item: DetailsItem, call: () -> AppDetailsClient.ReplyResult) {
        if (!sending.add(item.id)) return
        repaint()
        actions.execute {
            val r = call()
            main.post {
                sending.remove(item.id)
                when (r) {
                    AppDetailsClient.ReplyResult.Ok -> { drafts.remove(item.id); context.showToast(context.getString(R.string.details_sent)) }
                    AppDetailsClient.ReplyResult.Queued -> { drafts.remove(item.id); context.showToast(context.getString(R.string.details_queued)) }
                    AppDetailsClient.ReplyResult.PendingUnlock -> { drafts.remove(item.id); context.showToast(context.getString(R.string.details_pending_unlock)) }
                    AppDetailsClient.ReplyResult.NotAllowed -> context.showToast(context.getString(R.string.details_update_conductore_reply))
                    is AppDetailsClient.ReplyResult.Failed -> context.showToast(context.getString(R.string.details_failed_kept, r.message))
                }
                if (!dismissed) repaint()
            }
        }
    }

    private fun repaint() = present(lastResult, lastShortcuts)

    private fun openItem(item: DetailsItem) {
        val link = item.deepLink
        if (link == null) return openApp()
        try {
            val intent = Intent.parseUri(link, Intent.URI_INTENT_SCHEME).apply {
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            dismissThen { context.startActivity(intent) }
        } catch (e: Exception) {
            openApp()
        }
    }

    private fun openApp() = dismissThen {
        try {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val component = launcher.getActivityList(packageName, user).firstOrNull()?.componentName
            if (component != null && LimitGate.intercept(context, packageName, LimitGate.Target.App(component, user))) return@dismissThen
            if (component != null) launcher.startMainActivity(component, user, null, null)
            else context.startActivity(
                context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            context.showToast(context.getString(R.string.unable_to_open_app))
        }
    }

    private fun startShortcut(sc: ShortcutInfo) = dismissThen {
        if (LimitGate.intercept(context, packageName, LimitGate.Target.Shortcut(sc.id, user))) return@dismissThen
        try {
            (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc, null, null)
        } catch (e: Exception) {
            context.showToast(context.getString(R.string.unable_to_open_shortcut))
        }
    }

    private fun loadShortcuts(): List<ShortcutInfo> = try {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val q = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcher.getShortcuts(q, user)?.filter { it.isEnabled }?.sortedBy { it.rank } ?: emptyList()
    } catch (_: Exception) {
        emptyList() // not the default launcher, or the profile is locked
    }

    private fun dismissThen(block: () -> Unit) {
        dialog.dismiss()
        block()
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun note(s: String) = text(s, 14f, secondary).apply { setPadding(0, dp(8), 0, dp(4)) }

    private fun action(s: String, onClick: () -> Unit) = text(s, 16f, fg).apply {
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { onClick() }
    }

    private fun spacer(dp: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(dp)); setBackgroundColor(Color.TRANSPARENT) }

    companion object {
        const val CONDUCTORE_PACKAGE = "com.outsmartis.conductore"
        private const val FILTER_KEY = "DETAILS_FILTER"
        private const val COMPACT_KEY = "DETAILS_COMPACT"
        private const val FOLDED_KEY = "DETAILS_FOLDED_PROJECTS"
        private const val OTHER_KEY = "\u0000other"
        private const val SEARCH_FIELD = "\u0000search"
        private const val SEARCH_FROM = 6
        private const val ATTENTION_FALLBACK = 0xFFE69875.toInt()
        private const val SUCCESS_FALLBACK = 0xFFA7C080.toInt()
        private val sharedDrafts = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val io = Executors.newSingleThreadExecutor()
        /** Separate from [io]: a reply can block ~5 s and must not hold up reloads. */
        private val actions = Executors.newCachedThreadPool()

        fun unwrap(context: Context): Context =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }.firstOrNull { it is Activity } ?: context

        /** [extraActions] are shown under the standard footer; each dismisses the sheet first. */
        fun show(context: Context, packageName: String, user: UserHandle, label: String, extraActions: List<SheetAction> = emptyList()) {
            val ctx = unwrap(context)
            DetailsSheet(ctx, packageName, user, label, extraActions).show()
        }

        /** Gesture entry point: the Conductore sheet, or a toast when it is not installed. */
        fun showConductore(context: Context) {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val user = android.os.Process.myUserHandle()
            val activity = launcher.getActivityList(CONDUCTORE_PACKAGE, user).firstOrNull()
            if (activity == null) {
                context.showToast(context.getString(R.string.details_conductore_missing))
                return
            }
            show(context, CONDUCTORE_PACKAGE, user, activity.label.toString())
        }
    }
}

/** Minimal wrapping row for the option chips. */
internal class FlowLayout(context: Context) : ViewGroup(context) {
    private fun lp(v: View) = v.layoutParams as? MarginLayoutParams ?: MarginLayoutParams(0, 0)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec)
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            measureChildWithMargins(c, wSpec, 0, hSpec, 0)
            val m = lp(c)
            val w = c.measuredWidth + m.leftMargin + m.rightMargin
            val h = c.measuredHeight + m.topMargin + m.bottomMargin
            if (x > 0 && x + w > maxW) { x = 0; y += rowH; rowH = 0 }
            x += w; rowH = maxOf(rowH, h)
        }
        setMeasuredDimension(maxW, y + rowH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val m = lp(c)
            val w = c.measuredWidth + m.leftMargin + m.rightMargin
            val h = c.measuredHeight + m.topMargin + m.bottomMargin
            if (x > 0 && x + w > maxW) { x = 0; y += rowH; rowH = 0 }
            c.layout(x + m.leftMargin, y + m.topMargin, x + m.leftMargin + c.measuredWidth, y + m.topMargin + c.measuredHeight)
            x += w; rowH = maxOf(rowH, h)
        }
    }

    override fun generateLayoutParams(attrs: android.util.AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?) = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams
}
