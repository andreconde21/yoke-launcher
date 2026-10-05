package com.outsmartis.yoke.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogicTest {

    private fun e(
        key: String,
        pkg: String = "a",
        time: Long = 1,
        title: String = "T",
        text: String = "x",
        ongoing: Boolean = false,
        summary: Boolean = false,
        group: String? = null,
        media: Boolean = false,
    ) = NoteEntry(key, pkg, title, text, time, ongoing, summary, group, media)

    @Test fun emptyAllowedSetShowsNothing() {
        assertTrue(NotificationLogic.visible(listOf(e("1")), emptySet()).isEmpty())
    }

    @Test fun onlyAllowedAppsPass() {
        val v = NotificationLogic.visible(listOf(e("1", "a"), e("2", "b")), setOf("b"))
        assertEquals(listOf("2"), v.map { it.key })
    }

    @Test fun ongoingAndMediaAreSkipped() {
        val v = NotificationLogic.visible(listOf(e("1", ongoing = true), e("2", media = true), e("3")), setOf("a"))
        assertEquals(listOf("3"), v.map { it.key })
    }

    @Test fun summaryWithChildrenIsSkippedButLoneSummaryStays() {
        val withKids = listOf(e("s", summary = true, group = "g"), e("c", group = "g"))
        assertEquals(listOf("c"), NotificationLogic.visible(withKids, setOf("a")).map { it.key })
        val lone = listOf(e("s", summary = true, group = "g"))
        assertEquals(listOf("s"), NotificationLogic.visible(lone, setOf("a")).map { it.key })
    }

    @Test fun groupsPerAppNewestFirstAndLimited() {
        val list = listOf(
            e("1", "a", 10), e("2", "b", 30), e("3", "a", 20), e("4", "c", 5), e("5", "d", 1),
        )
        val g = NotificationLogic.group(list, 3)
        assertEquals(listOf("b", "a", "c"), g.map { it.packageName })
        assertEquals(2, g[1].count)
        assertEquals("3", g[1].latest.key)
        assertEquals(listOf("3", "1"), g[1].keys)
    }

    @Test fun maxLinesOnlyThreeOrFive() {
        assertEquals(3, NotificationLogic.normalizeMax(4))
        assertEquals(3, NotificationLogic.normalizeMax(0))
        assertEquals(5, NotificationLogic.normalizeMax(5))
    }

    @Test fun lineFormatsTitleTextAndCount() {
        val g = AppGroup("a", listOf(e("1", time = 2, title = "Anna", text = "hi"), e("2", time = 1)))
        val line = NotificationLogic.line(g, "Signal", true, emptySet())
        assertEquals(2, line.count)
        assertEquals("Signal · 2   Anna: hi", NotificationLogic.plain(line))
    }

    @Test fun singleNotificationHasNoCount() {
        val g = AppGroup("a", listOf(e("1", title = "Anna", text = "hi")))
        assertNull(NotificationLogic.line(g, "Signal", true, emptySet()).count)
        assertEquals("Signal   Anna: hi", NotificationLogic.plain(NotificationLogic.line(g, "Signal", true, emptySet())))
    }

    @Test fun privacyHidesTextGloballyAndPerApp() {
        val g = AppGroup("a", listOf(e("1", title = "Anna", text = "secret")))
        assertEquals("Signal", NotificationLogic.plain(NotificationLogic.line(g, "Signal", false, emptySet())))
        assertEquals("Signal", NotificationLogic.plain(NotificationLogic.line(g, "Signal", true, setOf("a"))))
        assertEquals("Signal   Anna: secret", NotificationLogic.plain(NotificationLogic.line(g, "Signal", true, setOf("b"))))
    }

    @Test fun joinHandlesMissingParts() {
        assertEquals("T", NotificationLogic.joinTitleText("T", " "))
        assertEquals("x y", NotificationLogic.joinTitleText("", "x\n y"))
        assertEquals("", NotificationLogic.joinTitleText("", ""))
    }

    @Test fun clipNeverSplitsSurrogatePair() {
        val s = "ab" + "😀" + "cd"
        assertEquals("ab…", NotificationLogic.clip(s, 4))
        assertEquals("abc", NotificationLogic.clip("abc", 3))
        assertEquals("ab…", NotificationLogic.clip("abcdef", 3))
    }

    @Test fun dndRule() {
        assertFalse(NotificationLogic.hiddenByDnd(true, NotificationLogic.INTERRUPTION_FILTER_ALL))
        assertFalse(NotificationLogic.hiddenByDnd(true, NotificationLogic.INTERRUPTION_FILTER_UNKNOWN))
        assertTrue(NotificationLogic.hiddenByDnd(true, 2))
        assertTrue(NotificationLogic.hiddenByDnd(true, 3))
        assertFalse(NotificationLogic.hiddenByDnd(false, 3))
    }

    @Test fun labelCountSuffix() {
        assertEquals("", NotificationLogic.labelCountSuffix(0, true))
        assertEquals("", NotificationLogic.labelCountSuffix(3, false))
        assertEquals("2", NotificationLogic.labelCountSuffix(2, true))
        assertEquals("99+", NotificationLogic.labelCountSuffix(120, true))
    }

    @Test fun countsPerPackage() {
        val c = NotificationLogic.counts(listOf(e("1", "a"), e("2", "a"), e("3", "b")))
        assertEquals(mapOf("a" to 2, "b" to 1), c)
    }

    @Test fun nowPlayingLine() {
        assertEquals("▶ Song — Band", NotificationLogic.nowPlayingLine("Song", "Band", false))
        assertEquals("❚❚ Song — Band", NotificationLogic.nowPlayingLine("Song", "Band", true))
        assertEquals("▶ Song", NotificationLogic.nowPlayingLine("Song", "", false))
        assertEquals("▶ Band", NotificationLogic.nowPlayingLine("", "Band", false))
    }
}
