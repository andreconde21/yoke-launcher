package com.outsmartis.yoke.omarchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLabelTest {
    @Test fun promptAndLowercase() = assertEquals("> obsidian", TerminalLabel.format("Obsidian", ">", lowercase = true))
    @Test fun promptOnly() = assertEquals("$ Obsidian", TerminalLabel.format("Obsidian", "$", lowercase = false))
    @Test fun lowercaseOnly() = assertEquals("obsidian", TerminalLabel.format("Obsidian", null, lowercase = true))
    @Test fun nothingOn() = assertEquals("Obsidian", TerminalLabel.format("Obsidian", null, lowercase = false))
    @Test fun emptyLabelStaysEmpty() = assertEquals("", TerminalLabel.format("", ">", lowercase = true))

    @Test fun promptDefaultsAndIsTrimmedAndCapped() {
        assertEquals(">", TerminalLabel.prompt(null))
        assertEquals(">", TerminalLabel.prompt("   "))
        assertEquals("~$", TerminalLabel.prompt("  ~$ "))
        assertEquals(TerminalLabel.MAX_PROMPT, TerminalLabel.prompt("x".repeat(20)).length)
    }

    @Test fun cursorIsAboutOneCharacterWide() {
        assertEquals(24, TerminalLabel.cursorWidthPx(40f))
        assertTrue(TerminalLabel.cursorWidthPx(1f) >= 2)
    }
}
