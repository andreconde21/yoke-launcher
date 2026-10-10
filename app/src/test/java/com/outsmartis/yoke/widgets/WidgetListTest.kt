package com.outsmartis.yoke.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetListTest {

    private val list = WidgetList().add(WidgetEntry(1, 120)).add(WidgetEntry(2, 240)).add(WidgetEntry(3, 400))

    @Test
    fun addKeepsOrderAndIgnoresDuplicates() {
        assertEquals(listOf(1, 2, 3), list.ids)
        assertEquals(listOf(1, 2, 3), list.add(WidgetEntry(2, 999)).ids)
    }

    @Test
    fun moveShiftsAndClamps() {
        assertEquals(listOf(2, 1, 3), list.move(2, -1).ids)
        assertEquals(listOf(1, 3, 2), list.move(2, 1).ids)
        assertEquals(list, list.move(1, -1))
        assertEquals(list, list.move(3, 1))
        assertEquals(list, list.move(42, 1))
    }

    @Test
    fun resizeChangesOnlyThatWidget() {
        val r = list.resize(2, 400)
        assertEquals(listOf(120, 400, 400), r.entries.map { it.heightDp })
    }

    @Test
    fun removeAndRetain() {
        assertEquals(listOf(1, 3), list.remove(2).ids)
        assertEquals(listOf(2), list.retain { it == 2 }.ids)
    }

    @Test
    fun jsonRoundTripKeepsOrderAndHeights() {
        val back = WidgetList.parse(list.move(3, -2).resize(1, 240).toJson())
        assertEquals(listOf(3, 1, 2), back.ids)
        assertEquals(listOf(400, 240, 240), back.entries.map { it.heightDp })
    }

    @Test
    fun parseIsLenient() {
        assertTrue(WidgetList.parse(null).entries.isEmpty())
        assertTrue(WidgetList.parse("not json").entries.isEmpty())
        assertTrue(WidgetList.parse("{}").entries.isEmpty())
        assertEquals(listOf(5), WidgetList.parse("""{"widgets":[{"id":5},{"x":1},{"id":5,"height":99}]}""").ids)
    }
}
