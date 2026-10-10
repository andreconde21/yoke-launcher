package com.outsmartis.yoke.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.edit

/**
 * The Settings page's structure: sections that fold (remembered), a search box that filters rows
 * by their text, and rows that react to a tap anywhere on them, not only on the value at the end.
 */
class SettingsOrganizer(
    context: Context,
    private val sections: List<Section>,
    private val search: EditText,
) {
    /** [key] is stable (it is stored); [title] folds [rows] when tapped. */
    class Section(val key: String, val card: View, val title: TextView, val rows: ViewGroup)

    private val prefs = context.applicationContext.getSharedPreferences("com.outsmartis.yoke", 0)
    private val titles = sections.associate { it.key to it.title.text.toString() }

    fun install() {
        sections.forEach { s ->
            s.title.setOnClickListener {
                if (query().isNotEmpty()) return@setOnClickListener
                val folded = folded().toMutableSet()
                if (!folded.add(s.key)) folded.remove(s.key)
                prefs.edit { putStringSet(FOLDED, folded) }
                render()
            }
            for (i in 0 until s.rows.childCount) forwardRowTaps(s.rows.getChildAt(i))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) = render()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        render()
    }

    private fun query() = search.text?.toString()?.trim().orEmpty()

    private fun folded(): Set<String> = prefs.getStringSet(FOLDED, null) ?: emptySet()

    /** Applies the fold state, or while searching shows only matching rows and their sections. */
    fun render() {
        val q = query()
        val folded = folded()
        for (s in sections) {
            if (q.isEmpty()) {
                s.card.visibility = View.VISIBLE
                for (i in 0 until s.rows.childCount) s.rows.getChildAt(i).visibility = View.VISIBLE
                val open = s.key !in folded
                s.rows.visibility = if (open) View.VISIBLE else View.GONE
                s.title.text = "${titles[s.key]}  ${if (open) OPEN else CLOSED}"
            } else {
                var any = false
                for (i in 0 until s.rows.childCount) {
                    val row = s.rows.getChildAt(i)
                    val hit = SettingsSearch.matches(q, titles[s.key].orEmpty(), texts(row))
                    row.visibility = if (hit) View.VISIBLE else View.GONE
                    any = any || hit
                }
                s.rows.visibility = View.VISIBLE
                s.card.visibility = if (any) View.VISIBLE else View.GONE
                s.title.text = titles[s.key]
            }
        }
    }

    /** A tap on the row's label or empty space does what tapping its value does. */
    private fun forwardRowTaps(row: View) {
        if (row !is ViewGroup) return
        val value = findValue(row) ?: return
        row.setOnClickListener { value.performClick() }
        if (value.isLongClickable) row.setOnLongClickListener { value.performLongClick() }
    }

    private fun findValue(group: ViewGroup): View? {
        for (i in group.childCount - 1 downTo 0) {
            val c = group.getChildAt(i)
            if (c.id != View.NO_ID && c.hasOnClickListeners()) return c
            if (c is ViewGroup) findValue(c)?.let { return it }
        }
        return null
    }

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    companion object {
        private const val FOLDED = "SETTINGS_FOLDED_SECTIONS"
        private const val OPEN = "−"
        private const val CLOSED = "+"
    }
}

/** Pure matching for the Settings search, so it can be unit tested. */
object SettingsSearch {
    /** Every word of [query] appears in the row's texts or its section's title, ignoring case. */
    fun matches(query: String, sectionTitle: String, rowTexts: List<String>): Boolean {
        val hay = (rowTexts + sectionTitle).joinToString(" ").lowercase()
        return query.lowercase().split(' ').filter { it.isNotBlank() }.all { it in hay }
    }
}
