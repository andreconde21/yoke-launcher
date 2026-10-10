package com.outsmartis.yoke.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraSearchTest {

    private fun hit(kind: SearchKind, title: String, vararg keywords: String) =
        SearchHit(kind, title, null, "t:$title", keywords.toList())

    @Test fun settingsMatchSynonyms() {
        val hits = SettingsPages.hits { true }
        assertEquals("Wi-Fi", SearchRanking.rank("wifi", hits).first().title)
        assertEquals("Display", SearchRanking.rank("brightness", hits).first().title)
        assertEquals("Do not disturb", SearchRanking.rank("dnd", hits).first().title)
        assertTrue(SearchRanking.rank("yoke", hits).map { it.title }.containsAll(listOf("Yoke settings", "Yoke's app info")))
    }

    @Test fun settingsListHasPlainNamesAndYokePages() {
        assertTrue(SettingsPages.ALL.size >= 25)
        assertTrue(SettingsPages.ALL.any { it.action == SettingsPages.YOKE_SETTINGS })
        assertTrue(SettingsPages.ALL.any { it.action == SettingsPages.YOKE_APP_INFO })
    }

    @Test fun unavailablePagesAreDropped() {
        val hits = SettingsPages.hits { it.name != "NFC" }
        assertTrue(hits.none { it.title == "NFC" })
        assertTrue(SearchRanking.rank("nfc", hits).isEmpty())
    }

    @Test fun matchingIgnoresCaseAndDiacritics() {
        val hits = listOf(hit(SearchKind.CARD, "Reunião de Planeamento"))
        assertEquals(1, SearchRanking.rank("reuniao", hits).size)
        assertEquals(1, SearchRanking.rank("PLANEAM", hits).size)
    }

    @Test fun shortQueryMatchesNothing() {
        assertTrue(SearchRanking.rank("w", listOf(hit(SearchKind.CARD, "Wash car"))).isEmpty())
    }

    @Test fun prefixBeatsWordStartBeatsContainsThenShorterTitle() {
        val hits = listOf(
            hit(SearchKind.NOTE, "Buy cardboard"),
            hit(SearchKind.NOTE, "Card game"),
            hit(SearchKind.NOTE, "Big card ideas"),
            hit(SearchKind.NOTE, "Scorecards"),
        )
        assertEquals(
            listOf("Card game", "Buy cardboard", "Big card ideas", "Scorecards"),
            SearchRanking.rank("card", hits).map { it.title },
        )
    }

    @Test fun eachSourceIsLimitedToFive() {
        val notes = (1..12).map { hit(SearchKind.NOTE, "Note $it") }
        assertEquals(5, SearchRanking.rank("note", notes).size)
        assertEquals(3, SearchRanking.rank("note", notes, limit = 3).size)
    }

    @Test fun combinedOrderIsCardsNotesContactsSettings() {
        val sources = mapOf(
            SearchKind.SETTING to listOf(hit(SearchKind.SETTING, "Sound")),
            SearchKind.CONTACT to listOf(hit(SearchKind.CONTACT, "Sound Guy")),
            SearchKind.NOTE to listOf(hit(SearchKind.NOTE, "Sound notes")),
            SearchKind.CARD to listOf(hit(SearchKind.CARD, "Sound check")),
        )
        assertEquals(
            listOf(SearchKind.CARD, SearchKind.NOTE, SearchKind.CONTACT, SearchKind.SETTING),
            SearchRanking.combine("sound", sources).map { it.kind },
        )
    }

    @Test fun combinedLimitsEachSourceSeparately() {
        val sources = mapOf(
            SearchKind.CARD to (1..9).map { hit(SearchKind.CARD, "Task $it") },
            SearchKind.NOTE to (1..9).map { hit(SearchKind.NOTE, "Task note $it") },
        )
        val rows = SearchRanking.combine("task", sources)
        assertEquals(5, rows.count { it.kind == SearchKind.CARD })
        assertEquals(5, rows.count { it.kind == SearchKind.NOTE })
    }

    @Test fun missingSourceIsSkipped() {
        assertTrue(SearchRanking.combine("sound", emptyMap()).isEmpty())
    }

    @Test fun cardFrontmatterTitleStatusDue() {
        val card = CardFrontmatter.parse("Cards/fix-bike.md", "---\ntitle: \"Fix the \\\"bike\\\"\"\nstatus: in-progress\ndue: 2026-10-06\n---\n\n# x")
        assertEquals("Fix the \"bike\"", card.title)
        assertEquals("card · in-progress · 2026-10-06", card.subtitle)
    }

    @Test fun cardTitleFallsBackToFileName() {
        val card = CardFrontmatter.parse("Cards/sub/call-mum.md", "no frontmatter here")
        assertEquals("call-mum", card.title)
        assertEquals("card", card.subtitle)
        assertEquals("a b", CardFrontmatter.parse("a b.md", "---\ntitle:\nstatus: done\n---").title)
    }

    @Test fun obsidianLinkIsEncodedWithoutExtension() {
        assertEquals(
            "obsidian://open?vault=My%20Vault&file=Cards%2Ffix%20bike%20%26%20co",
            ObsidianLinks.open("My Vault", "Cards/fix bike & co.md"),
        )
    }
}
