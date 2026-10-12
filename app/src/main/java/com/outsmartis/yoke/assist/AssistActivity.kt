package com.outsmartis.yoke.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.outsmartis.yoke.MainActivity
import com.outsmartis.yoke.cockpit.QuickAddActivity
import com.outsmartis.yoke.gestures.GestureAction

/**
 * Started by the system's assist gesture (long-press power, long-press home) once Yoke is the
 * assistant. Draws nothing: it opens the chosen action and finishes. Quick-add opens over the
 * current app; the other actions need Yoke's home, so they open it.
 */
class AssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = Assistant.action(this) ?: GestureAction.CockpitVoiceAdd
        runCatching {
            when (action) {
                GestureAction.CockpitVoiceAdd -> startActivity(QuickAddActivity.voiceIntent(this))
                GestureAction.CockpitQuickAdd -> startActivity(Intent(this, QuickAddActivity::class.java))
                else -> startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_RUN_ACTION, action.type)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        finish()
    }
}
