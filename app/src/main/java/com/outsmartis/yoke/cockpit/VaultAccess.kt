package com.outsmartis.yoke.cockpit

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Reads and writes vault files through a Storage Access Framework tree the
 * user granted once (the Syncthing-synced vault folder). Paths are
 * vault-relative with "/" separators, as Obsidian writes them.
 */
class VaultAccess(private val resolver: ContentResolver, private val treeUri: Uri) {

    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun exists(path: String): Boolean = find(path) != null

    fun readText(path: String): String? {
        val id = find(path) ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /**
     * The first [bytes] of each `.md` file directly in [folder] (at most [maxFiles]), for
     * reading frontmatter cheaply: one directory listing, then one small read per file.
     */
    fun readHeads(folder: String, maxFiles: Int = 400, bytes: Int = 1024): List<String> {
        val folderId = if (folder.isEmpty()) rootId else find(folder) ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val ids = mutableListOf<String>()
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext() && ids.size < maxFiles) if (c.getString(1).endsWith(".md")) ids += c.getString(0)
        }
        return ids.mapNotNull { id ->
            runCatching {
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))?.use {
                    val buf = ByteArray(bytes)
                    val n = it.read(buf)
                    if (n > 0) String(buf, 0, n, Charsets.UTF_8) else null
                }
            }.getOrNull()
        }
    }

    /** Creates `path` (its folders must exist) and writes [content]. Throws if it already exists. */
    fun createText(path: String, content: String) {
        val parentPath = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val parentId = if (parentPath.isEmpty()) rootId
        else find(parentPath) ?: throw IllegalStateException("Folder not found in vault: $parentPath")
        check(child(parentId, name) == null) { "Already exists: $path" }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        // "application/octet-stream" keeps providers from appending an extension to "x.md".
        val uri = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            ?: throw IllegalStateException("Could not create $path")
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Could not write $path")
    }

    /** Document id for a vault-relative path, or null. */
    private fun find(path: String): String? {
        var id = rootId
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            id = child(id, segment) ?: return null
        }
        return id
    }

    private fun child(parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == name) return c.getString(0)
        }
        return null
    }

    companion object {
        const val SETTINGS_PATH = ".obsidian/plugins/cockpit-board/data.json"
    }
}
