package com.outsmartis.yoke.cockpit

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.Executors

/** Computes the agenda line off the main thread and caches it in [CockpitPrefs], like WeatherRepository. */
object CockpitAgendaRepository {

    const val MAX_AGE_MS = 10 * 60 * 1000L

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Recomputes when the cache is older than [MAX_AGE_MS] (or [force]); [onDone] runs on the main thread after a refresh. */
    fun refreshIfNeeded(context: Context, force: Boolean = false, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val prefs = CockpitPrefs(app)
        if (!prefs.agendaEnabled) return
        if (!force && System.currentTimeMillis() - prefs.agendaAt < MAX_AGE_MS) return
        io.execute {
            val vault = prefs.vault(app)
            val line = if (vault == null) "" else runCatching {
                val cfg = vault.readText(VaultAccess.SETTINGS_PATH)?.let { CockpitBoardConfig.parse(it) }
                    ?: CockpitBoardConfig("", CockpitBoardConfig.DEFAULT_COLUMNS)
                CockpitAgenda.lineFromTexts(CardHeads.read(vault, cfg.folder).map { it.second }, LocalDate.now())
            }.getOrNull() ?: return@execute  // a failed read keeps the last line
            prefs.agendaLine = line
            prefs.agendaAt = System.currentTimeMillis()
            main.post(onDone)
        }
    }
}
