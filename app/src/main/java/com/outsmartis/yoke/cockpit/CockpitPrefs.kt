package com.outsmartis.yoke.cockpit

import android.content.Context
import android.net.Uri

/** Where the vault is and which column quick-add starts on. */
class CockpitPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yoke.cockpit", Context.MODE_PRIVATE)

    var vaultUri: String?
        get() = prefs.getString("vault_uri", null)
        set(value) = prefs.edit().putString("vault_uri", value).apply()


    /** Labels seen on the board's cards last time, so the label row shows at once. */
    var knownLabels: Set<String>
        get() = prefs.getStringSet("known_labels", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("known_labels", value).apply()

    /** The optional home-screen agenda line (off by default), its cached text and when it was computed. */
    var agendaEnabled: Boolean
        get() = prefs.getBoolean("agenda_enabled", false)
        set(value) = prefs.edit().putBoolean("agenda_enabled", value).apply()

    var agendaLine: String
        get() = prefs.getString("agenda_line", "") ?: ""
        set(value) = prefs.edit().putString("agenda_line", value).apply()

    var agendaAt: Long
        get() = prefs.getLong("agenda_at", 0L)
        set(value) = prefs.edit().putLong("agenda_at", value).apply()

    /** The vault, if one was picked and its permission is still held. */
    fun vault(context: Context): VaultAccess? {
        val uri = vaultUri?.let(Uri::parse) ?: return null
        val held = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return if (held) VaultAccess(context.contentResolver, uri) else null
    }
}
