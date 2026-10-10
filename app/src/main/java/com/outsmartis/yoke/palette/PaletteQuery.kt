package com.outsmartis.yoke.palette

import com.outsmartis.yoke.data.Constants

/** What the drawer's search box is currently doing, decided by the first character typed. */
enum class PaletteMode(val prefix: Char?) {
    /** Plain text: apps and links. */
    APPS(null),

    /** `=` calculator. */
    CALC('='),

    /** `>` Yoke actions. */
    ACTIONS('>'),

    /** `/` app shortcuts. */
    SHORTCUTS('/'),
}

data class PaletteQuery(val mode: PaletteMode, val text: String) {
    companion object {
        /** The existing DuckDuckGo search URL for "Search the web for ...". */
        fun webSearchUrl(query: String): String =
            Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")

        /**
         * A prefix only counts as the very first character, so " =x" stays a plain search
         * (a leading space is also how the drawer opts out of auto-launch). For a prefix
         * mode [text] is what follows the prefix, trimmed on the left.
         */
        fun parse(raw: CharSequence?): PaletteQuery {
            val s = raw?.toString().orEmpty()
            val mode = PaletteMode.values().firstOrNull { it.prefix != null && s.startsWith(it.prefix) }
                ?: return PaletteQuery(PaletteMode.APPS, s)
            return PaletteQuery(mode, s.substring(1).trimStart())
        }
    }
}
