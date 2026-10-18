package com.outsmartis.yoke.limits

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.helper.getColorFromAttr
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import kotlin.concurrent.thread

/**
 * The calm pause before a limited app: its name, which open of the day this is, a countdown,
 * and "Not now". "Open" appears only when the countdown ends. Leaving the screen cancels it.
 */
class PauseActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pkg: String
    private var remaining = 0
    private var opening = false
    private lateinit var countdown: TextView
    private lateinit var open: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val prefs = LimitPrefs(this)
        val opensSoFar = prefs.opensToday(pkg)
        remaining = LimitPolicy.delaySeconds(opensSoFar)
        setContentView(buildView(opensSoFar + 1))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = notNow()
        })
        tick()
    }

    private fun buildView(openNumber: Int): View {
        val theme = ThemeStore.current(this)
        val d = resources.displayMetrics.density
        val fg = theme?.foreground ?: getColorFromAttr(R.attr.primaryColor)
        val secondary = ThemeApplier.textColor(this, secondary = true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * d).toInt(), 0, (32 * d).toInt(), 0)
            setBackgroundColor(theme?.background ?: getColorFromAttr(R.attr.primaryInverseColor))
        }
        fun text(s: String, sp: Float, color: Int) = TextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        }
        root.addView(text(label(), 30f, fg))
        root.addView(text(getString(R.string.limits_open_today, LimitPolicy.ordinal(openNumber)), 16f, secondary))
        countdown = text("", 56f, fg).apply { setPadding(0, (32 * d).toInt(), 0, (32 * d).toInt()) }
        root.addView(countdown)
        root.addView(text(getString(R.string.limits_breathe), 16f, secondary))
        val buttons = LinearLayout(this).apply {
            // Stacked, each centred; "Open" keeps its space (invisible) so "Not now" never shifts.
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (32 * d).toInt(), 0, 0)
        }
        val notNow = text(getString(R.string.limits_not_now), 20f, fg).apply {
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (12 * d).toInt())
            setOnClickListener { notNow() }
        }
        open = text(getString(R.string.limits_open), 20f, theme?.accentText ?: fg).apply {
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (12 * d).toInt())
            visibility = View.INVISIBLE
            setOnClickListener { openApp() }
        }
        buttons.addView(notNow)
        buttons.addView(open)
        root.addView(buttons)
        ThemeApplier.fonts(this)?.let { f -> listOf(countdown, open, notNow).forEach { it.typeface = f.regular } }
        return root
    }

    private fun label(): String = runCatching {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    }.getOrDefault(pkg)

    private fun tick() {
        if (remaining <= 0) {
            countdown.text = "0"
            open.visibility = View.VISIBLE
            return
        }
        countdown.text = remaining.toString()
        remaining--
        handler.postDelayed(::tick, 1000)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the pause screen cancels it; coming back means waiting again.
        if (!isFinishing && !opening && !isChangingConfigurations) finish()
    }

    override fun onResume() {
        super.onResume()
        showingFor = pkg
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (showingFor == pkg) showingFor = null
        super.onDestroy()
    }

    private fun notNow() {
        if (target() is LimitGate.Target.InFront) goHome()
        finish()
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openApp() {
        if (opening) return
        opening = true
        LimitGate.startVisit(this, pkg)
        val app = applicationContext
        thread(name = "yoke-pause-open") {
            val ready = HardPause.unsuspend(app, pkg)
            handler.post {
                if (!ready) showToast(getString(R.string.limits_still_paused))
                launch(target())
                finish()
            }
        }
    }

    private fun launch(target: LimitGate.Target) {
        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            when (target) {
                is LimitGate.Target.App -> launcher.startMainActivity(target.component, target.user, null, null)
                is LimitGate.Target.Shortcut -> launcher.startShortcut(pkg, target.id, null, null, target.user)
                is LimitGate.Target.Pending -> target.intent.send()
                LimitGate.Target.InFront -> Unit
            }
        } catch (e: Exception) {
            showToast(getString(R.string.unable_to_open_app))
        }
    }

    @Suppress("DEPRECATION")
    private fun target(): LimitGate.Target {
        val user: UserHandle? = intent.getParcelableExtra(EXTRA_USER)
        val component: ComponentName? = intent.getParcelableExtra(EXTRA_COMPONENT)
        val pending: PendingIntent? = intent.getParcelableExtra(EXTRA_PENDING)
        val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)
        return when {
            pending != null -> LimitGate.Target.Pending(pending)
            shortcut != null && user != null -> LimitGate.Target.Shortcut(shortcut, user)
            component != null && user != null -> LimitGate.Target.App(component, user)
            else -> LimitGate.Target.InFront
        }
    }

    companion object {
        /** The app whose pause screen is up; a second request for it must not restart the countdown. */
        @Volatile
        var showingFor: String? = null
            private set

        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_COMPONENT = "component"
        private const val EXTRA_USER = "user"
        private const val EXTRA_SHORTCUT = "shortcut"
        private const val EXTRA_PENDING = "pending"

        fun start(context: Context, pkg: String, target: LimitGate.Target) {
            val intent = Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            when (target) {
                is LimitGate.Target.App -> intent.putExtra(EXTRA_COMPONENT, target.component).putExtra(EXTRA_USER, target.user)
                is LimitGate.Target.Shortcut -> intent.putExtra(EXTRA_SHORTCUT, target.id).putExtra(EXTRA_USER, target.user)
                is LimitGate.Target.Pending -> intent.putExtra(EXTRA_PENDING, target.intent)
                LimitGate.Target.InFront -> Unit
            }
            context.startActivity(intent)
        }
    }
}
