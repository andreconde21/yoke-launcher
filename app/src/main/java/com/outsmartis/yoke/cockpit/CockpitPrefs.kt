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

    /** The vault, if one was picked and its permission is still held. */
    fun vault(context: Context): VaultAccess? {
        val uri = vaultUri?.let(Uri::parse) ?: return null
        val held = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return if (held) VaultAccess(context.contentResolver, uri) else null
    }
}
