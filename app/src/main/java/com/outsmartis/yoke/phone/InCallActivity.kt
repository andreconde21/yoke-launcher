package com.outsmartis.yoke.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.isVisible
import com.outsmartis.yoke.R
import com.outsmartis.yoke.databinding.ActivityInCallBinding

/**
 * Deliberately plain call screen (number, state, answer / decline / hang up) so the phone role is
 * usable; YOK-039 replaces the look with Yoke's theme. Shows over the lock screen and wakes it.
 */
class InCallActivity : Activity() {

    companion object {
        private const val EXTRA_ACTION = "com.outsmartis.yoke.phone.extra.ACTION"

        /** [action] runs on the focused call as soon as the screen opens (the notification's buttons). */
        fun intent(context: Context, action: CallAction? = null): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (action != null) putExtra(EXTRA_ACTION, action.name) }

        fun open(context: Context) {
            runCatching { context.startActivity(intent(context)) }
        }
    }

    private lateinit var binding: ActivityInCallBinding
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.callAnswer.setOnClickListener { PhoneCalls.perform(CallAction.ANSWER) }
        binding.callDecline.setOnClickListener { PhoneCalls.perform(CallAction.DECLINE) }
        binding.callHangUp.setOnClickListener { PhoneCalls.perform(CallAction.HANG_UP) }
        runRequestedAction(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        runRequestedAction(intent)
    }

    override fun onStart() {
        super.onStart()
        PhoneCalls.listen(listener)
        render()
    }

    override fun onStop() {
        PhoneCalls.unlisten(listener)
        super.onStop()
    }

    private fun runRequestedAction(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_ACTION) ?: return
        intent.removeExtra(EXTRA_ACTION)
        val action = runCatching { CallAction.valueOf(name) }.getOrNull() ?: return
        PhoneCalls.perform(action)
    }

    private fun render() {
        val screen = PhoneCalls.registry.screen()
        if (screen == CallScreen.None) {
            finish()
            return
        }
        val number = when (screen) {
            is CallScreen.Incoming -> screen.number
            is CallScreen.Ongoing -> screen.number
            is CallScreen.Ended -> screen.number
            CallScreen.None -> null
        }
        binding.callNumber.text = number ?: getString(R.string.phone_unknown_number)
        binding.callState.setText(
            when (screen) {
                is CallScreen.Incoming -> if (screen.waiting) R.string.phone_waiting else R.string.phone_incoming
                is CallScreen.Ongoing -> when (screen.phase) {
                    CallPhase.ACTIVE -> R.string.phone_on_call
                    CallPhase.HOLDING -> R.string.phone_on_hold
                    else -> if (screen.incoming) R.string.phone_connecting else R.string.phone_calling
                }
                else -> R.string.phone_ended
            }
        )
        val actions = screen.actions
        binding.callAnswer.isVisible = CallAction.ANSWER in actions
        binding.callDecline.isVisible = CallAction.DECLINE in actions
        binding.callHangUp.isVisible = CallAction.HANG_UP in actions
    }
}
