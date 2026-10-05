package com.outsmartis.yoke.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A web link that lives in the drawer next to the apps (Omarchy's "Web App").
 * Can be pinned to a home slot or picked as a gesture target: [pinToken] is the
 * value stored in a slot's package field, see [idFromPinToken].
 */
data class LinkEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
) {
    val pinToken: String get() = PIN_PREFIX + id

    companion object {
        const val PIN_PREFIX = "link:"

        fun idFromPinToken(token: String): String? =
            if (token.startsWith(PIN_PREFIX)) token.removePrefix(PIN_PREFIX) else null

        /** Adds https:// when the user left the scheme out. Returns null for anything that is not a web URL. */
        /** Id prefix of entries Yoke provides itself; they can't be edited or removed. */
        const val BUILT_IN_PREFIX = "yoke:"

        /** Built-in entries: open the Obsidian Cockpit Board straight away (only when Obsidian is installed). */
        fun builtIns(context: android.content.Context): List<LinkEntry> {
            // Any one of these is enough: obsidian:// links open, Obsidian's package is visible,
            // or a Cockpit vault was picked (the user clearly has the board).
            val pm = context.packageManager
            val opensLinks = runCatching {
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("obsidian://cockpit-board"))
                    .resolveActivity(pm) != null
            }.getOrDefault(false)
            val packageVisible = runCatching { pm.getPackageInfo("md.obsidian", 0); true }.getOrDefault(false)
            val hasVault = com.outsmartis.yoke.cockpit.CockpitPrefs(context).vaultUri != null
            if (!opensLinks && !packageVisible && !hasVault) return emptyList()
            return listOf(
                LinkEntry("${BUILT_IN_PREFIX}cockpit-board", "Cockpit board", "obsidian://cockpit-board"),
                LinkEntry("${BUILT_IN_PREFIX}cockpit-calendar", "Cockpit calendar", "obsidian://cockpit-board?view=calendar"),
            )
        }

        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val hasScheme = trimmed.contains("://")
            val hostPort = Regex("^[^/:]+:\\d+(/.*)?$").matches(trimmed)
            // "javascript:x", "mailto:x" and friends carry a scheme without "//": not web links
            if (!hasScheme && !hostPort && trimmed.substringBefore('/').contains(':')) return null
            val withScheme = if (hasScheme) trimmed else "https://$trimmed"
            val lower = withScheme.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
            return if (withScheme.substringAfter("://").isBlank()) null else withScheme
        }

        fun toJson(links: List<LinkEntry>): String {
            val array = JSONArray()
            links.forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
            }
            return array.toString()
        }

        /** Tolerant: garbage or malformed entries are dropped, never thrown. */
        fun listFromJson(json: String?): List<LinkEntry> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name").trim()
                    val url = o.optString("url").trim()
                    val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                    if (name.isEmpty() || url.isEmpty()) null else LinkEntry(id, name, url)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
