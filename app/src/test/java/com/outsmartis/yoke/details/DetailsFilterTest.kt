package com.outsmartis.yoke.details

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsFilterTest {
    private fun item(id: String, state: ItemState, project: String? = null, active: Boolean? = null) =
        DetailsItem(id, id, null, state, null, 0L, null, project = project, active = active)

    private val items = listOf(
        item("a", ItemState.Working, "Yoke"),
        item("b", ItemState.Finished, "Yoke"),
        item("c", ItemState.NeedsInput, "Cockpit"),
        item("d", ItemState.Idle),
    )

    @Test fun activeKeepsWorkingAndWaitingWithoutTheColumn() =
        assertEquals(listOf("a", "c"), DetailsFilter.Active.apply(items).map { it.id })

    @Test fun activeFollowsConductoreWhenItSays() {
        val v3 = listOf(item("x", ItemState.Finished, active = true), item("y", ItemState.Working, active = false))
        assertEquals(listOf("x"), DetailsFilter.Active.apply(v3).map { it.id })
    }

    @Test fun projectKeepsOnlyItsRows() =
        assertEquals(listOf("a", "b"), DetailsFilter.Project("Yoke").apply(items).map { it.id })

    @Test fun projectsAreDistinctInOrder() = assertEquals(listOf("Yoke", "Cockpit"), DetailsFilter.projects(items))

    @Test fun storedKeysRoundTrip() {
        assertEquals(DetailsFilter.All, DetailsFilter.parse("all", items))
        assertEquals(DetailsFilter.Project("Cockpit"), DetailsFilter.parse(DetailsFilter.Project("Cockpit").key, items))
    }

    @Test fun goneProjectOrUnknownFallsBackToActive() {
        assertEquals(DetailsFilter.Active, DetailsFilter.parse("project:Old", items))
        assertEquals(DetailsFilter.Active, DetailsFilter.parse(null, items))
    }

    @Test fun mapperReadsTheV3Columns() {
        val row = object : RowSource {
            val m = mapOf("id" to "1", "state" to "working", "project" to "Yoke", "active" to 1)
            override fun string(column: String) = m[column] as? String
            override fun int(column: String) = m[column] as? Int
            override fun long(column: String) = null
        }
        val i = DetailsMapper.item(row)
        assertEquals("Yoke", i.project)
        assertEquals(true, i.active)
    }

    @Test fun lockedConductoreQueuesTheAnswer() =
        assertEquals(AppDetailsClient.ReplyResult.PendingUnlock, AppDetailsClient.ReplyOutcome.of(true, true, null, true, pendingUnlock = true))
}
