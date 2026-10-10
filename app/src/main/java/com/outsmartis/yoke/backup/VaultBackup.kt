package com.outsmartis.yoke.backup

import android.content.Context
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.cockpit.VaultAccess

/** Optional copy of the settings at `.yoke/backup.json` in the picked vault, which Syncthing carries to other phones. */
object VaultBackup {
    const val FOLDER = ".yoke"
    const val PATH = ".yoke/backup.json"
    const val MIN_INTERVAL_MS = 10 * 60_000L

    private val prefsName = "yoke.backup"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun keepCopy(context: Context): Boolean = prefs(context).getBoolean("keep_vault_copy", false)

    fun setKeepCopy(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("keep_vault_copy", on).apply()
    }

    /** Writes the copy now. False when no vault is picked or the write failed. */
    fun write(context: Context): Boolean {
        val vault = CockpitPrefs(context).vault(context) ?: return false
        return runCatching {
            vault.createDirectory(FOLDER)
            vault.writeText(PATH, YokeBackup.export(context))
        }.isSuccess
    }

    /** The copy's text, or null when there is none (or no vault). */
    fun read(context: Context): String? {
        val vault: VaultAccess = CockpitPrefs(context).vault(context) ?: return null
        return runCatching { vault.readText(PATH) }.getOrNull()
    }

    /**
     * Called from MainActivity.onStop: when the toggle is on, at most once per 10 minutes, and only
     * if the settings changed since the last copy. Runs on its own thread.
     */
    fun maybeBackup(context: Context) {
        val app = context.applicationContext
        if (!keepCopy(app)) return
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_vault_backup", 0L) < MIN_INTERVAL_MS) return
        Thread {
            val hash = YokeBackup.fingerprint(app)
            if (hash == p.getString("last_vault_hash", null)) {
                p.edit().putLong("last_vault_backup", now).apply()
                return@Thread
            }
            if (write(app)) p.edit().putLong("last_vault_backup", now).putString("last_vault_hash", hash).apply()
        }.start()
    }
}
