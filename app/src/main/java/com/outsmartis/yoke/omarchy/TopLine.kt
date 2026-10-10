package com.outsmartis.yoke.omarchy

/** What the Waybar-like line can show, in the order the user picks. */
enum class TopModule(val id: String) {
    CLOCK("clock"), DATE("date"), BATTERY("battery"), WEATHER("weather"), AGENDA("agenda"), CONDUCTORE("conductore");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Whether the line replaces the big clock and date or sits above them. */
enum class TopLineClock(val id: String) {
    REPLACE("replace"), ABOVE("above");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: REPLACE
    }
}

/** The Waybar-like line: module list codec and edits, and each module's text. Pure, so it is unit tested. */
object TopLine {

    val DEFAULT = listOf(TopModule.CLOCK, TopModule.DATE, TopModule.BATTERY)
    const val SEPARATOR = "   "

    /** Comma-separated ids; null (never set) is [DEFAULT], unknown ids and repeats are dropped. */
    fun decode(text: String?): List<TopModule> {
        if (text == null) return DEFAULT
        return text.split(',').mapNotNull { TopModule.fromId(it.trim()) }.distinct()
    }

    fun encode(modules: List<TopModule>): String = modules.joinToString(",") { it.id }

    /** Removes [module] when shown, else adds it at the end. */
    fun toggle(modules: List<TopModule>, module: TopModule): List<TopModule> =
        if (module in modules) modules - module else modules + module

    /** Moves the module at [index] by [delta] places; out of range leaves the list as it is. */
    fun move(modules: List<TopModule>, index: Int, delta: Int): List<TopModule> {
        val to = index + delta
        if (index !in modules.indices || to !in modules.indices) return modules
        return modules.toMutableList().apply { add(to, removeAt(index)) }
    }

    /** "78%", "78%+" while charging; empty when the level is unknown. */
    fun battery(percent: Int, charging: Boolean): String =
        if (percent <= 0) "" else "$percent%" + if (charging) "+" else ""

    /** "17° light rain" from the home weather line "17° · light rain · 12°/19°". */
    fun weather(line: String): String =
        line.split(" · ").take(2).filter { it.isNotBlank() }.joinToString(" ")

    /** How many cards the agenda line stands for: "Today · Call · +2" is 3, empty is 0. */
    fun agendaCount(line: String): Int {
        if (line.isBlank()) return 0
        val more = Regex("""· \+(\d+)$""").find(line.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return 1 + more
    }

    fun agenda(count: Int): String = if (count <= 0) "" else "$count due"

    /** A dot only while Conductore has sessions waiting on an answer. */
    fun conductore(waiting: Int): String = if (waiting <= 0) "" else "● $waiting"

    /** The modules with something to show, in [order]. */
    fun segments(order: List<TopModule>, values: Map<TopModule, String>): List<Pair<TopModule, String>> =
        order.mapNotNull { m -> values[m]?.takeIf { it.isNotBlank() }?.let { m to it } }
}
