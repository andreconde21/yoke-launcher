package com.outsmartis.yoke.theme

/** The ordered set of selectable theme ids and the pure logic over them. */
object ThemeCatalog {

    /** Omarchy themes by id. */
    val omarchy: Map<String, YokeTheme> by lazy { OMARCHY_THEMES.associate { it.id to YokeTheme.from(it) } }

    /** Ids in picker order: system, every Omarchy theme, then Conductore when [conductore] is offered. */
    fun ids(conductore: Boolean = true): List<String> =
        listOf(YokeTheme.SYSTEM_ID) + OMARCHY_THEMES.map { it.id } +
            if (conductore) listOf(YokeTheme.PC_ID) else emptyList()

    /** The theme after [current], wrapping; an unknown id starts the cycle. */
    fun next(current: String, ids: List<String> = ids()): String {
        if (ids.isEmpty()) return YokeTheme.SYSTEM_ID
        val i = ids.indexOf(current)
        return ids[(i + 1) % ids.size]
    }

    /** The bundled theme for [id], or null for system / conductore / unknown. */
    fun omarchyTheme(id: String): YokeTheme? = omarchy[id]
}
