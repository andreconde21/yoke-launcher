package com.outsmartis.yoke.phone

import android.os.Build
import android.telecom.Call
import android.telecom.VideoProfile

/**
 * The live Telecom [Call] objects behind [registry], in the `:phone` process. YokeInCallService
 * adds and removes them; InCallActivity listens and acts on the focused call. Main thread only
 * (Telecom calls the service there).
 */
object PhoneCalls {

    val registry = CallRegistry()

    private val calls = LinkedHashMap<String, Call>()
    private var nextId = 0
    private val listeners = LinkedHashSet<() -> Unit>()

    fun add(call: Call) {
        val id = "call-${nextId++}"
        calls[id] = call
        registry.added(id, numberOf(call), CallPhase.of(stateOf(call)))
        changed()
    }

    fun update(call: Call) {
        val id = idOf(call) ?: return
        registry.changed(id, CallPhase.of(stateOf(call)))
        changed()
    }

    fun remove(call: Call) {
        val id = idOf(call) ?: return
        calls.remove(id)
        registry.removed(id)
        changed()
    }

    /** Runs [action] on the call the screen shows. */
    fun perform(action: CallAction) {
        val call = registry.focus()?.let { calls[it.id] } ?: return
        runCatching {
            when (action) {
                CallAction.ANSWER -> call.answer(VideoProfile.STATE_AUDIO_ONLY)
                CallAction.DECLINE -> call.reject(false, null)
                CallAction.HANG_UP -> call.disconnect()
            }
        }
    }

    fun listen(listener: () -> Unit) {
        listeners += listener
    }

    fun unlisten(listener: () -> Unit) {
        listeners -= listener
    }

    fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state
        else @Suppress("DEPRECATION") call.state

    private fun idOf(call: Call): String? = calls.entries.firstOrNull { it.value === call }?.key

    private fun numberOf(call: Call): String? = call.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

    private fun changed() = listeners.toList().forEach { it() }
}
