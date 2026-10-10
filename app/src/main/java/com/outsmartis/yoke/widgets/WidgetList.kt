package com.outsmartis.yoke.widgets

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One hosted widget: the AppWidgetHost id and the height (dp) the user chose for it. */
data class WidgetEntry(val id: Int, val heightDp: Int)

/** Ordered, immutable list of hosted widgets with a small JSON codec. Pure so it unit-tests on the JVM. */
data class WidgetList(val entries: List<WidgetEntry> = emptyList()) {

    val ids: List<Int> get() = entries.map { it.id }

    fun add(entry: WidgetEntry): WidgetList =
        if (entries.any { it.id == entry.id }) this else WidgetList(entries + entry)

    fun remove(id: Int): WidgetList = WidgetList(entries.filterNot { it.id == id })

    /** Moves [id] by [delta] positions (-1 up, +1 down), clamped to the ends. */
    fun move(id: Int, delta: Int): WidgetList {
        val from = entries.indexOfFirst { it.id == id }
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, entries.lastIndex)
        if (to == from) return this
        val list = entries.toMutableList()
        list.add(to, list.removeAt(from))
        return WidgetList(list)
    }

    fun resize(id: Int, heightDp: Int): WidgetList =
        WidgetList(entries.map { if (it.id == id) it.copy(heightDp = heightDp) else it })

    /** Drops entries whose id is not in [valid] (provider uninstalled). */
    fun retain(valid: (Int) -> Boolean): WidgetList = WidgetList(entries.filter { valid(it.id) })

    fun toJson(): String {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("id", it.id).put("height", it.heightDp)) }
        return JSONObject().put("version", 1).put("widgets", arr).toString()
    }

    companion object {
        val SIZES_DP = listOf(120, 240, 400)
        const val DEFAULT_HEIGHT_DP = 160

        /** Lenient: malformed or missing JSON gives an empty list; bad entries are skipped. */
        fun parse(text: String?): WidgetList {
            if (text.isNullOrBlank()) return WidgetList()
            return try {
                val arr = JSONObject(text).optJSONArray("widgets") ?: return WidgetList()
                val out = ArrayList<WidgetEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!o.has("id")) continue
                    val e = WidgetEntry(o.optInt("id"), o.optInt("height", DEFAULT_HEIGHT_DP).coerceAtLeast(40))
                    if (out.none { it.id == e.id }) out.add(e)
                }
                WidgetList(out)
            } catch (e: JSONException) {
                WidgetList()
            }
        }
    }
}
