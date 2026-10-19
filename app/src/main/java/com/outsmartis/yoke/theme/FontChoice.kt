package com.outsmartis.yoke.theme

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import com.outsmartis.yoke.R

/** The fonts Settings offers. [id] is what ThemePrefs stores; [SYSTEM] has no files. */
enum class FontChoice(
    val id: String,
    @StringRes val label: Int,
    @FontRes val regular: Int = 0,
    @FontRes val bold: Int = 0,
) {
    SYSTEM("system", R.string.font_system),
    JETBRAINS_MONO("jetbrains_mono", R.string.font_jetbrains_mono, R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold),
    INTER("inter", R.string.font_inter, R.font.inter_regular, R.font.inter_bold),
    IBM_PLEX_SANS("ibm_plex_sans", R.string.font_ibm_plex_sans, R.font.ibm_plex_sans_regular, R.font.ibm_plex_sans_bold),
    IBM_PLEX_MONO("ibm_plex_mono", R.string.font_ibm_plex_mono, R.font.ibm_plex_mono_regular, R.font.ibm_plex_mono_bold),
    SPACE_GROTESK("space_grotesk", R.string.font_space_grotesk, R.font.space_grotesk_regular, R.font.space_grotesk_bold),
    GEIST("geist", R.string.font_geist, R.font.geist_regular, R.font.geist_bold),
    GEIST_MONO("geist_mono", R.string.font_geist_mono, R.font.geist_mono_regular, R.font.geist_mono_bold);

    companion object {
        /** Unknown or missing ids (a newer backup, say) fall back to the system font. */
        fun fromId(id: String?): FontChoice = entries.firstOrNull { it.id == id } ?: SYSTEM

        /**
         * The choice to use: the stored [id] if there is one, else the old on/off JetBrains Mono
         * switch ([legacyJetBrains]) from before the picker existed.
         */
        fun resolve(id: String?, legacyJetBrains: Boolean): FontChoice =
            if (id != null) fromId(id) else if (legacyJetBrains) JETBRAINS_MONO else SYSTEM
    }
}
