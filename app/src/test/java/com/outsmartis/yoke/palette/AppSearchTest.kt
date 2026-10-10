package com.outsmartis.yoke.palette

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    @Test
    fun matchesAlias() {
        assertTrue(AppSearch.matches("brow", "Browser", "Chrome"))
    }

    @Test
    fun matchesOriginalLabelWhenAliased() {
        assertTrue(AppSearch.matches("chro", "Browser", "Chrome"))
    }

    @Test
    fun noMatchWhenNeitherContainsQuery() {
        assertFalse(AppSearch.matches("mail", "Browser", "Chrome"))
    }

    @Test
    fun ignoresCaseDiacriticsAndSeparators() {
        assertTrue(AppSearch.matches("CAFE", "Café"))
        assertTrue(AppSearch.matches("my app", "My-App"))
    }

    @Test
    fun nullOriginalLabelIsSkipped() {
        assertTrue(AppSearch.matches("a", "Alpha", null))
        assertFalse(AppSearch.matches("z", "Alpha", null))
    }
}
