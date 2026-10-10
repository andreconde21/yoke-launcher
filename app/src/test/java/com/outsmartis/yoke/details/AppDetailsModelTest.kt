package com.outsmartis.yoke.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetailsModelTest {

    private class Row(private val v: Map<String, Any?>) : RowSource {
        override fun string(column: String) = v[column] as? String
        override fun int(column: String) = (v[column] as? Number)?.toInt()
        override fun long(column: String) = (v[column] as? Number)?.toLong()
    }

    private fun item(state: String, at: Long, id: String = "x") =
        DetailsMapper.item(Row(mapOf("id" to id, "title" to id, "state" to state, "updated_at" to at)))

    @Test fun itemMapsAllColumns() {
        val i = DetailsMapper.item(
            Row(mapOf("id" to "h/a", "title" to "build", "subtitle" to "dev · Working", "state" to "working",
                "progress" to 40, "updated_at" to 99L, "deep_link" to "intent:#Intent;end"))
        )
        assertEquals("h/a", i.id); assertEquals("build", i.title); assertEquals("dev · Working", i.subtitle)
        assertEquals(ItemState.Working, i.state); assertEquals(40, i.progress)
        assertEquals(99L, i.updatedAt); assertEquals("intent:#Intent;end", i.deepLink)
    }

    @Test fun nullsAndMinusOneProgress() {
        val i = DetailsMapper.item(Row(mapOf("state" to "working", "progress" to -1)))
        assertEquals("", i.title); assertNull(i.subtitle); assertNull(i.deepLink); assertNull(i.progress)
        assertEquals(0L, i.updatedAt)
        assertNull(DetailsMapper.item(Row(mapOf("subtitle" to "  ", "deep_link" to ""))).subtitle)
    }

    @Test fun unknownStates() {
        assertEquals(ItemState.Unknown, ItemState.parse(null))
        assertEquals(ItemState.Unknown, ItemState.parse("done"))
        assertEquals(ItemState.Unknown, ItemState.parse("garbage"))
        assertEquals(ItemState.Finished, ItemState.parse("finished"))
        assertEquals(ItemState.NeedsInput, ItemState.parse("needsInput"))
        assertTrue(ItemState.Blocked.urgent); assertFalse(ItemState.Working.urgent)
    }

    @Test fun summaryMapsAndHidesUnknownLimits() {
        val s = DetailsMapper.summary(Row(mapOf("monitoring" to 1, "attention_count" to 2, "updated_at" to 5L, "limit_5h_pct" to -1, "limit_7d_pct" to 30)))
        assertTrue(s.monitoring); assertEquals(2, s.attentionCount)
        assertNull(s.limit5hPct); assertEquals(30, s.limit7dPct)
        assertFalse(DetailsMapper.summary(Row(emptyMap())).monitoring)
    }

    @Test fun urgentFirstThenNewest() {
        val sorted = DetailsMapper.order(
            listOf(item("working", 50, "w-new"), item("blocked", 10, "b-old"), item("idle", 90, "i"), item("needsInput", 20, "n-new"))
        ).map { it.id }
        assertEquals(listOf("n-new", "b-old", "i", "w-new"), sorted)
    }

    @Test fun orderIsStableOnTies() {
        assertEquals(listOf("a", "b"), DetailsMapper.order(listOf(item("idle", 5, "a"), item("idle", 5, "b"))).map { it.id })
    }

    @Test fun summaryLine() {
        val s = DetailsSummary(true, 1, 0, 42, 10)
        assertEquals("3 agents · 1 needs you · 5h 42% · 7d 10%", DetailsText.summaryLine(s, 3))
        assertEquals("1 agent", DetailsText.summaryLine(s.copy(attentionCount = 0, limit5hPct = null, limit7dPct = null), 1))
        assertEquals("2 agents · 7d 0%", DetailsText.summaryLine(s.copy(attentionCount = 0, limit5hPct = null, limit7dPct = 0), 2))
        assertEquals("", DetailsText.summaryLine(s.copy(monitoring = false), 3))
        assertEquals("", DetailsText.summaryLine(null, 3))
    }

    @Test fun relativeTime() {
        val now = 10_000_000_000L
        assertEquals("", DetailsText.relativeTime(0, now))
        assertEquals("now", DetailsText.relativeTime(now - 30_000, now))
        assertEquals("now", DetailsText.relativeTime(now + 5_000, now))
        assertEquals("2m ago", DetailsText.relativeTime(now - 150_000, now))
        assertEquals("3h ago", DetailsText.relativeTime(now - 3 * 3600_000L, now))
        assertEquals("2d ago", DetailsText.relativeTime(now - 2 * 86400_000L, now))
        assertEquals("updated 2m ago", DetailsText.updated(now - 120_000, now))
        assertEquals("updated now", DetailsText.updated(now, now))
        assertEquals("", DetailsText.updated(0, now))
    }

    private fun v2(state: String = "needsInput", vararg extra: Pair<String, Any?>) =
        DetailsMapper.item(Row(mapOf("id" to "h/a", "title" to "a", "state" to state) + extra))

    @Test fun v1RowHasNoReplyFields() {
        val i = item("needsInput", 1)
        assertNull(i.question); assertNull(i.options); assertFalse(i.answerable); assertNull(i.answerNote)
        assertEquals(1, DetailsMapper.summary(Row(mapOf("monitoring" to 1))).contractVersion)
        assertEquals(ReplyUi.None, ReplyUi.of(i, 1))
    }

    @Test fun v2ColumnsAndSummaryVersion() {
        val i = v2("needsInput", "question" to "Run rm?", "options" to "[\"Allow\",\"Always allow\",\"Deny\"]", "answerable" to 1)
        assertEquals("Run rm?", i.question); assertEquals(listOf("Allow", "Always allow", "Deny"), i.options); assertTrue(i.answerable)
        assertEquals(2, DetailsMapper.summary(Row(mapOf("contract_version" to 2))).contractVersion)
    }

    @Test fun malformedOptionsAreNull() {
        listOf(null, "", "nope", "{\"a\":1}", "[1,2]", "[]", "[\"ok\",\"\"]", "[\"a\",").forEach {
            assertNull(it, DetailsMapper.parseOptions(it))
        }
    }

    @Test fun replyUiChoices() {
        val choices = v2("blocked", "options" to "[\"Allow\",\"Deny\"]", "answerable" to 1)
        assertEquals(ReplyUi.Choices(listOf("Allow", "Deny")), ReplyUi.of(choices, 2))
        assertEquals(ReplyUi.None, ReplyUi.of(choices, 1))
        assertTrue(ReplyUi.isSecondary("Deny")); assertFalse(ReplyUi.isSecondary("Allow"))
    }

    @Test fun replyUiTextNoteAndNonUrgent() {
        assertEquals(ReplyUi.TextField, ReplyUi.of(v2("needsInput", "question" to "q", "answerable" to 1), 2))
        assertEquals(ReplyUi.Note("why"), ReplyUi.of(v2("needsInput", "answerable" to 0, "answer_note" to "why"), 2))
        assertEquals(ReplyUi.None, ReplyUi.of(v2("needsInput", "answerable" to 0), 2))
        assertEquals(ReplyUi.None, ReplyUi.of(v2("working", "answerable" to 1), 2))
    }

    @Test fun replyOutcomeMapping() {
        val o = AppDetailsClient.ReplyOutcome
        assertEquals(AppDetailsClient.ReplyResult.Ok, o.of(true, true, null, false))
        assertEquals(AppDetailsClient.ReplyResult.Queued, o.of(true, true, null, true))
        assertEquals(AppDetailsClient.ReplyResult.Failed("gone"), o.of(true, false, "gone", false))
        assertTrue(o.of(true, false, null, false) is AppDetailsClient.ReplyResult.Failed)
        assertTrue(o.of(false, false, null, false) is AppDetailsClient.ReplyResult.Failed)
    }
}
