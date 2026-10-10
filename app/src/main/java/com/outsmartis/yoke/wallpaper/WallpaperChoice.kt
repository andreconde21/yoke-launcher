package com.outsmartis.yoke.wallpaper

enum class WallpaperSource { THEME_COLOUR, OMARCHY, IMAGE }

enum class WallpaperTarget { HOME, LOCK, BOTH }

/** "Dim wallpaper": a black scrim over an image wallpaper so text stays readable. */
enum class DimLevel(val alpha: Float) {
    OFF(0f), LIGHT(0.2f), STRONG(0.3f);

    /** ARGB black scrim at [alpha]. */
    val scrimColor: Int get() = ((alpha * 255).toInt() shl 24)
}

/** Everything the Wallpaper screen remembers. [encode] / [decode] are a pure round trip. */
data class WallpaperChoice(
    val source: WallpaperSource = WallpaperSource.THEME_COLOUR,
    val omarchyTheme: String = "",
    val omarchyFile: String = "",
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val dim: DimLevel = DimLevel.OFF,
) {
    /** True when the home screen should show the system wallpaper instead of drawing a solid colour. */
    val showsImage: Boolean get() = source != WallpaperSource.THEME_COLOUR

    fun encode(): String = listOf(source.name, omarchyTheme, omarchyFile, target.name, dim.name)
        .joinToString(SEP) { it.replace(SEP, " ") }

    companion object {
        private const val SEP = "\t"

        /** Unknown or missing fields fall back to the defaults, so old or damaged values never crash. */
        fun decode(raw: String?): WallpaperChoice {
            if (raw.isNullOrEmpty()) return WallpaperChoice()
            val p = raw.split(SEP)
            fun at(i: Int) = p.getOrElse(i) { "" }
            return WallpaperChoice(
                source = WallpaperSource.entries.firstOrNull { it.name == at(0) } ?: WallpaperSource.THEME_COLOUR,
                omarchyTheme = at(1),
                omarchyFile = at(2),
                target = WallpaperTarget.entries.firstOrNull { it.name == at(3) } ?: WallpaperTarget.BOTH,
                dim = DimLevel.entries.firstOrNull { it.name == at(4) } ?: DimLevel.OFF,
            )
        }
    }
}
