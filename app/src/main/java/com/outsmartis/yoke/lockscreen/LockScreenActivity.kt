package com.outsmartis.yoke.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.outsmartis.yoke.databinding.ActivityLockScreenBinding
import com.outsmartis.yoke.notifications.NoteEntry
import com.outsmartis.yoke.notifications.NotificationLogic
import com.outsmartis.yoke.notifications.NotificationPrefs
import com.outsmartis.yoke.notifications.NotificationStore
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.weather.WeatherFormat
import com.outsmartis.yoke.weather.WeatherPrefs
import com.outsmartis.yoke.weather.WeatherRepository

/**
 * The Yoke lock screen: clock, date, weather, music and notifications over the system keyguard.
 * Put up while the display is off ([LockScreenController.onScreenOff]); it never turns the screen
 * on itself. Swiping up hands over to the keyguard (PIN, fingerprint, face stay the system's).
 * Anything else that shows over the lock screen (calls, alarms, the camera) makes it leave.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var downY = Float.NaN

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = renderMusic()
        override fun onMetadataChanged(metadata: MediaMetadata?) = renderMusic()
        override fun onSessionDestroyed() = bindMedia()
    }

    // Unlocked another way (fingerprint or face straight from the screen-off state): get out of the way
    private val unlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    // API 31+: a call starting to ring while we are up (mode change); older APIs have no
    // permission-free signal, see onResume
    private var modeListener: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!allowed()) {
            // onDestroy follows straight away; nothing below ran
            finish()
            return
        }
        showOverKeyguard()
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeApplier.applyWindow(this)
        makeOpaque()
        ThemeApplier.apply(binding.root, surface = true)
        ContextCompat.registerReceiver(
            this, unlockedReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        watchCalls()

        binding.prev.setOnClickListener { controller?.transportControls?.skipToPrevious() }
        binding.next.setOnClickListener { controller?.transportControls?.skipToNext() }
        binding.playPause.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        }
        binding.unlockHint.setOnClickListener { unlock() }
        NotificationStore.entries.observe(this) {
            renderNotifications(it)
            bindMedia() // a player starting or stopping posts or removes its notification
        }
    }

    /**
     * The keyguard's own clock must not show through (seen on Android 16): paint the window and the
     * root in the theme background, and never ask for the wallpaper layer. A themed wallpaper is
     * not drawn here; an opaque theme colour is the safe look over a keyguard.
     */
    private fun makeOpaque() {
        val theme = com.outsmartis.yoke.theme.ThemeStore.current(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        // No Yoke theme (System default, e-ink): keep the activity theme's own window background, so
        // the text colours (which follow the same system theme) stay readable on it.
        val colour = theme?.background ?: return
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colour or 0xFF000000.toInt()))
        binding.root.setBackgroundColor(colour or 0xFF000000.toInt())
    }

    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // Started while the display is off: it waits for the user's wake, never wakes the screen itself
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!allowed()) finish()
    }

    /**
     * Still right to be up: the screen is off, this start answers a screen-off with no screen-on
     * since (not a stale or recents relaunch, not the double-press camera racing us), and the
     * launch rules (call, alarm, lock, camera in front) still say show.
     */
    private fun allowed(): Boolean {
        val offAt = intent?.getLongExtra(EXTRA_OFF_AT, 0L) ?: 0L
        val ok = LockScreenPolicy.acceptCreate(interactive(), offAt, LockScreenController.screenOnAt) &&
            LockScreenController.decide(this) == LockScreenPolicy.Decision.SHOW
        if (!ok) android.util.Log.d(LockScreenController.TAG, "activity not kept up")
        return ok
    }

    override fun onStart() {
        super.onStart()
        bindMedia()
        renderWeather()
        renderNotifications(NotificationStore.entries.value)
        WeatherRepository.refreshIfNeeded(this) { if (!isDestroyed) renderWeather() }
    }

    override fun onResume() {
        super.onResume()
        if (callActive()) {
            finish()
            return
        }
        if (LockScreenPolicy.finishOnResume(interactive(), keyguard().isKeyguardLocked)) finish()
    }

    override fun onStop() {
        super.onStop()
        unbindMedia()
        if (LockScreenPolicy.finishOnStop(interactive(), isFinishing)) finish()
    }

    override fun onDestroy() {
        unwatchCalls()
        unbindMedia()
        main.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(unlockedReceiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = ev.rawY
            MotionEvent.ACTION_CANCEL -> downY = Float.NaN
            MotionEvent.ACTION_UP -> {
                val swiped = !downY.isNaN() && LockScreenPolicy.isSwipeUp(ev.rawY - downY, binding.root.height)
                downY = Float.NaN
                if (swiped) {
                    // Cancel the gesture for the views under it, so the swipe never also taps a button
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    unlock()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun unlock() {
        val km = keyguard()
        when (LockScreenPolicy.onSwipeUp(km.isKeyguardLocked)) {
            LockScreenPolicy.Unlock.DISMISS_KEYGUARD -> km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
                override fun onDismissError() = finish()
                // onDismissCancelled: the user backed out of the PIN pad; stay up
            })
            LockScreenPolicy.Unlock.FINISH -> finish()
        }
    }

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun callActive() = LockScreenPolicy.finishForCall(audio().mode)

    /**
     * A call that rings while this screen is up: finish so the system call UI is in front and its
     * Answer works. Below API 31 there is no permission-free callback (ACTION_PHONE_STATE_CHANGED
     * needs READ_PHONE_STATE), so that gap is closed only by the onResume check.
     */
    private fun watchCalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val l = AudioManager.OnModeChangedListener { mode -> if (LockScreenPolicy.finishForCall(mode)) finish() }
        modeListener = l
        audio().addOnModeChangedListener(ContextCompat.getMainExecutor(this), l)
    }

    private fun unwatchCalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        (modeListener as? AudioManager.OnModeChangedListener)?.let { audio().removeOnModeChangedListener(it) }
        modeListener = null
    }

    private fun keyguard() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private fun interactive() = getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun renderWeather() {
        val prefs = WeatherPrefs(this)
        val text = if (prefs.enabled) {
            WeatherFormat.line(prefs.reading, prefs.units, prefs.showHighLow, System.currentTimeMillis())
        } else ""
        binding.weather.text = text
        binding.weather.isVisible = text.isNotEmpty()
    }

    /** The same apps as "Notifications on home", minus what the system hides on the lock screen. */
    private fun renderNotifications(entries: List<NoteEntry>?) {
        val notes = NotificationPrefs(this)
        val showOnLock = LockScreenController.systemShowsNotificationsOnLock(this)
        val allowPrivate = LockScreenController.systemAllowsPrivateOnLock(this)
        // The app's own visibility, lowered by the user's per-channel lock screen setting
        val visibility = { e: NoteEntry ->
            LockScreenPolicy.effectiveVisibility(
                NotificationStore.find(e.key)?.notification?.visibility ?: LockScreenPolicy.VISIBILITY_PRIVATE,
                NotificationStore.channelLockVisibility(e.key),
            )
        }
        val shown = entries.orEmpty().filter { LockScreenPolicy.shownOnLock(visibility(it), showOnLock) }
        val lines = NotificationLogic.group(shown, notes.maxLines).map { group ->
            val reveal = LockScreenPolicy.revealOnLock(visibility(group.latest), allowPrivate)
            NotificationLogic.plain(NotificationLogic.line(group, label(group.packageName), notes.showText && reveal, notes.hideTextApps))
        }
        binding.notifications.text = lines.joinToString("\n")
        binding.notifications.isVisible = lines.isNotEmpty()
    }

    private fun label(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun bindMedia() {
        val current = NotificationStore.mediaController()
        if (current?.sessionToken != controller?.sessionToken) {
            unbindMedia()
            controller = current?.also { it.registerCallback(mediaCallback, main) }
        }
        renderMusic()
    }

    private fun unbindMedia() {
        controller?.unregisterCallback(mediaCallback)
        controller = null
    }

    private fun renderMusic() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        val state = c?.playbackState?.state
        val show = LockScreenController.systemShowsNotificationsOnLock(this) && LockScreenPolicy.showMusic(title, artist, state)
        binding.musicBlock.isVisible = show
        if (!show) return
        binding.nowPlaying.text = LockScreenPolicy.musicLine(title, artist)
        binding.playPause.text = if (state == PlaybackState.STATE_PLAYING) PAUSE_GLYPH else PLAY_GLYPH
    }

    companion object {
        private const val PLAY_GLYPH = "▶"
        private const val PAUSE_GLYPH = "❚❚"

        /** elapsedRealtime of the screen-off this start answers; a start without it is refused. */
        private const val EXTRA_OFF_AT = "off_at"

        fun intent(context: Context, offAt: Long): Intent = Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_OFF_AT, offAt)
    }
}
