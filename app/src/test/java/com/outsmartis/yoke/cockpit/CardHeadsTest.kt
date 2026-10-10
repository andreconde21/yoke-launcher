package com.outsmartis.yoke.cockpit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardHeadsTest {
    private fun file(modified: Long, size: Long) = VaultAccess.VaultFile("Cockpit/a.md", "id", modified, size)

    @Test fun unchangedFileReusesTheHead() = assertTrue(CardHeads.isFresh(100L, 20L, file(100L, 20L)))
    @Test fun newerFileIsReadAgain() = assertFalse(CardHeads.isFresh(100L, 20L, file(200L, 20L)))
    @Test fun sameTimeDifferentSizeIsReadAgain() = assertFalse(CardHeads.isFresh(100L, 20L, file(100L, 21L)))
    @Test fun providerWithoutTimesAlwaysReads() = assertFalse(CardHeads.isFresh(0L, 20L, file(0L, 20L)))
}
