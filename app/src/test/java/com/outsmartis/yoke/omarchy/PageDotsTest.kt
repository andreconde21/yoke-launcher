package com.outsmartis.yoke.omarchy

import com.outsmartis.yoke.gestures.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDotsTest {
    @Test fun homeOnlyWhenNothingOpensTheOtherPages() =
        assertEquals(listOf(Page.HOME), PageDots.pages(listOf(GestureAction.AppDrawer), agendaLine = false))

    @Test fun widgetPageWhenAGestureOpensIt() =
        assertEquals(listOf(Page.HOME, Page.WIDGETS), PageDots.pages(listOf(GestureAction.WidgetPage), agendaLine = false))

    @Test fun todayFromAGestureOrTheAgendaLine() {
        assertEquals(listOf(Page.HOME, Page.TODAY), PageDots.pages(listOf(GestureAction.CockpitToday), agendaLine = false))
        assertEquals(listOf(Page.HOME, Page.TODAY), PageDots.pages(emptyList(), agendaLine = true))
    }

    @Test fun allThreeInOrder() = assertEquals(
        listOf(Page.HOME, Page.WIDGETS, Page.TODAY),
        PageDots.pages(listOf(GestureAction.CockpitToday, GestureAction.WidgetPage), agendaLine = false),
    )

    @Test fun aSinglePageIsNotShown() {
        assertFalse(PageDots.visible(listOf(Page.HOME)))
        assertTrue(PageDots.visible(listOf(Page.HOME, Page.TODAY)))
    }

    @Test fun numbersCountShownPages() {
        assertEquals("1", PageDots.label(0, current = true, style = DotStyle.NUMBERS))
        assertEquals("2", PageDots.label(1, current = false, style = DotStyle.NUMBERS))
    }

    @Test fun dotsFillTheCurrentPage() {
        assertEquals("●", PageDots.label(0, current = true, style = DotStyle.DOTS))
        assertEquals("○", PageDots.label(1, current = false, style = DotStyle.DOTS))
    }

    @Test fun unknownIdsFallBackToDefaults() {
        assertEquals(DotStyle.NUMBERS, DotStyle.fromId("x"))
        assertEquals(Edge.TOP, Edge.fromId(null))
        assertEquals(Edge.BOTTOM, Edge.fromId("bottom"))
    }
}
