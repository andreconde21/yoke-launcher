package com.outsmartis.yoke.notifications

/** One active notification, reduced to what the home block needs. Nothing here is persisted. */
data class NoteEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    val groupKey: String? = null,
    val media: Boolean = false,
    val autoCancel: Boolean = false,
)

/** All of one app's visible notifications; [latest] is the newest. */
data class AppGroup(val packageName: String, val entries: List<NoteEntry>) {
    val latest: NoteEntry get() = entries.first()
    val count: Int get() = entries.size
    val keys: List<String> get() = entries.map { it.key }
}

/** One rendered line: [label], an optional [count] (more than one), and the [body]. */
data class NoteLine(val packageName: String, val label: String, val count: Int?, val body: String)

object NotificationLogic {

    const val MAX_LINES_SMALL = 3
    const val MAX_LINES_LARGE = 5

    /** Allowed apps only, minus ongoing, media and summaries that have children in the list. */
    fun visible(all: List<NoteEntry>, allowed: Set<String>): List<NoteEntry> {
        val withChildren = all.filter { !it.groupSummary && it.groupKey != null }.map { it.packageName to it.groupKey }.toSet()
        return all.filter { e ->
            e.packageName in allowed &&
                !e.ongoing &&
                !e.media &&
                !(e.groupSummary && (e.packageName to e.groupKey) in withChildren)
        }
    }

    /** One group per app, newest first, at most [max]. Entries inside a group are newest first. */
    fun group(visible: List<NoteEntry>, max: Int): List<AppGroup> =
        visible.groupBy { it.packageName }
            .map { (pkg, list) -> AppGroup(pkg, list.sortedByDescending { it.postTime }) }
            .sortedByDescending { it.latest.postTime }
            .take(max.coerceAtLeast(0))

    /** Notification counts per package, for the home app labels. */
    fun counts(visible: List<NoteEntry>): Map<String, Int> =
        visible.groupingBy { it.packageName }.eachCount()

    /** Only 3 and 5 are offered; anything else falls back to 3. */
    fun normalizeMax(value: Int): Int = if (value == MAX_LINES_LARGE) MAX_LINES_LARGE else MAX_LINES_SMALL

    fun line(
        group: AppGroup,
        label: String,
        showText: Boolean,
        hideTextApps: Set<String>,
        maxChars: Int = 160,
    ): NoteLine {
        val e = group.latest
        val reveal = showText && group.packageName !in hideTextApps
        val body = if (reveal) clip(joinTitleText(e.title, e.text), maxChars) else ""
        return NoteLine(group.packageName, label, group.count.takeIf { it > 1 }, body)
    }

    /** `Title: text`, `Title` or `text`, whichever parts exist. */
    fun joinTitleText(title: String, text: String): String {
        val t = title.trim()
        val x = text.trim().replace(Regex("\\s+"), " ")
        return when {
            t.isEmpty() -> x
            x.isEmpty() -> t
            else -> "$t: $x"
        }
    }

    /** The plain-text form of a line: `Signal · 2   Anna: hi`. The view colours the count. */
    fun plain(line: NoteLine): String = buildString {
        append(line.label)
        if (line.count != null) append(" · ").append(line.count)
        if (line.body.isNotEmpty()) append("   ").append(line.body)
    }

    /** Cuts to [max] chars with an ellipsis, never splitting a surrogate pair. */
    fun clip(text: String, max: Int): String {
        if (max <= 1 || text.length <= max) return text
        var end = max - 1
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    /** Do Not Disturb rule: [filter] is NotificationManager.currentInterruptionFilter. */
    fun hiddenByDnd(hideDuringDnd: Boolean, filter: Int): Boolean =
        hideDuringDnd && filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN

    // android.app.NotificationManager values, duplicated so this stays a plain JVM class
    const val INTERRUPTION_FILTER_UNKNOWN = 0
    const val INTERRUPTION_FILTER_ALL = 1

    /** The count shown after a home app's label (`2`), or "" when there is nothing to show. */
    fun labelCountSuffix(count: Int, enabled: Boolean): String =
        if (enabled && count > 0) count.coerceAtMost(99).toString() + if (count > 99) "+" else "" else ""

    /** `▶ Title — Artist`; the pause glyph while playing. Empty title falls back to the app. */
    fun nowPlayingLine(title: String, artist: String, playing: Boolean): String {
        val glyph = if (playing) "❚❚" else "▶"
        val t = title.trim()
        val a = artist.trim()
        val body = when {
            t.isEmpty() -> a
            a.isEmpty() -> t
            else -> "$t — $a"
        }
        return clip("$glyph $body".trimEnd(), 120)
    }
}
