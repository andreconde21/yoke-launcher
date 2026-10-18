package com.outsmartis.yoke.askbar

import com.outsmartis.yoke.palette.PaletteMode
import com.outsmartis.yoke.palette.PaletteQuery

/** Who the palette's "Ask …" row hands the text to. Declaration order is the order the setting cycles in. */
enum class AskAgent(val key: String) {
    /** The phone's default assistant (Gemini, Bixby...). */
    PHONE_ASSISTANT("assistant"),

    /** Conductore's "Send to agent" share target; only offered when Conductore is installed. */
    CONDUCTORE("conductore"),

    /** No Ask row at all. */
    NONE("none");

    companion object {
        val DEFAULT = PHONE_ASSISTANT

        fun fromKey(key: String?): AskAgent = values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** When the Ask row shows and which agents can be picked. No Android types, so it is unit-tested. */
object AskBarLogic {

    /** The agent actually used: Conductore picked but since uninstalled behaves like none. */
    fun effective(agent: AskAgent, conductoreInstalled: Boolean): AskAgent =
        if (agent == AskAgent.CONDUCTORE && !conductoreInstalled) AskAgent.NONE else agent

    /** Whether [agent] can be picked in Settings right now. */
    fun isEnabled(agent: AskAgent, conductoreInstalled: Boolean): Boolean =
        agent != AskAgent.CONDUCTORE || conductoreInstalled

    /** The next pickable agent after [current], wrapping around. */
    fun next(current: AskAgent, conductoreInstalled: Boolean): AskAgent {
        val all = AskAgent.values()
        var i = all.indexOf(current)
        repeat(all.size) {
            i = (i + 1) % all.size
            if (isEnabled(all[i], conductoreInstalled)) return all[i]
        }
        return current
    }

    /**
     * The Ask row closes the plain-text results when the bar is on, an agent is usable, and no app
     * or link matched [query] ([baseMatches]). Prefix modes (`=`, `>`, `/`) and `!` bang searches
     * have their own rows and never get it.
     */
    fun showsAskRow(
        barOn: Boolean,
        agent: AskAgent,
        conductoreInstalled: Boolean,
        query: String,
        baseMatches: Int,
    ): Boolean {
        if (!barOn || effective(agent, conductoreInstalled) == AskAgent.NONE) return false
        if (query.isBlank() || baseMatches > 0) return false
        if (query.trimStart().startsWith("!")) return false
        return PaletteQuery.parse(query.trimStart()).mode == PaletteMode.APPS
    }

    /**
     * The assistant package to send to: the "assistant" setting (`package/class`) when set, else the
     * package that answers ACTION_ASSIST. Null when unknown, the system chooser ("android") or
     * Yoke itself ([own]), since ACTION_ASSIST would then run Yoke's own assist entry.
     */
    fun assistantPackage(setting: String?, resolved: String?, own: String): String? {
        val pkg = setting?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: resolved
        return pkg?.takeIf { it != "android" && it != own }
    }
}
