package com.outsmartis.yoke.cockpit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CockpitCardsTest {

    private val today = LocalDate.of(2026, 10, 4)
    private fun col(rule: String?, id: String = "x") = CockpitColumn(id, id, "#000000", rule)

    @Test
    fun columnRulesMatchThePluginsDropUpdates() {
        assertEquals(ColumnFields("", "", null), CockpitCards.fieldsFor(col("no-date"), today))
        assertEquals(ColumnFields("", "", "work"), CockpitCards.fieldsFor(col("no-date label:work"), today))
        assertEquals(ColumnFields("", "", null), CockpitCards.fieldsFor(col("no-date NOT label:work"), today))
        assertEquals(ColumnFields("scheduled", "2026-10-04", null), CockpitCards.fieldsFor(col("date:today"), today))
        assertEquals(ColumnFields("scheduled", "2026-10-05", null), CockpitCards.fieldsFor(col("date:tomorrow"), today))
        assertEquals(ColumnFields("scheduled", "", null), CockpitCards.fieldsFor(col("date:future"), today))
        assertEquals(ColumnFields("in-progress", "", null), CockpitCards.fieldsFor(col("status:in-progress"), today))
        assertEquals(ColumnFields("waiting", "", null), CockpitCards.fieldsFor(col(null, id = "waiting"), today))
    }

    @Test
    fun slugFollowsCreateCardInColumn() {
        assertEquals("call-the-bank", CockpitCards.slug("Call the Bank"))
        assertEquals("ligar-me", CockpitCards.slug("Ligar à mãe"))
        assertEquals("card", CockpitCards.slug("日本"))
        assertEquals(60, CockpitCards.slug("a".repeat(80)).length)
    }

    @Test
    fun pathSkipsTakenNames() {
        val taken = setOf("Tasks/Active/x.md", "Tasks/Active/x-1.md")
        assertEquals("Tasks/Active/x-2.md", CockpitCards.pathFor("Tasks/Active", "X") { it in taken })
        assertEquals("x.md", CockpitCards.pathFor("", "X") { false })
    }

    @Test
    fun contentIsThePluginsCardFormat() {
        val body = CockpitCards.content("Say \"hi\"", ColumnFields("scheduled", "2026-10-04", null), today)
        assertEquals(
            "---\ntitle: \"Say \\\"hi\\\"\"\nstatus: scheduled\ndue: 2026-10-04\ntime:\ncompleted:\nproject:\n" +
                "labels: []\ncreated: 2026-10-04\nsource: yoke\n---\n\n# Say \"hi\"\n",
            body,
        )
    }

    @Test
    fun parsesDataJsonWithDefaults() {
        val cfg = CockpitBoardConfig.parse("""{"folder":"/Tasks/Active/","columns":[{"id":"inbox","label":"Inbox","color":"#fff","rule":null}]}""")
        assertEquals("Tasks/Active", cfg.folder)
        assertEquals(listOf(CockpitColumn("inbox", "Inbox", "#fff", null)), cfg.columns)
        assertEquals(CockpitBoardConfig.DEFAULT_COLUMNS, CockpitBoardConfig.parse("{}").columns)
    }

    @Test
    fun pickedDueDateSchedulesTheCard() {
        val backlog = CockpitCards.fieldsFor(col("no-date"), today)
        assertEquals(ColumnFields("scheduled", "2026-10-09", null), CockpitCards.withDue(backlog, LocalDate.of(2026, 10, 9)))
        assertEquals(backlog, CockpitCards.withDue(backlog, null))
        val labelled = CockpitCards.fieldsFor(col("no-date label:work"), today)
        assertEquals(ColumnFields("scheduled", "2026-10-04", "work"), CockpitCards.withDue(labelled, today))
        val inProgress = CockpitCards.fieldsFor(col("status:in-progress"), today)
        assertEquals(ColumnFields("in-progress", "2026-10-05", null), CockpitCards.withDue(inProgress, today.plusDays(1)))
    }

    @Test
    fun dateColumnsAreRecognised() {
        assertEquals(
            listOf("scheduled", "soon", "today"),
            CockpitBoardConfig.DEFAULT_COLUMNS.filter { CockpitCards.isDateColumn(it) }.map { it.id },
        )
    }
}
