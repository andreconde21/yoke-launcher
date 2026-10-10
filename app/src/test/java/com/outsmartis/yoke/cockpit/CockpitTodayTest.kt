package com.outsmartis.yoke.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CockpitTodayTest {
    private val today = LocalDate.of(2026, 10, 5)

    private val card = "---\ntitle: \"Call\"\nstatus: scheduled\ndue: 2026-10-05\ntime:\nlabels:\n  - a\n  - b\n# note\nproject:\n---\n\n# Call\nstatus: body\n"

    @Test fun replacesInPlaceAndKeepsEverythingElse() {
        val out = CockpitFrontmatter.update(card, mapOf("status" to "done", "due" to "2026-10-06"))!!
        assertEquals(card.replace("status: scheduled", "status: done").replace("due: 2026-10-05", "due: 2026-10-06"), out)
    }

    @Test fun addsMissingKeyBeforeClosingFence() {
        val out = CockpitFrontmatter.update(card, mapOf("completed" to "2026-10-05"))!!
        assertEquals(card.replace("project:\n---", "project:\ncompleted: 2026-10-05\n---"), out)
    }

    @Test fun emptyValueClearsAndNullRemoves() {
        assertEquals(card.replace("due: 2026-10-05", "due:"), CockpitFrontmatter.update(card, mapOf("due" to ""))!!)
        assertEquals(card.replace("due: 2026-10-05\n", ""), CockpitFrontmatter.update(card, mapOf("due" to null))!!)
        // Removing then editing a later key still works (indices shift).
        assertEquals(card.replace("due: 2026-10-05\n", "").replace("status: scheduled", "status: done"),
            CockpitFrontmatter.update(card, mapOf("due" to null, "status" to "done"))!!)
    }

    @Test fun crlfIsPreserved() {
        val crlf = card.replace("\n", "\r\n")
        val out = CockpitFrontmatter.update(crlf, mapOf("status" to "done", "completed" to "2026-10-05"))!!
        assertEquals(crlf.replace("status: scheduled", "status: done").replace("project:\r\n---", "project:\r\ncompleted: 2026-10-05\r\n---"), out)
        assertTrue(!out.replace("\r\n", "").contains('\n'))
    }

    @Test fun multiLineListAndBodyUntouched() {
        val out = CockpitFrontmatter.update(card, mapOf("status" to "in-progress"))!!
        assertTrue(out.contains("labels:\n  - a\n  - b\n# note\n"))
        assertTrue(out.endsWith("---\n\n# Call\nstatus: body\n"))
    }

    @Test fun noTrailingNewlineAndIdenticalValueUntouched() {
        val t = "---\nstatus: done\n---"
        assertEquals(t, CockpitFrontmatter.update(t, mapOf("status" to "done")))
        assertEquals("---\nstatus: done\ndue: x\n---", CockpitFrontmatter.update(t, mapOf("due" to "x")))
    }

    @Test fun noFrontmatterGivesNull() {
        assertNull(CockpitFrontmatter.update("# just a note\n", mapOf("status" to "done")))
        assertNull(CockpitFrontmatter.update("---\nstatus: x\nno closing", mapOf("status" to "done")))
    }

    private fun c(title: String, status: String = "scheduled", due: String? = null, time: String = "") =
        TodayCard("$title.md", "id", title, status, due?.let(LocalDate::parse), time, emptyList(), 0)

    @Test fun sectionsSelectAndSort() {
        val s = CockpitToday.sections(listOf(
            c("late", due = "2026-10-01"), c("b", due = "2026-10-05"), c("a", due = "2026-10-05", time = "09:00"),
            c("done", "done", "2026-10-05"), c("wip", "in-progress"), c("wipFuture", "in-progress", "2026-10-09"),
            c("wipToday", "in-progress", "2026-10-05"), c("later", due = "2026-10-09"), c("untitled", due = "2026-10-05").copy(title = ""),
        ), today)
        assertEquals(listOf("late"), s.overdue.map { it.title })
        assertEquals(listOf("a", "b", "wipToday"), s.today.map { it.title })
        assertEquals(listOf("wipFuture", "wip"), s.inProgress.map { it.title })
        assertTrue(CockpitToday.sections(emptyList(), today).isEmpty)
    }

    @Test fun actionChanges() {
        val plain = c("x", due = "2026-10-05")
        assertEquals(mapOf("status" to "done", "completed" to "2026-10-05"), CockpitToday.changesFor(TodayAction.DONE, plain, today, true))
        assertEquals(mapOf("status" to "in-progress", "due" to ""), CockpitToday.changesFor(TodayAction.START, plain, today, true))
        assertEquals(mapOf("status" to "in-progress"), CockpitToday.changesFor(TodayAction.START, plain, today, false))
        assertEquals(mapOf("status" to "in-progress"), CockpitToday.changesFor(TodayAction.START, plain.copy(time = "10:00"), today, true))
        assertEquals(mapOf("status" to "scheduled", "due" to "2026-10-06"), CockpitToday.changesFor(TodayAction.TOMORROW, plain, today, true))
        assertEquals(mapOf("due" to "2026-10-06"), CockpitToday.changesFor(TodayAction.TOMORROW, plain.copy(status = "in-progress"), today, true))
        assertEquals(mapOf("status" to "scheduled", "due" to "2026-12-24"),
            CockpitToday.changesFor(TodayAction.PICK_DATE, plain, today, true, LocalDate.of(2026, 12, 24)))
    }

    @Test fun optimisticApply() {
        val started = CockpitToday.applyChanges(c("x", due = "2026-10-05"), mapOf("status" to "in-progress", "due" to ""))
        assertEquals("in-progress", started.status)
        assertNull(started.due)
    }

    @Test fun obsidianUriEncodes() {
        assertEquals("obsidian://open?vault=My%20Vault&file=Cards%2Fcall-me%26x", CockpitToday.obsidianUri("My Vault", "Cards/call-me&x.md"))
    }

    @Test fun clearDateSettingParsed() {
        assertTrue(CockpitBoardConfig.parse("{}").clearDateOnInProgress)
        assertEquals(false, CockpitBoardConfig.parse("""{"clearDateOnInProgress":false}""").clearDateOnInProgress)
    }
}
