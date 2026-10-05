package com.outsmartis.yoke.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** What the now-playing line shows. */
data class NowPlaying(val packageName: String, val title: String, val artist: String, val playing: Boolean)

/**
 * In-memory view of the notifications of the apps the user picked. Nothing is persisted or sent
 * anywhere; the home screen observes [entries] and [nowPlaying].
 */
object NotificationStore {
    private val _entries = MutableLiveData<List<NoteEntry>>(emptyList())
    val entries: LiveData<List<NoteEntry>> get() = _entries

    private val _nowPlaying = MutableLiveData<NowPlaying?>(null)
    val nowPlaying: LiveData<NowPlaying?> get() = _nowPlaying

    @Volatile
    internal var service: YokeNotificationListener? = null

    /** The listener is bound, i.e. Notification access is granted and the service is alive. */
    val connected: Boolean get() = service != null

    internal fun publish(list: List<NoteEntry>, playing: NowPlaying?) {
        _entries.postValue(list)
        _nowPlaying.postValue(playing)
    }

    /** Re-reads the prefs (allowed apps, toggles); call after they change or the home resumes. */
    fun refresh() {
        service?.rebuild()
    }

    fun cancel(keys: List<String>) {
        val s = service ?: return
        keys.forEach { runCatching { s.cancelNotification(it) } }
    }

    fun find(key: String): StatusBarNotification? =
        runCatching { service?.activeNotifications?.firstOrNull { it.key == key } }.getOrNull()

    fun mediaController(): MediaController? = service?.currentController()
}

class YokeNotificationListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        NotificationStore.service = this
        rebuild()
    }

    override fun onListenerDisconnected() {
        if (NotificationStore.service === this) NotificationStore.service = null
        NotificationStore.publish(emptyList(), null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

    fun rebuild() {
        val prefs = NotificationPrefs(this)
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val entries = if (prefs.enabled) {
            NotificationLogic.visible(active.map { it.toEntry() }, prefs.allowed)
        } else emptyList()
        val playing = if (prefs.enabled && prefs.nowPlaying) currentController()?.toNowPlaying() else null
        NotificationStore.publish(entries, playing)
    }

    /** The active media session, preferring one that is playing. Needs Notification access. */
    fun currentController(): MediaController? = try {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = manager.getActiveSessions(ComponentName(this, YokeNotificationListener::class.java))
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    } catch (e: SecurityException) {
        null
    }

    private fun MediaController.toNowPlaying(): NowPlaying? {
        val meta = metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        if (title.isBlank() && artist.isBlank()) return null
        val state = playbackState?.state
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) return null
        return NowPlaying(packageName, title, artist, state == PlaybackState.STATE_PLAYING)
    }

    private fun StatusBarNotification.toEntry(): NoteEntry {
        val n = notification
        val extras = n.extras
        val flags = n.flags
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return NoteEntry(
            key = key,
            packageName = packageName,
            title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString(),
            text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString(),
            postTime = postTime,
            ongoing = isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
                flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            groupKey = n.group,
            media = template.endsWith("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT,
            autoCancel = flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }
}
