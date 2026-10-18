package com.outsmartis.yoke.glance

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.cockpit.CardHeads
import com.outsmartis.yoke.cockpit.CockpitAgendaRepository
import com.outsmartis.yoke.cockpit.CockpitAgenda
import com.outsmartis.yoke.cockpit.CockpitBoardConfig
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.cockpit.CockpitToday
import com.outsmartis.yoke.cockpit.VaultAccess
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Reads today's and tomorrow's events from the chosen source off the main thread and keeps them for the life of
 * the process, so home can pick the next one at once and again whenever an event ends.
 */
object GlanceRepository {

    const val PERMISSION = Manifest.permission.READ_CALENDAR
    private const val MAX_EVENTS = 200

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** How long a Cockpit read is reused, like the agenda line. */
    private val COCKPIT_MAX_AGE_MS = CockpitAgendaRepository.MAX_AGE_MS

    @Volatile var events: List<GlanceEvent> = emptyList()
        private set

    // Bumped by clear(); a read that started before it belongs to the old source and is dropped.
    @Volatile private var generation = 0
    @Volatile private var cockpitReadAt = 0L

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Forgets what was read, e.g. after the source was changed. */
    fun clear() {
        generation++
        cockpitReadAt = 0L
        events = emptyList()
    }

    /** Reads the source again; [onDone] runs on the main thread. Nothing is read when the card or the source is off. */
    fun refresh(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        val prefs = GlancePrefs(app)
        if (!prefs.enabled || prefs.source == GlanceSource.NONE) {
            events = emptyList()
            return
        }
        val source = prefs.source
        // Reading up to 500 vault files: reuse the last read for a while (the card still re-picks on every tick)
        if (source == GlanceSource.COCKPIT && System.currentTimeMillis() - cockpitReadAt < COCKPIT_MAX_AGE_MS) return
        val gen = generation
        io.execute {
            val read = runCatching {
                when (source) {
                    GlanceSource.CALENDAR -> readCalendar(app)
                    GlanceSource.COCKPIT -> readCockpit(app)
                    GlanceSource.NONE -> emptyList()
                }
            }.getOrNull() ?: return@execute  // a failed read keeps the last events
            if (gen != generation) return@execute  // the source changed while reading
            if (source == GlanceSource.COCKPIT) cockpitReadAt = System.currentTimeMillis()
            events = read
            main.post(onDone)
        }
    }

    private fun readCalendar(context: Context): List<GlanceEvent> {
        if (!hasCalendarPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayMs = 24 * 60 * 60 * 1000L
        // A day of slack each side: all-day instances sit on UTC midnights. GlanceLogic drops what is outside.
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli() - dayMs
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + dayMs
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val out = ArrayList<GlanceEvent>()
        // Visible calendars only, and no declined or cancelled events (both columns may be null)
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}) AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
        context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX_EVENTS) {
                val allDay = c.getInt(4) == 1
                val begin = c.getLong(2)
                val end = c.getLong(3)
                out += GlanceEvent(
                    title = c.getString(1).orEmpty().trim().ifEmpty { "(no title)" },
                    start = if (allDay) GlanceLogic.allDayToLocal(begin, zone) else begin,
                    end = if (allDay) GlanceLogic.allDayToLocal(end, zone) else end,
                    allDay = allDay,
                    uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, c.getLong(0)).toString(),
                )
            }
        }
        return out
    }

    private fun readCockpit(context: Context): List<GlanceEvent> {
        val vault = CockpitPrefs(context).vault(context) ?: return emptyList()
        val cfg = vault.readText(VaultAccess.SETTINGS_PATH)?.let { CockpitBoardConfig.parse(it) }
            ?: CockpitBoardConfig("", CockpitBoardConfig.DEFAULT_COLUMNS)
        val name = vault.displayName()
        val cards = CardHeads.read(vault, cfg.folder).mapNotNull { (file, head) ->
            val h = CockpitAgenda.parseHead(head) ?: return@mapNotNull null
            h to CockpitToday.obsidianUri(name, file.path)
        }
        val zone = ZoneId.systemDefault()
        return GlanceLogic.cockpitEvents(cards, LocalDate.now(zone), zone)
    }
}
