package com.outsmartis.yoke.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentGroupsTest {
    private fun item(id: String, state: ItemState, project: String? = null, updated: Long = 0L, subtitle: String? = null) =
        DetailsItem(id, "Agent $id", subtitle, state, null, updated, null, project = project)

    @Test fun groupsByProjectInProviderOrderWithOtherLast() {
        val items = listOf(
            item("a", ItemState.Working), item("b", ItemState.Working, "Cockpit"),
            item("c", ItemState.NeedsInput, "Yoke"), item("d", ItemState.Working, "Cockpit"),
        )
        val g = AgentGroups.group(items, DetailsFilter.All, "")
        assertEquals(listOf("Cockpit", "Yoke", null), g.map { it.name })
        assertEquals(listOf("b", "d"), g[0].items.map { it.id })
    }

    @Test fun withoutProjectsItIsOneBoxUrgentFirst() {
        val items = listOf(item("a", ItemState.Working, updated = 5), item("b", ItemState.NeedsInput, updated = 1))
        val g = AgentGroups.group(items, DetailsFilter.All, "")
        assertEquals(1, g.size)
        assertEquals(null, g[0].name)
        assertEquals(listOf("b", "a"), g[0].items.map { it.id })
    }

    @Test fun searchHidesProjectsWithoutMatches() {
        val items = listOf(item("a", ItemState.Working, "Cockpit", subtitle = "claude · cockpit-board"), item("b", ItemState.Working, "Yoke"))
        val g = AgentGroups.group(items, DetailsFilter.All, "board")
        assertEquals(listOf("Cockpit"), g.map { it.name })
    }

    @Test fun activeFilterDropsFinishedBeforeGrouping() {
        val items = listOf(item("a", ItemState.Finished, "Yoke"), item("b", ItemState.Working, "Cockpit"))
        assertEquals(listOf("Cockpit"), AgentGroups.group(items, DetailsFilter.Active, "").map { it.name })
    }

    @Test fun finishedAndIdleAreDimmed() {
        assertTrue(AgentGroups.dimmed(item("a", ItemState.Finished)))
        assertTrue(AgentGroups.dimmed(item("a", ItemState.Idle)))
        assertEquals(false, AgentGroups.dimmed(item("a", ItemState.NeedsInput)))
    }
}
