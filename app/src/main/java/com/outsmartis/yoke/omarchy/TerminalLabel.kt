package com.outsmartis.yoke.omarchy

import kotlin.math.roundToInt

/** Terminal mode's text rules: "> obsidian" labels and the block cursor's size. Pure, so it is unit tested. */
object TerminalLabel {

    const val DEFAULT_PROMPT = ">"
    const val MAX_PROMPT = 8

    /** The prompt as typed, trimmed and capped; blank falls back to [DEFAULT_PROMPT]. */
    fun prompt(text: String?): String = text?.trim()?.take(MAX_PROMPT)?.ifEmpty { null } ?: DEFAULT_PROMPT

    /** [label] with the [prompt] before it (none when null) and lowercased when asked. Empty stays empty. */
    fun format(label: String, prompt: String?, lowercase: Boolean): String {
        if (label.isEmpty()) return label
        val text = if (lowercase) label.lowercase() else label
        return if (prompt == null) text else "$prompt $text"
    }

    /** A block cursor about one mono character wide. */
    fun cursorWidthPx(textSizePx: Float): Int = (textSizePx * 0.6f).roundToInt().coerceAtLeast(2)
}
