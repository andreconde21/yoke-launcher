package com.outsmartis.yoke.omarchy

/** The text and timing of the theme-change notice, like Omarchy's "Theme: tokyo-night". Pure, so it is unit tested. */
object ThemeNoticeText {

    const val SHORT_MS = 2000L
    const val LONG_MS = 4000L

    fun theme(id: String): String = "Theme: $id"

    /** "Background: 2/4" for [file] among [files], or null when it is not one of them. */
    fun background(files: List<String>, file: String?): String? {
        val i = files.indexOf(file)
        return if (i < 0) null else "Background: ${i + 1}/${files.size}"
    }

    fun durationMs(long: Boolean): Long = if (long) LONG_MS else SHORT_MS
}
