package com.outsmartis.yoke.omarchy

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.outsmartis.yoke.R
import com.outsmartis.yoke.cockpit.CockpitAgendaRepository
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.theme.ThemeStore

/**
 * Yoke's screensaver (YOK-026), picked in Android's screensaver settings: the typed "YOKE" wordmark,
 * a clock, or a clock with the agenda line, in the active theme's colours. The content moves to a
 * new spot every minute against burn-in, and the screen dims after the chosen number of minutes.
 */
class YokeDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var frame: FrameLayout
    private lateinit var content: LinearLayout
    private var wordmark: TextView? = null
    private var agenda: TextView? = null
    private var step = 0L
    private var typedChars = 0

    private val shift = object : Runnable {
        override fun run() {
            place()
            if (wordmark != null) startTyping()
            handler.postDelayed(this, DreamSpec.SHIFT_EVERY_MS)
        }
    }

    private val type = object : Runnable {
        override fun run() {
            val mark = wordmark ?: return
            typedChars++
            val lines = DreamSpec.typed(typedChars)
            val done = typedChars >= DreamSpec.WORDMARK_LENGTH
            // A block cursor follows the typing, then blinks at the end.
            val cursor = if (!done || (typedChars / 4) % 2 == 0) "▌" else " "
            mark.text = lines.joinToString("\n") + cursor
            handler.postDelayed(this, if (done) 500L else 60L)
            if (done) typedChars += 3 // blink: four ticks per on / off phase
        }
    }

    private val dim = Runnable {
        isScreenBright = false
        content.animate().alpha(0.45f).setDuration(2000).start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val prefs = OmarchyStylePrefs(this)
        val theme = ThemeStore.current(this)
        val bg = theme?.background ?: Color.BLACK
        val fg = theme?.foreground ?: Color.WHITE
        val secondary = theme?.secondary ?: 0xFF9E9E9E.toInt()
        val accent = theme?.accentText ?: Color.WHITE
        val mono = ResourcesCompat.getFont(this, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
        val monoBold = ResourcesCompat.getFont(this, R.font.jetbrains_mono_bold) ?: mono

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START }
        when (prefs.dreamContent) {
            DreamContent.LOGO -> wordmark = TextView(this).apply {
                typeface = mono
                textSize = 28f
                setTextColor(accent)
                setLineSpacing(0f, 0.9f)
                text = DreamSpec.WORDMARK.joinToString("\n") + "▌"
            }.also { content.addView(it) }
            DreamContent.CLOCK, DreamContent.CLOCK_AGENDA -> {
                content.addView(TextClock(this).apply {
                    typeface = monoBold
                    textSize = 64f
                    setTextColor(fg)
                })
                content.addView(TextClock(this).apply {
                    format12Hour = "EEE d MMM"
                    format24Hour = "EEE d MMM"
                    typeface = mono
                    textSize = 18f
                    setTextColor(secondary)
                })
                if (prefs.dreamContent == DreamContent.CLOCK_AGENDA) agenda = TextView(this).apply {
                    typeface = mono
                    textSize = 16f
                    setTextColor(accent)
                    maxLines = 2
                }.also { content.addView(it) }
            }
        }
        frame = FrameLayout(this).apply { setBackgroundColor(bg) }
        frame.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(frame)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        agenda?.let { line ->
            line.text = CockpitPrefs(this).agendaLine
            CockpitAgendaRepository.refreshIfNeeded(this) { line.text = CockpitPrefs(this).agendaLine }
        }
        // Wait for the first layout so the free space is known.
        frame.post { handler.post(shift) }
        handler.postDelayed(dim, OmarchyStylePrefs(this).dreamDimAfterMin * 60_000L)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun place() {
        // Leave room for a fully typed wordmark or a longer agenda line.
        val margin = (24 * resources.displayMetrics.density).toInt()
        val freeW = frame.width - content.width - 2 * margin
        val freeH = frame.height - content.height - 2 * margin
        val (x, y) = DreamSpec.offset(step++, freeW, freeH)
        content.translationX = (margin + x).toFloat()
        content.translationY = (margin + y).toFloat()
    }

    private fun startTyping() {
        handler.removeCallbacks(type)
        typedChars = 0
        // Size the view for the whole wordmark first so the shift keeps it on screen.
        wordmark?.text = DreamSpec.WORDMARK.joinToString("\n") + "▌"
        handler.post(type)
    }
}
