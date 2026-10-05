package com.outsmartis.yoke.palette

import java.net.URLEncoder
import java.text.Normalizer

/** Where a plain-text palette hit comes from. Declaration order is the order rows appear in. */
enum class SearchKind { CARD, NOTE, CONTACT, SETTING }

/**
 * One extra row under the app and link matches. [target] is what tapping opens: an
 * obsidian:// URL, a contact lookup URI or a settings action, depending on [kind].
 * [keywords] only help matching (synonyms), they are never shown.
 */
data class SearchHit(
    val kind: SearchKind,
    val title: String,
    val subtitle: String?,
    val target: String,
    val keywords: List<String> = emptyList(),
)

/** Matching, ranking and per-source limits for [SearchHit]s. No Android types, so it is unit-tested. */
object SearchRanking {
    const val LIMIT = 5

    /** Queries shorter than this match nothing: one letter would list the whole vault. */
    const val MIN_QUERY = 2

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    /** 0 = no match; higher is better: title prefix, word start, keyword start, anywhere. */
    fun score(query: String, hit: SearchHit): Int {
        val q = fold(query.trim())
        if (q.length < MIN_QUERY) return 0
        val title = fold(hit.title)
        if (title.startsWith(q)) return 4
        if (title.split(nonWord).any { it.startsWith(q) }) return 3
        if (hit.keywords.any { k -> fold(k).split(nonWord).any { it.startsWith(q) } || fold(k) == q }) return 2
        if (AppSearch.labelMatches(hit.title, query) || hit.keywords.any { AppSearch.labelMatches(it, query) }) return 1
        return 0
    }

    /** The [limit] best matches of [hits] for [query], best first, ties by shorter then alphabetical title. */
    fun rank(query: String, hits: List<SearchHit>, limit: Int = LIMIT): List<SearchHit> =
        hits.map { it to score(query, it) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<SearchHit, Int>> { it.second }
                    .thenBy { it.first.title.length }
                    .thenBy { it.first.title.lowercase() }
            )
            .take(limit).map { it.first }

    /**
     * The rows below the app matches: cards, notes, contacts, settings, each limited to
     * [limit]. A source left out of [sources] is skipped.
     */
    fun combine(
        query: String,
        sources: Map<SearchKind, List<SearchHit>>,
        limit: Int = LIMIT,
    ): List<SearchHit> = SearchKind.values().flatMap { kind -> rank(query, sources[kind].orEmpty(), limit) }
}

/** A card's frontmatter fields the palette shows. */
data class CardInfo(val path: String, val title: String, val status: String, val due: String) {
    /** "card · status · due", with whatever of the two exists. */
    val subtitle: String
        get() = listOf("card", status, due).filter { it.isNotBlank() }.joinToString(" · ")
}

object CardFrontmatter {
    /** [head] is the start of the file; [path] is vault-relative. Title falls back to the file name. */
    fun parse(path: String, head: String): CardInfo {
        val lines = head.lineSequence().toList()
        val fields = mutableMapOf<String, String>()
        if (lines.firstOrNull()?.trim() == "---") {
            for (line in lines.drop(1)) {
                if (line.trim() == "---") break
                val i = line.indexOf(':')
                if (i <= 0 || line[0].isWhitespace()) continue
                fields[line.substring(0, i).trim()] = unquote(line.substring(i + 1).trim())
            }
        }
        val stem = path.substringAfterLast('/').removeSuffix(".md")
        return CardInfo(path, fields["title"]?.takeIf { it.isNotBlank() } ?: stem, fields["status"].orEmpty(), fields["due"].orEmpty())
    }

    private fun unquote(v: String): String =
        if (v.length >= 2 && (v.first() == '"' && v.last() == '"' || v.first() == '\'' && v.last() == '\''))
            v.substring(1, v.length - 1).replace("\\\"", "\"")
        else v
}

object ObsidianLinks {
    /** `obsidian://open?vault=<name>&file=<path without .md>`, both URL-encoded. */
    fun open(vaultName: String, path: String): String =
        "obsidian://open?vault=${enc(vaultName)}&file=${enc(path.removeSuffix(".md"))}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** A page of Android's Settings app. [action] is an intent action, or one of the Yoke ones below. */
data class SettingsPage(val name: String, val action: String, val synonyms: List<String> = emptyList())

object SettingsPages {
    const val YOKE_SETTINGS = "yoke:settings"
    const val YOKE_APP_INFO = "yoke:app_info"

    val ALL: List<SettingsPage> = listOf(
        SettingsPage("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wireless", "internet", "network")),
        SettingsPage("Bluetooth", "android.settings.BLUETOOTH_SETTINGS", listOf("pair", "headphones", "earbuds")),
        SettingsPage("Battery", "android.settings.BATTERY_SAVER_SETTINGS", listOf("power", "battery saver", "charge")),
        SettingsPage("Display", "android.settings.DISPLAY_SETTINGS", listOf("brightness", "screen", "dark mode", "font size", "screen timeout")),
        SettingsPage("Notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("alerts", "notification settings")),
        SettingsPage("Apps", "android.settings.APPLICATION_SETTINGS", listOf("applications", "app list", "uninstall")),
        SettingsPage("Location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
        SettingsPage("Sound", "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "audio", "vibration")),
        SettingsPage("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("space", "memory", "disk")),
        SettingsPage("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("talkback", "a11y")),
        SettingsPage("Date & time", "android.settings.DATE_SETTINGS", listOf("clock", "timezone", "time zone")),
        SettingsPage("Developer options", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("adb", "usb debugging", "dev")),
        SettingsPage("NFC", "android.settings.NFC_SETTINGS", listOf("tap to pay", "contactless")),
        SettingsPage("Hotspot", "android.settings.TETHER_SETTINGS", listOf("tethering", "share internet", "mobile hotspot")),
        SettingsPage("Data usage", "android.settings.DATA_USAGE_SETTINGS", listOf("mobile data", "cellular", "data saver")),
        SettingsPage("Do not disturb", "android.settings.ZEN_MODE_PRIORITY_SETTINGS", listOf("dnd", "silent", "focus", "quiet")),
        SettingsPage("Default apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("default launcher", "home app", "browser")),
        SettingsPage("Security", "android.settings.SECURITY_SETTINGS", listOf("lock screen", "screen lock", "fingerprint", "pin", "password")),
        SettingsPage("Privacy", "android.settings.PRIVACY_SETTINGS", listOf("permissions", "permission manager")),
        SettingsPage("Wallpaper", "android.intent.action.SET_WALLPAPER", listOf("background", "home screen image")),
        SettingsPage("Keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("input", "typing", "ime")),
        SettingsPage("Language", "android.settings.LOCALE_SETTINGS", listOf("locale", "region", "input language")),
        SettingsPage("About phone", "android.settings.DEVICE_INFO_SETTINGS", listOf("device info", "android version", "build number", "imei", "about device")),
        SettingsPage("Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight mode")),
        SettingsPage("Cast", "android.settings.CAST_SETTINGS", listOf("screen mirroring", "chromecast")),
        SettingsPage("Yoke settings", YOKE_SETTINGS, listOf("launcher settings", "home settings")),
        SettingsPage("Yoke's app info", YOKE_APP_INFO, listOf("yoke app info", "yoke permissions", "yoke storage")),
    )

    /** The settings rows for the pages [available] says this phone can open. */
    fun hits(available: (SettingsPage) -> Boolean): List<SearchHit> =
        ALL.filter(available).map { SearchHit(SearchKind.SETTING, it.name, "settings", it.action, it.synonyms) }
}
