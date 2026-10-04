package com.outsmartis.yoke.palette

import java.text.Normalizer

/**
 * Drawer search matching. A query matches when it is contained in any of the
 * given labels (the user's alias and the app's original label), ignoring case,
 * diacritics and separators.
 */
object AppSearch {
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

    fun matches(query: CharSequence, vararg labels: String?): Boolean =
        labels.any { !it.isNullOrEmpty() && labelMatches(it, query) }

    fun labelMatches(label: String, query: CharSequence): Boolean {
        if (label.contains(query.trim(), true)) return true
        val normalizedQuery = query.normalizeForSearch()
        return normalizedQuery.isNotEmpty() && label.normalizeForSearch().contains(normalizedQuery, true)
    }

    private fun CharSequence.normalizeForSearch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")
}
