package com.outsmartis.yoke.cockpit

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Finds a due date and a time in a dictated (or typed) task, in pt-PT and English, and returns
 * the title without them: "Ligar ao contabilista amanhã às 10" → "Ligar ao contabilista",
 * tomorrow, 10:00. Pure, so it is unit tested.
 *
 * Kept deliberately conservative: a bare number is never a date ("Ler capítulo 12"), Portuguese
 * weekdays need "na/no/próxima…" or "-feira" ("a segunda versão" stays a title), and a bare
 * "às 3" counts only at the end of the text ("Fazer as 3 tarefas" stays a title).
 */
object SpokenDate {

    data class Parsed(val title: String, val date: LocalDate?, val time: LocalTime?) {
        val found get() = date != null || time != null
    }

    private val PT_MONTHS = listOf(
        "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val EN_MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val PT_DAYS = listOf("segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo")
    private val EN_DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    private const val B = "(?<![\\p{L}\\d])" // start of a word
    private const val E = "(?![\\p{L}\\d])" // end of a word
    private const val ORD = "(?:st|nd|rd|th|º|o)?"
    private const val AT_END = "(?=\\s*(?:$|[,.;!?]))"

    private class Rule<T>(pattern: String, val read: (MatchResult, LocalDate) -> T?) {
        val regex = Regex(pattern)
    }

    private val ptMonths = PT_MONTHS.joinToString("|")
    private val enMonths = EN_MONTHS.joinToString("|")
    private val enDays = EN_DAYS.joinToString("|")

    private val dateRules: List<Rule<LocalDate>> = listOf(
        Rule("${B}depois de amanha$E") { _, today -> today.plusDays(2) },
        Rule("${B}(?:the )?day after tomorrow$E") { _, today -> today.plusDays(2) },
        Rule("$B(?:(?:no |a |para |ate )?dia )?(\\d{1,2})$ORD de ($ptMonths)(?: de (\\d{4}))?$E") { m, today ->
            monthDay(today, m.groupValues[1], PT_MONTHS.indexOf(m.groupValues[2]) + 1, m.groupValues[3])
        },
        Rule("$B(?:(?:on|by) )?(?:the )?(\\d{1,2})$ORD (?:of )?($enMonths)(?:,? (\\d{4}))?$E") { m, today ->
            monthDay(today, m.groupValues[1], EN_MONTHS.indexOf(m.groupValues[2]) + 1, m.groupValues[3])
        },
        Rule("$B(?:(?:on|by) )?($enMonths) (?:the )?(\\d{1,2})$ORD(?:,? (\\d{4}))?$E") { m, today ->
            monthDay(today, m.groupValues[2], EN_MONTHS.indexOf(m.groupValues[1]) + 1, m.groupValues[3])
        },
        Rule("${B}amanha$E") { _, today -> today.plusDays(1) },
        Rule("${B}tomorrow$E") { _, today -> today.plusDays(1) },
        Rule("${B}hoje$E") { _, today -> today },
        Rule("$B(?:today|tonight)$E") { _, today -> today },
        Rule("$B(?:(?:no|para o|ate ao) )?dia (\\d{1,2})$E") { m, today -> dayOfMonth(today, m.groupValues[1].toInt()) },
        Rule("$B(?:on|by) the (\\d{1,2})(?:st|nd|rd|th)$E") { m, today -> dayOfMonth(today, m.groupValues[1].toInt()) },
        // Portuguese weekdays double as ordinals and nouns ("a segunda versão", "ir à quinta"),
        // so they need a lead-in (na, no, nesta, até, próxima) or "-feira". Sábado and domingo don't.
        Rule(
            "$B(?:ate (?:a |o )?|(?:na|no|nesta|neste|esta|este)(?: (?:proxima|proximo))? |(?:a |o )?(?:proxima|proximo) )" +
                "(${PT_DAYS.joinToString("|")})(?:[- ]feira)?$E"
        ) { m, today -> nextWeekday(today, PT_DAYS.indexOf(m.groupValues[1])) },
        Rule("$B(${PT_DAYS.take(5).joinToString("|")})[- ]feira$E") { m, today ->
            nextWeekday(today, PT_DAYS.indexOf(m.groupValues[1]))
        },
        Rule("$B(sabado|domingo)$E") { m, today -> nextWeekday(today, PT_DAYS.indexOf(m.groupValues[1])) },
        Rule("$B(?:(?:on|next|this|by) )?($enDays)$E") { m, today -> nextWeekday(today, EN_DAYS.indexOf(m.groupValues[1])) },
    )

    private val timeRules: List<Rule<LocalTime>> = listOf(
        Rule("$B(?:ao |at )?(?:meio[- ]dia|noon|midday)$E") { _, _ -> LocalTime.NOON },
        // às 10h, às 10h30, às 10:30, às 10 horas, às 3 da tarde
        Rule("$B(?:as|pelas|ate as) (\\d{1,2})(?:h|:| ?horas?)(?: ?(\\d{2}))?(?: ?h)?(?: (?:da|de) (manha|tarde|noite))?$E") { m, _ ->
            time(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        },
        Rule("$B(?:as|pelas|ate as) (\\d{1,2}) (?:da|de) (manha|tarde|noite)$E") { m, _ ->
            time(m.groupValues[1], "", m.groupValues[2])
        },
        Rule("$B(?:as|pelas|ate as) (\\d{1,2})$AT_END") { m, _ -> time(m.groupValues[1], "", "") },
        // at 3pm, 3 p.m., at 3:30pm
        Rule("$B(?:at |by )?(\\d{1,2})(?::(\\d{2}))? ?([ap])\\.? ?m\\.?(?![\\p{L}\\d])") { m, _ ->
            time(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        },
        Rule("$B(?:at|by) (\\d{1,2}):(\\d{2})$E") { m, _ -> time(m.groupValues[1], m.groupValues[2], "") },
        Rule("$B(?:at|by) (\\d{1,2})$AT_END") { m, _ -> time(m.groupValues[1], "", "") },
    )

    fun parse(text: String, now: LocalDateTime): Parsed {
        val today = now.toLocalDate()
        val work = fold(text).toCharArray()
        val cut = mutableListOf<IntRange>()

        fun <T> firstMatch(rules: List<Rule<T>>): T? {
            for (rule in rules) {
                for (m in rule.regex.findAll(String(work))) {
                    val value = rule.read(m, today) ?: continue
                    m.range.forEach { work[it] = ' ' }
                    cut += m.range
                    return value
                }
            }
            return null
        }

        var date = firstMatch(dateRules)
        val time = firstMatch(timeRules)
        // A time alone means today, or tomorrow when that time has already passed.
        if (date == null && time != null) date = if (time.isAfter(now.toLocalTime())) today else today.plusDays(1)
        if (cut.isEmpty()) return Parsed(text.trim(), null, null)
        val kept = StringBuilder(text)
        cut.forEach { r -> r.forEach { kept.setCharAt(it, ' ') } }
        return Parsed(tidy(kept.toString()), date, time)
    }

    /** Lower case without accents, one char per char so match ranges line up with the original. */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text) {
            val lower = c.lowercaseChar()
            val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)[0]
            append(if (base.isLetter() || base.isDigit()) base else lower)
        }
    }

    private val DANGLING = setOf(
        "no", "na", "para", "ate", "e", "de", "a", "as", "às", "o", "pelas", "ao",
        "on", "at", "by", "and", "for", "the", "next", "this",
    )

    /** Collapses the gaps left by the cut phrases and drops connecting words left at the ends. */
    private fun tidy(s: String): String {
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        fun bare(w: String) = w.trim(',', ';', '.', '!', '?').lowercase()
        while (words.isNotEmpty() && (bare(words.last()) in DANGLING || bare(words.last()).isEmpty())) words.removeAt(words.size - 1)
        while (words.isNotEmpty() && (bare(words.first()) in DANGLING || bare(words.first()).isEmpty())) words.removeAt(0)
        if (words.isNotEmpty()) words[words.size - 1] = words.last().trimEnd(',', ';')
        return words.joinToString(" ").replaceFirstChar { it.uppercaseChar() }
    }

    private fun time(hourText: String, minuteText: String, period: String): LocalTime? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.ifEmpty { "0" }.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        when (period) {
            "p", "tarde", "noite" -> if (hour in 1..11) hour += 12
            "a", "manha" -> if (hour == 12) hour = 0
            // No am/pm: 1 to 7 is afternoon ("às 3" is 15:00), 8 to 12 and 13 to 23 are taken as said.
            "" -> if (hour in 1..7) hour += 12
        }
        if (period == "a" || period == "p") if (hourText.toInt() !in 1..12) return null
        if (hour !in 0..23) return null
        return LocalTime.of(hour, minute)
    }

    private fun monthDay(today: LocalDate, dayText: String, month: Int, yearText: String): LocalDate? {
        val day = dayText.toIntOrNull() ?: return null
        val year = yearText.toIntOrNull()
        if (year != null) return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isBefore(today)) return thisYear
        return runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
    }

    /** The next date with that day of the month, today included. */
    private fun dayOfMonth(today: LocalDate, day: Int): LocalDate? {
        if (day !in 1..31) return null
        var month = today.withDayOfMonth(1)
        repeat(13) {
            if (day <= month.lengthOfMonth()) {
                val d = month.withDayOfMonth(day)
                if (!d.isBefore(today)) return d
            }
            month = month.plusMonths(1)
        }
        return null
    }

    /** The next such weekday after today: "sexta" on a Friday is next week's. */
    private fun nextWeekday(today: LocalDate, index: Int): LocalDate? {
        if (index < 0) return null
        val target = DayOfWeek.of(index + 1)
        var d = today.plusDays(1)
        while (d.dayOfWeek != target) d = d.plusDays(1)
        return d
    }
}
