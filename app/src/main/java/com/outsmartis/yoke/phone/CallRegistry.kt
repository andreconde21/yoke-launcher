package com.outsmartis.yoke.phone

import android.telecom.Call

/** What a call is doing, as far as the call screen cares. */
enum class CallPhase {
    DIALING, RINGING, ACTIVE, HOLDING, ENDED;

    companion object {
        /** Maps a Telecom `Call.STATE_*` value (compile-time constants, so this stays testable on the JVM). */
        fun of(state: Int): CallPhase = when (state) {
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> RINGING
            Call.STATE_ACTIVE -> ACTIVE
            Call.STATE_HOLDING -> HOLDING
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> ENDED
            // NEW, CONNECTING, DIALING, SELECT_PHONE_ACCOUNT, PULLING_CALL, AUDIO_PROCESSING: on its way to active
            else -> DIALING
        }
    }
}

data class CallEntry(val id: String, val number: String?, val phase: CallPhase, val incoming: Boolean)

/** The buttons of the placeholder call screen. */
enum class CallAction { ANSWER, DECLINE, HANG_UP }

/** What the call screen shows. */
sealed class CallScreen {
    open val actions: Set<CallAction> get() = emptySet()

    object None : CallScreen()

    /** [waiting]: another call is already in progress (call waiting). */
    data class Incoming(val number: String?, val waiting: Boolean) : CallScreen() {
        override val actions get() = setOf(CallAction.ANSWER, CallAction.DECLINE)
    }

    /** [others]: further live calls (on hold, say) besides this one. */
    data class Ongoing(val number: String?, val phase: CallPhase, val incoming: Boolean, val others: Int) : CallScreen() {
        override val actions get() = setOf(CallAction.HANG_UP)
    }

    /** Telecom reported the call disconnected but hasn't removed it yet. */
    data class Ended(val number: String?) : CallScreen()
}

/**
 * The calls Telecom handed to Yoke's InCallService, and which one the call screen shows. Pure
 * Kotlin so the state machine is unit-tested: YokeInCallService feeds it, InCallActivity renders it.
 * Emergency calls go through it like any other: it never drops, holds back or rewrites a call.
 */
class CallRegistry {

    private val calls = LinkedHashMap<String, CallEntry>()

    val entries: List<CallEntry> get() = calls.values.toList()

    /** A call that arrives ringing is incoming; anything else was placed from this phone. */
    fun added(id: String, number: String?, phase: CallPhase): CallEntry {
        val entry = CallEntry(id, number, phase, incoming = phase == CallPhase.RINGING)
        calls[id] = entry
        return entry
    }

    /** Unknown ids are ignored (a late callback after removal); an ended call stays ended. */
    fun changed(id: String, phase: CallPhase) {
        val entry = calls[id] ?: return
        if (entry.phase == CallPhase.ENDED) return
        calls[id] = entry.copy(phase = phase, incoming = entry.incoming || phase == CallPhase.RINGING)
    }

    fun removed(id: String) {
        calls.remove(id)
    }

    /** The call the screen shows and the buttons act on: a ringing one first, then the live one. */
    fun focus(): CallEntry? {
        val all = calls.values
        return all.firstOrNull { it.phase == CallPhase.RINGING }
            ?: all.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: all.firstOrNull { it.phase == CallPhase.DIALING }
            ?: all.firstOrNull { it.phase == CallPhase.HOLDING }
            ?: all.lastOrNull()
    }

    fun screen(): CallScreen {
        val focus = focus() ?: return CallScreen.None
        val others = calls.values.count { it.id != focus.id && it.phase != CallPhase.ENDED }
        return when (focus.phase) {
            CallPhase.RINGING -> CallScreen.Incoming(focus.number, waiting = others > 0)
            CallPhase.ENDED -> CallScreen.Ended(focus.number)
            else -> CallScreen.Ongoing(focus.number, focus.phase, focus.incoming, others)
        }
    }
}
