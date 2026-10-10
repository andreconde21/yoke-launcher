package com.outsmartis.yoke.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CockpitAgendaTest {

    private val today = LocalDate.of(2026, 10, 5)

    private fun card(title: String, status: String = "scheduled", due: String = "2026-10-05", time: String = "") =
        "---\ntitle: \"$title\"\nstatus: $status\ndue: $due\ntime: $time\ncompleted:\nproject:\nlabels: [\"work\"]\ncreated: 2026-10-01\nsource: yoke\n---\n\n# $title\n"

    @Test
    fun parsesRealCard() {
        val h = CockpitAgenda.parseHead(card("Call accountant", due = "2026-10-05", time = "10:00"))!!
        assertEquals(CardHead("Call accountant", "scheduled", today, "10:00"), h)
    }

    @Test
    fun quotedTitleWithEscapedQuotesAndColon() {
        val text = "---\ntitle: \"Say \\\"hi\\\": now\"\nstatus:\ndue:\ntime:\n---\n"
        val h = CockpitAgenda.parseHead(text)!!
        assertEquals("Say \"hi\": now", h.title)
        assertEquals("", h.status)
        assertNull(h.due)
        assertEquals("", h.time)
    }

    @Test
    fun singleQuotedAndPlainTitles() {
        assertEquals("It's", CockpitAgenda.parseHead("---\ntitle: 'It''s'\n---\n")!!.title)
        assertEquals("Plain one", CockpitAgenda.parseHead("---\ntitle: Plain one\n---\n")!!.title)
    }

    @Test
    fun crlfAndBom() {
        val text = "﻿" + card("Pay rent", time = "09:30").replace("\n", "\r\n")
        val h = CockpitAgenda.parseHead(text)!!
        assertEquals("Pay rent", h.title)
        assertEquals("09:30", h.time)
        assertEquals(today, h.due)
    }

    @Test
    fun noFrontmatterOrBadFieldsGiveNullOrNone() {
        assertNull(CockpitAgenda.parseHead("# just a note\n"))
        val h = CockpitAgenda.parseHead("---\ntitle: x\ndue: someday\ntime: noon\n---\n")!!
        assertNull(h.due)
        assertEquals("", h.time)
    }

    @Test
    fun truncatedHeadStillParses() {
        val h = CockpitAgenda.parseHead("---\ntitle: \"Cut off\"\nstatus: scheduled\ndue: 2026-10-05\ntim")!!
        assertEquals("Cut off", h.title)
        assertEquals(today, h.due)
    }

    @Test
    fun nothingDueGivesEmptyLine() {
        val texts = listOf(card("Later", due = "2026-10-06"), card("No date", due = ""), "# note")
        assertEquals("", CockpitAgenda.lineFromTexts(texts, today))
        assertEquals("", CockpitAgenda.lineFromTexts(emptyList(), today))
    }

    @Test
    fun todayWithTimeAndMore() {
        val texts = listOf(
            card("Write report", due = "2026-10-05"),
            card("Call accountant", time = "10:00"),
            card("Gym", time = "18:00"),
        )
        assertEquals("Today · 10:00 Call accountant · +2", CockpitAgenda.lineFromTexts(texts, today))
    }

    @Test
    fun singleUntimedHasNoCount() {
        assertEquals("Today · Write report", CockpitAgenda.lineFromTexts(listOf(card("Write report")), today))
    }

    @Test
    fun timedBeforeUntimedWithinADay() {
        val sel = CockpitAgenda.select(
            listOf(CardHead("b", "", today, ""), CardHead("a", "", today, "23:00"), CardHead("c", "", today, "07:05")), today,
        )
        assertEquals(listOf("c", "a", "b"), sel.map { it.title })
    }

    @Test
    fun overdueComesFirstAndIsPrefixed() {
        val texts = listOf(card("Today thing", time = "08:00"), card("Old thing", due = "2026-10-03", time = "09:00"))
        assertEquals("Overdue · Old thing · +1", CockpitAgenda.lineFromTexts(texts, today))
    }

    @Test
    fun doneArchivedAndFutureExcluded() {
        val texts = listOf(
            card("Finished", status = "done"),
            card("Filed", status = "Archived"),
            card("Tomorrow", due = "2026-10-06"),
            card("Open", status = "in-progress"),
        )
        assertEquals("Today · Open", CockpitAgenda.lineFromTexts(texts, today))
    }
}
