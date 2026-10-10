package com.outsmartis.yoke.theme

/**
 * One palette in Omarchy's vocabulary (basecamp/omarchy, MIT). The bundled
 * list in OmarchyThemes.kt is generated; "Follow Conductore" builds the same
 * shape from the Conductore content provider.
 */
class OmarchyTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val accent: Int,
    val background: Int,
    val foreground: Int,
    val selection: Int,
    val muted: Int,
    val darkBackground: Int = background,
    val lighterBackground: Int = background,
    val red: Int = accent,
    val green: Int = accent,
    val yellow: Int = accent,
    val blue: Int = accent,
    val magenta: Int = accent,
    val cyan: Int = accent,
    val orange: Int = accent,
    /** File names under themes/<id>/backgrounds/ in the Omarchy repo; empty when the theme has none. */
    val backgrounds: List<String> = emptyList(),
)
