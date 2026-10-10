package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamSpecTest {
    @Test fun contentDefaultsToTheClock() = assertEquals(DreamContent.CLOCK, DreamContent.fromId(null))

    @Test fun typingRevealsLineByLine() {
        val first = DreamSpec.WORDMARK[0]
        assertEquals(listOf("", ""), DreamSpec.typed(0))
        assertEquals(listOf(first.take(3), ""), DreamSpec.typed(3))
        assertEquals(listOf(first, DreamSpec.WORDMARK[1].take(2)), DreamSpec.typed(first.length + 2))
        assertEquals(DreamSpec.WORDMARK, DreamSpec.typed(DreamSpec.WORDMARK_LENGTH + 50))
    }

    @Test fun shiftStaysInsideTheFreeSpace() {
        for (step in 0L until 200L) {
            val (x, y) = DreamSpec.offset(step, 300, 900)
            assertTrue(x in 0..300 && y in 0..900)
        }
    }

    @Test fun shiftIsStablePerStepAndMoves() {
        assertEquals(DreamSpec.offset(5, 500, 500), DreamSpec.offset(5, 500, 500))
        val spots = (0L until 10L).map { DreamSpec.offset(it, 500, 500) }.toSet()
        assertTrue(spots.size > 5)
        assertNotEquals(DreamSpec.offset(1, 500, 500), DreamSpec.offset(2, 500, 500))
    }

    @Test fun noFreeSpaceMeansNoShift() = assertEquals(0 to 0, DreamSpec.offset(3, -20, 0))

    @Test fun dimOptions() {
        assertEquals(DreamSpec.DEFAULT_DIM_AFTER_MIN, DreamSpec.valid(7))
        assertEquals(15, DreamSpec.next(5))
        assertEquals(1, DreamSpec.next(15))
    }
}
