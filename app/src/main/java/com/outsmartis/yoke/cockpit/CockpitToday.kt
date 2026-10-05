package com.outsmartis.yoke.cockpit

import java.net.URLEncoder
import java.time.LocalDate

/**
 * Edits the leading `---` frontmatter block of a card, touching only the named top-level
 * scalar keys. Every other byte (other keys, YAML lists, comments, body, line endings) stays.
 */
object CockpitFrontmatter {

    /**
     * Applies [changes]: a value sets the key (`key: value`, an empty value gives `key:`), null
     * removes it. Existing keys are replaced in place, missing ones are appended before the
     * closing `---`. Returns null when the text has no complete frontmatter block.
     */
    fun update(text: String, changes: Map<String, String?>): String? {
        val bom = if (text.startsWith("﻿")) "﻿" else ""
        val body = text.removePrefix("﻿")
        // Lines with their terminators, so unchanged lines are copied verbatim.
        val lines = Regex("[^\n]*\n|[^\n]+").findAll(body).map { it.value }.toMutableList()
        fun bare(l: String) = l.trimEnd('\n', '\r')
        if (lines.isEmpty() || bare(lines[0]).trim() != "---") return null
        if ((1 until lines.size).none { bare(lines[it]).trim() == "---" }) return null
        val eol = if (lines[0].endsWith("\r\n")) "\r\n" else "\n"
        fun closeIdx() = (1 until lines.size).first { bare(lines[it]).trim() == "---" }

        for ((key, value) in changes) {
            val close = closeIdx()
            val idx = (1 until close).firstOrNull { i ->
                val l = lines[i]
                l.isNotEmpty() && !l[0].isWhitespace() && l.startsWith("$key:")
            }
            val newBare = if (value.isNullOrEmpty()) "$key:" else "$key: $value"
            when {
                idx != null && value == null -> lines.removeAt(idx)
                idx != null -> {
                    val old = lines[idx]
                    if (bare(old) != newBare) lines[idx] = newBare + old.substring(bare(old).length)
                }
                value != null -> lines.add(close, newBare + eol)
            }
        }
        return bom + lines.joinToString("")
    }
}

/** A card shown in the Today sheet. [headHash] is a hash of the head read, to detect edits made since. */
data class TodayCard(
    val path: String,
    val fileId: String,
    val title: String,
    val status: String,
    val due: LocalDate?,
    val time: String,
    val labels: List<String>,
    val headHash: Int,
)

enum class TodayAction { DONE, START, TOMORROW, PICK_DATE }

data class TodaySections(val overdue: List<TodayCard>, val today: List<TodayCard>, val inProgress: List<TodayCard>) {
    val isEmpty get() = overdue.isEmpty() && today.isEmpty() && inProgress.isEmpty()
}

object CockpitToday {

    private val order: Comparator<TodayCard> =
        compareBy<TodayCard, LocalDate?>(nullsLast()) { it.due }
            .thenBy { it.time.isEmpty() }
            .thenBy { it.time.padStart(5, '0') }
            .thenBy { it.title.lowercase() }

    private fun live(c: TodayCard) = c.status != "done" && c.status != "archived" && c.title.isNotEmpty()

    /**
     * Overdue (due before today) and Today (due today) as the agenda selects them; In progress
     * holds in-progress cards not already listed above (e.g. no due date, or a future one).
     */
    fun sections(cards: List<TodayCard>, today: LocalDate): TodaySections {
        val open = cards.filter { live(it) }
        val overdue = open.filter { it.due != null && it.due.isBefore(today) }.sortedWith(order)
        val due = open.filter { it.due == today }.sortedWith(order)
        val progress = open.filter { it.status == "in-progress" && (it.due == null || it.due.isAfter(today)) }.sortedWith(order)
        return TodaySections(overdue, due, progress)
    }

    /**
     * Frontmatter changes for a row action. Start mirrors the plugin's `getDropUpdates` for a
     * status:in-progress column; Tomorrow and Pick date keep an in-progress status, else schedule.
     */
    fun changesFor(
        action: TodayAction,
        card: TodayCard,
        today: LocalDate,
        clearDateOnInProgress: Boolean,
        picked: LocalDate? = null,
    ): Map<String, String?> = when (action) {
        TodayAction.DONE -> linkedMapOf("status" to "done", "completed" to today.toString())
        TodayAction.START -> linkedMapOf<String, String?>("status" to "in-progress").also {
            if (clearDateOnInProgress && card.time.isEmpty()) it["due"] = ""
        }
        TodayAction.TOMORROW -> dueChange(card, today.plusDays(1))
        TodayAction.PICK_DATE -> dueChange(card, requireNotNull(picked) { "pick date needs a date" })
    }

    private fun dueChange(card: TodayCard, date: LocalDate): Map<String, String?> {
        val m = linkedMapOf<String, String?>()
        if (card.status != "in-progress") m["status"] = "scheduled"
        m["due"] = date.toString()
        return m
    }

    /** The card as it will be once [changes] are written, for the optimistic row update. */
    fun applyChanges(card: TodayCard, changes: Map<String, String?>): TodayCard {
        var c = card
        if ("status" in changes) c = c.copy(status = changes["status"].orEmpty())
        if ("due" in changes) c = c.copy(due = changes["due"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        return c
    }

    fun headHash(head: String): Int = head.hashCode()

    /** `obsidian://open?vault=<name>&file=<path without .md>`, URL-encoded. */
    fun obsidianUri(vaultName: String, path: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "obsidian://open?vault=${enc(vaultName)}&file=${enc(path.removeSuffix(".md"))}"
    }
}
