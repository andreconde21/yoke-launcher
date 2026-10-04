package com.outsmartis.yoke.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.helper.dpToPx
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.widgets.WidgetEntry
import com.outsmartis.yoke.widgets.WidgetList

/** A full-screen column of AppWidgets. Opened by the WidgetPage gesture action. */
class WidgetPageFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var manager: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private lateinit var column: LinearLayout
    private lateinit var scroll: ScrollView
    private var list = WidgetList()
    private var listening = false
    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = pendingId
        if (it.resultCode == Activity.RESULT_OK && id != -1) configureOrAdd(id) else discardPending()
    }
    private val configureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = pendingId
        if (it.resultCode == Activity.RESULT_OK && id != -1) finishAdd(id) else discardPending()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        prefs = Prefs(ctx)
        manager = AppWidgetManager.getInstance(ctx)
        host = AppWidgetHost(ctx.applicationContext, HOST_ID)
        column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Swipe down at the top of the page goes home
        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 != null && vy > 1500 && e2.y - e1.y > 150.dpToPx() && !scroll.canScrollVertically(-1)) {
                    findNavController().popBackStack()
                    return true
                }
                return false
            }
        })
        scroll.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); false }
        return FrameLayout(ctx).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = WidgetList.parse(prefs.widgetsJson).retain { id ->
            (manager.getAppWidgetInfo(id) != null).also { if (!it) host.deleteAppWidgetId(id) }
        }
        save()
        render()
    }

    override fun onStart() {
        super.onStart()
        try { host.startListening(); listening = true } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStop() {
        if (listening) {
            try { host.stopListening() } catch (e: Exception) { e.printStackTrace() }
            listening = false
        }
        super.onStop()
    }

    private fun save() { prefs.widgetsJson = list.toJson() }

    private fun themed(v: View): View = v.also { ThemeApplier.apply(it) }

    private fun render() {
        val ctx = requireContext()
        column.removeAllViews()
        val pad = 16.dpToPx()
        column.setPadding(0, 48.dpToPx(), 0, 48.dpToPx())
        if (list.entries.isEmpty()) {
            val empty = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(pad, 200.dpToPx(), pad, pad)
            }
            empty.addView(TextView(ctx).apply { setText(R.string.widgets_empty); textSize = 18f; gravity = Gravity.CENTER })
            empty.addView(addButton(ctx))
            column.addView(themed(empty))
            return
        }
        for (entry in list.entries) {
            val info = manager.getAppWidgetInfo(entry.id) ?: continue
            val view: AppWidgetHostView = host.createView(requireContext().applicationContext, entry.id, info)
            val minDp = (info.minHeight / resources.displayMetrics.density).toInt()
            val h = maxOf(entry.heightDp, minDp).dpToPx()
            view.setOnLongClickListener { showMenu(entry.id); true }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            params.setMargins(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            view.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                val d = resources.displayMetrics.density
                val w = ((r - l) / d).toInt()
                val hh = ((b - t) / d).toInt()
                (v as AppWidgetHostView).updateAppWidgetSize(null, w, hh, w, hh)
            }
            column.addView(view, params)
        }
        column.addView(themed(addButton(ctx)))
    }

    private fun addButton(ctx: android.content.Context) = TextView(ctx).apply {
        setText(R.string.widgets_add)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(16.dpToPx(), 20.dpToPx(), 16.dpToPx(), 20.dpToPx())
        setOnClickListener { showPicker() }
    }

    // --- long-press menu ---

    private fun showMenu(id: Int) {
        val items = arrayOf(
            getString(R.string.widgets_move_up), getString(R.string.widgets_move_down),
            getString(R.string.widgets_resize), getString(R.string.widgets_remove),
        )
        AlertDialog.Builder(requireContext()).setItems(items) { _, which ->
            when (which) {
                0 -> { list = list.move(id, -1); save(); render() }
                1 -> { list = list.move(id, 1); save(); render() }
                2 -> showResize(id)
                3 -> { host.deleteAppWidgetId(id); list = list.remove(id); save(); render() }
            }
        }.show()
    }

    private fun showResize(id: Int) {
        val names = arrayOf(getString(R.string.widgets_size_small), getString(R.string.widgets_size_medium), getString(R.string.widgets_size_large))
        AlertDialog.Builder(requireContext()).setItems(names) { _, which ->
            list = list.resize(id, WidgetList.SIZES_DP[which]); save(); render()
        }.show()
    }

    // --- picker ---

    private fun showPicker() {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val providers = manager.installedProviders.sortedBy { it.loadLabel(pm).toString().lowercase() }
        val grouped = providers.groupBy { it.provider.packageName }
            .entries.sortedBy { e -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(e.key, 0)).toString().lowercase() }.getOrDefault(e.key) }
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx()) }
        var dialog: AlertDialog? = null
        for ((pkg, infos) in grouped) {
            val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            body.addView(TextView(ctx).apply { text = appLabel; textSize = 14f; alpha = 0.6f; setPadding(0, 16.dpToPx(), 0, 4.dpToPx()) })
            for (info in infos) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                }
                val preview = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(runCatching { info.loadPreviewImage(ctx, 0) }.getOrNull() ?: info.loadIcon(ctx, 0))
                }
                row.addView(preview, LinearLayout.LayoutParams(96.dpToPx(), 64.dpToPx()))
                row.addView(TextView(ctx).apply { text = info.loadLabel(pm); textSize = 16f; setPadding(12.dpToPx(), 0, 0, 0) })
                row.setOnClickListener { dialog?.dismiss(); startAdd(info) }
                body.addView(row)
            }
        }
        val sv = ScrollView(ctx).apply { addView(body) }
        ThemeApplier.apply(sv)
        dialog = AlertDialog.Builder(ctx).setView(sv).create().also { it.show() }
    }

    // --- add flow ---

    private fun startAdd(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrAdd(id)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }
        try {
            bindLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            failAdd()
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: pendingInfo
        if (info?.configure == null) {
            finishAdd(id)
            return
        }
        try {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            configureLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            failAdd()
        }
    }

    private fun finishAdd(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { failAdd(); return }
        val minDp = (info.minHeight / resources.displayMetrics.density).toInt()
        list = list.add(WidgetEntry(id, maxOf(minDp, WidgetList.DEFAULT_HEIGHT_DP)))
        pendingId = -1
        pendingInfo = null
        save()
        render()
    }

    private fun failAdd() {
        discardPending()
        requireContext().showToast(getString(R.string.widgets_cannot_add))
    }

    private fun discardPending() {
        if (pendingId != -1) host.deleteAppWidgetId(pendingId)
        pendingId = -1
        pendingInfo = null
    }

    companion object {
        /** Unique AppWidgetHost id for Yoke. Never change: bound widget ids belong to it. */
        const val HOST_ID = 0x59304B45
    }
}
