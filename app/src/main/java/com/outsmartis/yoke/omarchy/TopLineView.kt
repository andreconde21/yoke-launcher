package com.outsmartis.yoke.omarchy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.details.AppDetailsClient
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.weather.WeatherFormat
import com.outsmartis.yoke.weather.WeatherPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Waybar-like line in the mono font. While attached it redraws every minute (clock, battery)
 * and, when the Conductore module is shown, reads Conductore's waiting sessions off the main
 * thread on attach and whenever its provider reports a change.
 */
class TopLineView(context: Context, private val modules: List<TopModule>) : TextView(context) {

    private var conductoreWaiting = 0
    private var observer: ContentObserver? = null
    private val client by lazy { AppDetailsClient(context.applicationContext) }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) = render()
    }

    init {
        textSize = 13f
        maxLines = 1
        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
        val d = resources.displayMetrics.density
        setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply { addAction(Intent.ACTION_TIME_CHANGED) }
        ContextCompat.registerReceiver(context, tick, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (TopModule.CONDUCTORE in modules) watchConductore()
        render()
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(tick) } catch (_: Exception) {}
        observer?.let { client.unobserve(it) }
        observer = null
        super.onDetachedFromWindow()
    }

    private fun watchConductore() {
        Thread {
            val authority = client.authorityOf(DetailsSheet.CONDUCTORE_PACKAGE) ?: return@Thread
            loadConductore(authority)
            post { if (isAttachedToWindow && observer == null) observer = client.observe(authority) { Thread { loadConductore(authority) }.start() } }
        }.start()
    }

    /** Blocking; runs on a worker thread and posts the redraw. */
    private fun loadConductore(authority: String) {
        val waiting = (client.load(authority) as? AppDetailsClient.Result.Ok)?.details?.items?.count { it.state.urgent } ?: 0
        post {
            conductoreWaiting = waiting
            render()
        }
    }

    fun render() {
        val now = Date()
        val values = HashMap<TopModule, String>()
        for (m in modules) values[m] = when (m) {
            TopModule.CLOCK -> android.text.format.DateFormat.getTimeFormat(context).format(now)
            TopModule.DATE -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now).replace(".", "")
            TopModule.BATTERY -> battery()
            TopModule.WEATHER -> weather()
            TopModule.AGENDA -> CockpitPrefs(context).let { if (it.agendaEnabled) TopLine.agenda(TopLine.agendaCount(it.agendaLine)) else "" }
            TopModule.CONDUCTORE -> TopLine.conductore(conductoreWaiting)
        }
        val c = OmarchyColors.of(context)
        val out = SpannableStringBuilder()
        for ((m, value) in TopLine.segments(modules, values)) {
            if (out.isNotEmpty()) out.append(TopLine.SEPARATOR)
            val start = out.length
            out.append(value)
            when (m) {
                TopModule.CLOCK -> out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, 0)
                TopModule.CONDUCTORE -> out.setSpan(ForegroundColorSpan(c.accent), start, out.length, 0)
                else -> {}
            }
        }
        setTextColor(c.foreground)
        text = out
    }

    private fun battery(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return ""
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return TopLine.battery(pct, bm.isCharging)
    }

    private fun weather(): String {
        val w = WeatherPrefs(context)
        if (!w.enabled) return ""
        return TopLine.weather(WeatherFormat.line(w.reading, w.units, false, System.currentTimeMillis()))
    }
}
