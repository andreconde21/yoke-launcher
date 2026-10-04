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
