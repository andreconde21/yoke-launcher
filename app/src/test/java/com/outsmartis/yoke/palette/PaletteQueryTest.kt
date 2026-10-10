package com.outsmartis.yoke.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class PaletteQueryTest {

    @Test
    fun plainTextIsAppsMode() {
        assertEquals(PaletteQuery(PaletteMode.APPS, "mail"), PaletteQuery.parse("mail"))
        assertEquals(PaletteQuery(PaletteMode.APPS, ""), PaletteQuery.parse(""))
        assertEquals(PaletteQuery(PaletteMode.APPS, ""), PaletteQuery.parse(null))
    }

    @Test
    fun prefixesRouteToTheirMode() {
        assertEquals(PaletteQuery(PaletteMode.CALC, "2+2"), PaletteQuery.parse("=2+2"))
        assertEquals(PaletteQuery(PaletteMode.ACTIONS, "set"), PaletteQuery.parse(">set"))
        assertEquals(PaletteQuery(PaletteMode.SHORTCUTS, "new"), PaletteQuery.parse("/new"))
    }

    @Test
    fun prefixAloneGivesEmptyText() {
        assertEquals(PaletteQuery(PaletteMode.CALC, ""), PaletteQuery.parse("="))
        assertEquals(PaletteQuery(PaletteMode.ACTIONS, ""), PaletteQuery.parse(">"))
        assertEquals(PaletteQuery(PaletteMode.SHORTCUTS, ""), PaletteQuery.parse("/"))
    }

    @Test
    fun spaceAfterPrefixIsTrimmed() {
        assertEquals(PaletteQuery(PaletteMode.ACTIONS, "add link"), PaletteQuery.parse(">  add link"))
    }

    @Test
    fun prefixOnlyCountsAsFirstCharacter() {
        assertEquals(PaletteMode.APPS, PaletteQuery.parse(" =1").mode)
        assertEquals(PaletteMode.APPS, PaletteQuery.parse("a=1").mode)
        assertEquals(PaletteMode.APPS, PaletteQuery.parse("!ddg").mode)
    }

    @Test
    fun webSearchUrlEncodesQuery() {
        val url = PaletteQuery.webSearchUrl(" kotlin & co ")
        assertTrue(url, url.endsWith("?q=kotlin%20%26%20co"))
    }

    @After
    fun cleanRegistry() = PaletteActions.clear()

    @Test
    fun actionRegistrySearchesByLabelAndReplacesById() {
        PaletteActions.clear()
        PaletteActions.register(PaletteAction("settings", "Settings") {})
        PaletteActions.register(PaletteAction("add_link", "Add link") {})
        PaletteActions.register(PaletteAction("settings", "Yoke settings") {})
        assertEquals(listOf("Yoke settings", "Add link"), PaletteActions.all().map { it.label })
        assertEquals(listOf("add_link"), PaletteActions.search("link").map { it.id })
        assertEquals(2, PaletteActions.search("").size)
        assertTrue(PaletteActions.search("zzz").isEmpty())
    }
}
