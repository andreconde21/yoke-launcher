package com.outsmartis.yoke.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusDotsTest {

    private class Row(private val v: Map<String, Any?>) : RowSource {
        override fun string(column: String) = v[column] as? String
        override fun int(column: String) = (v[column] as? Number)?.toInt()
        override fun long(column: String) = (v[column] as? Number)?.toLong()
    }

    private fun summary(vararg pairs: Pair<String, Any?>) = DetailsMapper.summary(Row(mapOf(*pairs)))

    @Test fun mapsContractV4Columns() {
        val s = summary("monitoring" to 1, "attention_count" to 2, "contract_version" to 4, "badge_count" to 3,
            "badge_level" to "warn", "summary_text" to "184 up · 3 pending", "empty_text" to "All up")
        assertEquals(3, s.badgeCount); assertEquals("warn", s.badgeLevel)
        assertEquals("184 up · 3 pending", s.summaryText); assertEquals("All up", s.emptyText)
    }

    @Test fun olderProvidersHaveNoV4Fields() {
        val s = summary("monitoring" to 1, "attention_count" to 1)
        assertNull(s.badgeCount); assertNull(s.badgeLevel); assertNull(s.summaryText); assertNull(s.emptyText)
        assertNull(summary("badge_count" to -1, "summary_text" to " ").badgeCount)
    }

    @Test fun summaryTextReplacesTheAgentsLine() {
        val s = summary("monitoring" to 0, "summary_text" to "Not set up")
        assertEquals("Not set up", DetailsText.summaryLine(s, 5))
        assertEquals("2 agents · 1 needs you", DetailsText.summaryLine(summary("monitoring" to 1, "attention_count" to 1), 2))
    }

    @Test fun dotFromProviderLevel() {
        assertEquals(Dot(DotLevel.Alert, 2), StatusDotLogic.dot(summary("monitoring" to 1, "badge_count" to 2, "badge_level" to "alert")))
        assertEquals(Dot(DotLevel.Ok, null), StatusDotLogic.dot(summary("monitoring" to 1, "badge_count" to 0, "badge_level" to "ok")))
        assertEquals(Dot(DotLevel.Stale, null), StatusDotLogic.dot(summary("monitoring" to 0, "badge_level" to "stale")))
    }

    @Test fun conductoreStyleDotFromAttentionCount() {
        assertEquals(Dot(DotLevel.Alert, 1), StatusDotLogic.dot(summary("monitoring" to 1, "attention_count" to 1)))
        assertEquals(Dot(DotLevel.Ok, null), StatusDotLogic.dot(summary("monitoring" to 1, "attention_count" to 0)))
        assertEquals(Dot(DotLevel.Stale, null), StatusDotLogic.dot(summary("monitoring" to 0)))
        assertEquals(Dot(DotLevel.Stale, null), StatusDotLogic.dot(null))
    }

    @Test fun textAndQuietMode() {
        assertEquals("● 2", StatusDotLogic.text(Dot(DotLevel.Alert, 2)))
        assertEquals("●", StatusDotLogic.text(Dot(DotLevel.Ok, null)))
        assertTrue(StatusDotLogic.visible(Dot(DotLevel.Ok, null), quiet = false))
        assertFalse(StatusDotLogic.visible(Dot(DotLevel.Ok, null), quiet = true))
        assertTrue(StatusDotLogic.visible(Dot(DotLevel.Stale, null), quiet = true))
        assertTrue(StatusDotLogic.visible(Dot(DotLevel.Warn, 1), quiet = true))
    }
}
