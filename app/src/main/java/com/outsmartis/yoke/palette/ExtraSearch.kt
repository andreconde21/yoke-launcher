package com.outsmartis.yoke.palette

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.outsmartis.yoke.cockpit.CockpitBoardConfig
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.cockpit.VaultAccess

/**
 * The palette's extra plain-text sources (Cockpit cards, vault notes, contacts, settings pages).
 * [warmUp] builds the indexes off the main thread; [search] only reads what is cached, so it is cheap
 * enough to run on every keystroke. Cards are cached 5 minutes, notes and contacts 30.
 */
class ExtraSearch(context: Context) {

    private val app = context.applicationContext

    @Volatile private var cards: Cached = Cached(emptyList(), 0L)
    @Volatile private var notes: Cached = Cached(emptyList(), 0L)
    @Volatile private var contacts: Cached = Cached(emptyList(), 0L)
    @Volatile private var settingsPages: List<SearchHit>? = null
    @Volatile private var building = false

    private class Cached(val hits: List<SearchHit>, val builtAt: Long) {
        fun fresh(ttl: Long, now: Long) = builtAt != 0L && now - builtAt < ttl
    }

    /** Builds whichever enabled indexes are stale, then calls [onReady] on the thread that built them. */
    fun warmUp(onReady: () -> Unit) {
        if (building) return
        val prefs = SearchPrefs(app)
        val now = System.currentTimeMillis()
        val needCards = prefs.cards && !cards.fresh(CARD_TTL, now)
        val needNotes = prefs.notes && !notes.fresh(NOTE_TTL, now)
        val needContacts = prefs.contacts && !contacts.fresh(NOTE_TTL, now)
        if (!needCards && !needNotes && !needContacts) return
        building = true
        Thread {
            try {
                val vault = CockpitPrefs(app).vault(app)
                if (vault != null && (needCards || needNotes)) {
                    val name = runCatching { vault.displayName() }.getOrDefault("")
                    if (needCards) cards = Cached(runCatching { buildCards(vault, name) }.getOrDefault(emptyList()), System.currentTimeMillis())
                    if (needNotes) notes = Cached(runCatching { buildNotes(vault, name) }.getOrDefault(emptyList()), System.currentTimeMillis())
                }
                if (needContacts) contacts = Cached(runCatching { buildContacts() }.getOrDefault(emptyList()), System.currentTimeMillis())
            } finally {
                building = false
            }
            onReady()
        }.start()
    }

    /**
     * True when every enabled index has been built at least once, so a search shows all it
     * will show. Auto-launch waits for this, or it would fire before cards/notes/contacts arrive.
     */
    val ready: Boolean
        get() {
            if (building) return false
            val prefs = SearchPrefs(app)
            return (!prefs.cards || cards.builtAt != 0L || CockpitPrefs(app).vault(app) == null) &&
                (!prefs.notes || notes.builtAt != 0L || CockpitPrefs(app).vault(app) == null) &&
                (!prefs.contacts || contacts.builtAt != 0L || !hasContactsPermission(app))
        }

    /** Rows for [query] from the sources switched on; a source with nothing to offer is skipped. */
    fun search(query: String): List<SearchHit> {
        if (query.trim().length < SearchRanking.MIN_QUERY) return emptyList()
        val prefs = SearchPrefs(app)
        val sources = mutableMapOf<SearchKind, List<SearchHit>>()
        if (prefs.cards) sources[SearchKind.CARD] = cards.hits
        if (prefs.notes) sources[SearchKind.NOTE] = notes.hits
        if (prefs.contacts && hasContactsPermission(app)) sources[SearchKind.CONTACT] = contacts.hits
        if (prefs.settings) sources[SearchKind.SETTING] = settingsPages ?: SettingsPages.hits(::canOpen).also { settingsPages = it }
        return SearchRanking.combine(query, sources)
    }

    private fun canOpen(page: SettingsPage): Boolean =
        page.action.startsWith("yoke:") || app.packageManager.resolveActivity(Intent(page.action), 0) != null

    private fun buildCards(vault: VaultAccess, vaultName: String): List<SearchHit> {
        val folder = vault.readText(VaultAccess.SETTINGS_PATH)
            ?.let { runCatching { CockpitBoardConfig.parse(it).folder }.getOrNull() } ?: return emptyList()
        return vault.listMarkdown(folder, MAX_CARDS).mapNotNull { file ->
            val head = vault.readHead(file) ?: return@mapNotNull null
            val card = CardFrontmatter.parse(file.path, head)
            SearchHit(SearchKind.CARD, card.title, card.subtitle, ObsidianLinks.open(vaultName, card.path))
        }
    }

    private fun buildNotes(vault: VaultAccess, vaultName: String): List<SearchHit> =
        vault.listAllNotes(MAX_NOTES).map { path ->
            SearchHit(SearchKind.NOTE, path.substringAfterLast('/').removeSuffix(".md"), "note · " + path.substringBeforeLast('/', "").ifEmpty { "/" },
                ObsidianLinks.open(vaultName, path))
        }

    private fun buildContacts(): List<SearchHit> {
        if (!hasContactsPermission(app)) return emptyList()
        val seen = HashSet<String>()
        val out = mutableListOf<SearchHit>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        )
        app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext() && out.size < MAX_CONTACTS) {
                val name = c.getString(0) ?: continue
                val key = c.getString(2) ?: continue
                if (!seen.add(key)) continue
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, key).toString()
                out += SearchHit(SearchKind.CONTACT, name, c.getString(1), uri)
            }
        }
        return out
    }

    companion object {
        private const val CARD_TTL = 5 * 60_000L
        private const val NOTE_TTL = 30 * 60_000L
        private const val MAX_CARDS = 500
        private const val MAX_NOTES = 3000
        private const val MAX_CONTACTS = 5000

        fun hasContactsPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
