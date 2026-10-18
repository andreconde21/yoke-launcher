package com.outsmartis.yoke.phone

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRegistryTest {

    @Test
    fun telecomStatesMapToPhases() {
        assertEquals(CallPhase.RINGING, CallPhase.of(Call.STATE_RINGING))
        assertEquals(CallPhase.RINGING, CallPhase.of(Call.STATE_SIMULATED_RINGING))
        assertEquals(CallPhase.ACTIVE, CallPhase.of(Call.STATE_ACTIVE))
        assertEquals(CallPhase.HOLDING, CallPhase.of(Call.STATE_HOLDING))
        assertEquals(CallPhase.ENDED, CallPhase.of(Call.STATE_DISCONNECTING))
        assertEquals(CallPhase.ENDED, CallPhase.of(Call.STATE_DISCONNECTED))
        for (s in listOf(Call.STATE_NEW, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT)) {
            assertEquals(CallPhase.DIALING, CallPhase.of(s))
        }
    }

    @Test
    fun emptyRegistryShowsNothing() {
        val r = CallRegistry()
        assertEquals(CallScreen.None, r.screen())
        assertNull(r.focus())
        assertTrue(CallScreen.None.actions.isEmpty())
    }

    @Test
    fun incomingCallAnsweredThenHungUp() {
        val r = CallRegistry()
        r.added("a", "5551234", CallPhase.RINGING)
        assertEquals(CallScreen.Incoming("5551234", waiting = false), r.screen())
        assertEquals(setOf(CallAction.ANSWER, CallAction.DECLINE), r.screen().actions)

        r.changed("a", CallPhase.ACTIVE)
        assertEquals(CallScreen.Ongoing("5551234", CallPhase.ACTIVE, incoming = true, others = 0), r.screen())
        assertEquals(setOf(CallAction.HANG_UP), r.screen().actions)

        r.changed("a", CallPhase.ENDED)
        assertEquals(CallScreen.Ended("5551234"), r.screen())
        assertTrue(r.screen().actions.isEmpty())

        r.removed("a")
        assertEquals(CallScreen.None, r.screen())
    }

    @Test
    fun outgoingCallDialsThenConnects() {
        val r = CallRegistry()
        r.added("a", "112", CallPhase.DIALING)
        assertEquals(CallScreen.Ongoing("112", CallPhase.DIALING, incoming = false, others = 0), r.screen())
        r.changed("a", CallPhase.ACTIVE)
        assertEquals(CallPhase.ACTIVE, (r.screen() as CallScreen.Ongoing).phase)
    }

    @Test
    fun callAddedBeforeRingingStillCountsAsIncoming() {
        val r = CallRegistry()
        r.added("a", "5551234", CallPhase.DIALING)
        r.changed("a", CallPhase.RINGING)
        r.changed("a", CallPhase.ACTIVE)
        assertTrue((r.screen() as CallScreen.Ongoing).incoming)
    }

    @Test
    fun waitingCallTakesTheScreenUntilAnswered() {
        val r = CallRegistry()
        r.added("a", "111", CallPhase.ACTIVE)
        r.added("b", "222", CallPhase.RINGING)
        assertEquals(CallScreen.Incoming("222", waiting = true), r.screen())
        assertEquals("b", r.focus()?.id)

        // Answering the waiting call puts the first one on hold.
        r.changed("a", CallPhase.HOLDING)
        r.changed("b", CallPhase.ACTIVE)
        assertEquals(CallScreen.Ongoing("222", CallPhase.ACTIVE, incoming = true, others = 1), r.screen())

        r.changed("b", CallPhase.ENDED)
        r.removed("b")
        assertEquals(CallScreen.Ongoing("111", CallPhase.HOLDING, incoming = false, others = 0), r.screen())
    }

    @Test
    fun declinedWaitingCallLeavesTheLiveOne() {
        val r = CallRegistry()
        r.added("a", "111", CallPhase.ACTIVE)
        r.added("b", "222", CallPhase.RINGING)
        r.changed("b", CallPhase.ENDED)
        assertEquals("a", r.focus()?.id)
        assertEquals(CallScreen.Ongoing("111", CallPhase.ACTIVE, incoming = false, others = 0), r.screen())
    }

    @Test
    fun endedIsFinalAndUnknownIdsAreIgnored() {
        val r = CallRegistry()
        r.added("a", null, CallPhase.RINGING)
        r.changed("a", CallPhase.ENDED)
        r.changed("a", CallPhase.ACTIVE)
        assertEquals(CallScreen.Ended(null), r.screen())
        r.changed("ghost", CallPhase.ACTIVE)
        r.removed("ghost")
        assertEquals(1, r.entries.size)
    }
}
