package com.outsmartis.yoke.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkEntryTest {

    @Test
    fun jsonRoundTrip() {
        val links = listOf(
            LinkEntry("a", "Hacker News", "https://news.ycombinator.com"),
            LinkEntry("b", "Quote \"x\" é", "http://example.com/?q=1&r=2"),
        )
        assertEquals(links, LinkEntry.listFromJson(LinkEntry.toJson(links)))
    }

    @Test
    fun garbageJsonGivesEmptyList() {
        assertTrue(LinkEntry.listFromJson(null).isEmpty())
        assertTrue(LinkEntry.listFromJson("").isEmpty())
        assertTrue(LinkEntry.listFromJson("not json").isEmpty())
        assertTrue(LinkEntry.listFromJson("{\"a\":1}").isEmpty())
    }

    @Test
    fun malformedEntriesAreDropped() {
        val json = """[{"id":"1","name":"","url":"https://a.com"},{"id":"2","name":"ok","url":"https://b.com"},5]"""
        assertEquals(listOf(LinkEntry("2", "ok", "https://b.com")), LinkEntry.listFromJson(json))
    }

    @Test
    fun normalizeUrlAddsHttps() {
        assertEquals("https://example.com", LinkEntry.normalizeUrl("example.com"))
        assertEquals("https://example.com:8080/x", LinkEntry.normalizeUrl(" example.com:8080/x "))
        assertEquals("http://example.com", LinkEntry.normalizeUrl("http://example.com"))
    }

    @Test
    fun normalizeUrlRejectsNonWebAndBlank() {
        assertNull(LinkEntry.normalizeUrl(""))
        assertNull(LinkEntry.normalizeUrl("javascript:alert(1)"))
        assertNull(LinkEntry.normalizeUrl("mailto:a@b.c"))
        assertNull(LinkEntry.normalizeUrl("intent://x#Intent;end"))
        assertNull(LinkEntry.normalizeUrl("two words.com"))
        assertNull(LinkEntry.normalizeUrl("https://"))
    }

    @Test
    fun pinTokenRoundTrip() {
        val link = LinkEntry("abc", "n", "https://x.y")
        assertEquals("abc", LinkEntry.idFromPinToken(link.pinToken))
        assertNull(LinkEntry.idFromPinToken("com.android.chrome"))
    }
}
