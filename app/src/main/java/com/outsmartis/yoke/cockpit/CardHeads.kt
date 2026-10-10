package com.outsmartis.yoke.cockpit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The first bytes of every card in the board folder, kept for the life of the process.
 *
 * Reading a file through the storage framework is slow per file, and the agenda line, the
 * Today sheet, quick-add's labels and search all read the same cards. A listing carries each
 * file's modified time and size, so only cards that changed since the last read are read again,
 * a few at a time.
 */
object CardHeads {

    private class Entry(val modified: Long, val size: Long, val head: String)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val readers = Executors.newFixedThreadPool(4)

    /** Each card in [folder] with its head, in listing order; unreadable cards are left out. */
    fun read(vault: VaultAccess, folder: String, maxFiles: Int = 500): List<Pair<VaultAccess.VaultFile, String>> {
        val files = vault.listMarkdown(folder, maxFiles)
        val stale = files.filter { f -> cache[key(vault, f)]?.let { !isFresh(it.modified, it.size, f) } ?: true }
        if (stale.isNotEmpty()) {
            val jobs = stale.map { f -> readers.submit { vault.readHead(f)?.let { cache[key(vault, f)] = Entry(f.modified, f.size, it) } } }
            jobs.forEach { runCatching { it.get(30, TimeUnit.SECONDS) } }
        }
        return files.mapNotNull { f -> cache[key(vault, f)]?.let { f to it.head } }
    }

    /** Forgets [file] after Yoke wrote it, so the next read sees the new content. */
    fun invalidate(vault: VaultAccess, file: VaultAccess.VaultFile) {
        cache.remove(key(vault, file))
    }

    /** A cached head is reused only when the provider reported a time and nothing about the file changed. */
    fun isFresh(cachedModified: Long, cachedSize: Long, file: VaultAccess.VaultFile): Boolean =
        file.modified > 0L && cachedModified == file.modified && cachedSize == file.size

    private fun key(vault: VaultAccess, f: VaultAccess.VaultFile) = vault.cacheKey + "\u0000" + f.id
}
