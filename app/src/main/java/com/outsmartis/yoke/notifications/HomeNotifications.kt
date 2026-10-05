package com.outsmartis.yoke.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.outsmartis.yoke.R
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.details.SheetAction
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import kotlin.math.abs

/** Swipe direction on a line: left or right. */
enum class SwipeDir { LEFT, RIGHT }

/** Tap, long press and horizontal swipe on a view; consumes its touches. */
class LineTouchListener(
    context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onSwipe: (SwipeDir) -> Unit,
) : View.OnTouchListener {

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { onTap(); return true }
        override fun onLongPress(e: MotionEvent) { onLongPress() }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val dx = e2.x - (e1?.x ?: return false)
            val dy = e2.y - e1.y
            if (abs(dx) < SWIPE_MIN_PX || abs(dx) < abs(dy) * 1.5f) return false
            onSwipe(if (dx < 0) SwipeDir.LEFT else SwipeDir.RIGHT)
            return true
        }
    })
    private val density = context.resources.displayMetrics.density
    private val SWIPE_MIN_PX get() = 48 * density

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
        return detector.onTouchEvent(event)
    }
}

/** Fills the home notification block and the now-playing line. Lives as long as the home view. */
class HomeNotifications(
    private val context: Context,
    private val container: LinearLayout,
    private val nowPlayingView: TextView,
) {
    private val prefs = NotificationPrefs(context)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    fun render() {
        renderLines()
        renderNowPlaying()
    }

    fun counts(): Map<String, Int> {
        if (!prefs.enabled || !prefs.countsOnApps) return emptyMap()
        return NotificationLogic.counts(NotificationStore.entries.value.orEmpty())
    }

    private fun accentColor() = ThemeStore.current(context)?.accentText ?: context.getColorFromAttr(R.attr.primaryColor)

    private fun newLine(): TextView = TextView(context, null, 0, R.style.TextSmallLight).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.date_size))
        maxLines = 1
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding((3 * density).toInt(), (4 * density).toInt(), (3 * density).toInt(), (4 * density).toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ThemeApplier.apply(this)
        setTextColor(ThemeApplier.textColor(context, true))
    }

    private fun renderLines() {
        container.removeAllViews()
        val dnd = (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.currentInterruptionFilter
            ?: NotificationLogic.INTERRUPTION_FILTER_ALL
        if (!prefs.enabled || NotificationLogic.hiddenByDnd(prefs.hideDuringDnd, dnd)) {
            container.isVisible = false
            return
        }
        val groups = NotificationLogic.group(NotificationStore.entries.value.orEmpty(), prefs.maxLines)
        val hideText = prefs.hideTextApps
        for (group in groups) {
            val line = NotificationLogic.line(group, appLabel(group.packageName), prefs.showText, hideText)
            val tv = newLine()
            tv.maxWidth = (context.resources.displayMetrics.widthPixels - 48 * density).toInt()
            tv.text = styled(line, ThemeApplier.textColor(context, false), accentColor())
            tv.setOnTouchListener(
                LineTouchListener(
                    context,
                    onTap = { open(group) },
                    onLongPress = { menu(group.packageName, line.label) },
                    onSwipe = { NotificationStore.cancel(group.keys) },
                )
            )
            container.addView(tv)
        }
        container.isVisible = container.childCount > 0
    }

    private fun styled(line: NoteLine, labelColor: Int, accent: Int): CharSequence {
        val sb = SpannableStringBuilder()
        fun span(text: String, color: Int) {
            val start = sb.length
            sb.append(text)
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, 0)
        }
        span(line.label, labelColor)
        if (line.count != null) span(" · ${line.count}", accent)
        if (line.body.isNotEmpty()) sb.append("   ").append(line.body)
        return sb
    }

    private fun appLabel(pkg: String): String = try {
        context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun launch(pkg: String) {
        try {
            context.packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun open(group: AppGroup) {
        val entry = group.latest
        val intent: PendingIntent? = NotificationStore.find(entry.key)?.notification?.contentIntent
        if (intent == null) {
            launch(group.packageName)
            return
        }
        try {
            intent.send()
            if (entry.autoCancel) NotificationStore.cancel(listOf(entry.key))
        } catch (e: PendingIntent.CanceledException) {
            launch(group.packageName)
        } catch (e: Exception) {
            launch(group.packageName)
        }
    }

    private fun menu(pkg: String, label: String) {
        val hidden = pkg in prefs.hideTextApps
        DetailsSheet.show(
            context, pkg, Process.myUserHandle(), label,
            listOf(
                SheetAction(context.getString(if (hidden) R.string.notes_show_text_app else R.string.notes_hide_text_app)) {
                    prefs.hideTextApps = if (hidden) prefs.hideTextApps - pkg else prefs.hideTextApps + pkg
                    NotificationStore.refresh()
                    render()
                },
                SheetAction(context.getString(R.string.notes_remove_app)) {
                    prefs.allowed = prefs.allowed - pkg
                    NotificationStore.refresh()
                    render()
                },
            ),
        )
    }

    private fun renderNowPlaying() {
        val playing = if (prefs.enabled && prefs.nowPlaying) NotificationStore.nowPlaying.value else null
        if (playing == null) {
            nowPlayingView.isVisible = false
            return
        }
        nowPlayingView.text = NotificationLogic.nowPlayingLine(playing.title, playing.artist, playing.playing)
        nowPlayingView.isVisible = true
    }

    /** Wires the now-playing line's gestures once. */
    fun bindNowPlaying() {
        nowPlayingView.setOnTouchListener(
            LineTouchListener(
                context,
                onTap = {
                    val c = NotificationStore.mediaController()
                    if (c != null) {
                        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                        else c.transportControls.play()
                    }
                    refreshSoon()
                },
                onLongPress = { NotificationStore.nowPlaying.value?.let { launch(it.packageName) } },
                onSwipe = { dir ->
                    val c = NotificationStore.mediaController()
                    if (dir == SwipeDir.LEFT) c?.transportControls?.skipToNext() else c?.transportControls?.skipToPrevious()
                    refreshSoon()
                },
            )
        )
    }

    private fun refreshSoon() {
        handler.postDelayed({ NotificationStore.refresh() }, 400)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }
}
