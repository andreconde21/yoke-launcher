package com.outsmartis.yoke.cockpit

import java.time.LocalDate

/** The frontmatter fields of a card the agenda line needs. [time] is "HH:mm" or empty. */
data class CardHead(val title: String, val status: String, val due: LocalDate?, val time: String)

/** The home-screen agenda line: cards due today or overdue. Pure; the vault read lives in CockpitAgendaRepository. */
object CockpitAgenda {

    /** Scalar frontmatter fields (`key: value`) of a card's text; quotes removed and unescaped. No frontmatter gives none. */
    fun parseFrontmatter(text: String): Map<String, String> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trimEnd('\r') }.toList()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.let { if (it < 0) lines.size else it + 1 }
        val out = LinkedHashMap<String, String>()
        for (line in lines.subList(1, end)) {
            if (line.isEmpty() || line[0].isWhitespace() || line[0] == '-' || line[0] == '#') continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            out.putIfAbsent(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()))
        }
        return out
    }

    private fun unquote(v: String): String = when {
        v.length >= 2 && v.startsWith('"') && v.endsWith('"') ->
            Regex("""\\(.)""").replace(v.substring(1, v.length - 1)) { it.groupValues[1] }
        v.length >= 2 && v.startsWith('\'') && v.endsWith('\'') -> v.substring(1, v.length - 1).replace("''", "'")
        else -> v
    }

    /** A card's head, or null when it has no frontmatter. A malformed due date or time reads as none. */
    fun parseHead(text: String): CardHead? {
        val fm = parseFrontmatter(text)
        if (fm.isEmpty()) return null
        val time = fm["time"].orEmpty().takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) }.orEmpty()
        return CardHead(
            title = fm["title"].orEmpty().trim(),
            status = fm["status"].orEmpty().trim().lowercase(),
            due = fm["due"]?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() },
            time = time,
        )
    }

    /** Not done, not archived, due today or earlier; by date, then timed before untimed, then time, then title. */
    fun select(heads: List<CardHead>, today: LocalDate): List<CardHead> =
        heads.filter { h ->
            h.due != null && !h.due.isAfter(today) && h.status != "done" && h.status != "archived" && h.title.isNotEmpty()
        }.sortedWith(
            compareBy<CardHead> { it.due }
                .thenBy { it.time.isEmpty() }
                .thenBy { it.time.padStart(5, '0') }
                .thenBy { it.title.lowercase() }
        )

    /**
     * `Today · 10:00 Call accountant · +2`, or `Overdue · Call accountant · +1` when the first
     * is overdue (its old time is left out). Empty when nothing is due.
     */
    fun line(heads: List<CardHead>, today: LocalDate): String {
        val due = select(heads, today)
        val first = due.firstOrNull() ?: return ""
        val overdue = first.due!!.isBefore(today)
        val item = if (first.time.isNotEmpty() && !overdue) "${first.time} ${first.title}" else first.title
        val rest = if (due.size > 1) " · +${due.size - 1}" else ""
        return (if (overdue) "Overdue" else "Today") + " · " + item + rest
    }

    /** The line for the raw head texts of the cards. */
    fun lineFromTexts(texts: List<String>, today: LocalDate): String =
        line(texts.mapNotNull { parseHead(it) }, today)
}
