package dev.abhay.monopack.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The library folder: a directory the user picked (`ACTION_OPEN_DOCUMENT_TREE`) with a persisted
 * read/write grant. Exports are written, listed and deleted through `DocumentsContract`, so no
 * storage permission is needed.
 */
interface LibraryFolder {
    /**
     * Whether [treeUri] can be used: it still has a persisted read/write grant, and the folder still
     * exists (the grant outlives a folder deleted or renamed in a file manager).
     */
    fun hasAccess(treeUri: String): Boolean

    /** Keeps the grant for a newly picked folder. */
    fun takeAccess(treeUri: String)

    fun releaseAccess(treeUri: String)

    /** "Download/Monopack". */
    fun label(treeUri: String): String

    suspend fun list(treeUri: String): List<FolderFile>

    /** Copies [file] into the folder as [name], replacing a file of that name; returns the document. */
    suspend fun write(treeUri: String, file: File, name: String, mimeType: String): FolderFile

    suspend fun delete(documentUri: String): Boolean

    /** Shows the folder in a file manager (falling back to Downloads); false if nothing could. */
    fun open(treeUri: String): Boolean
}

class SafLibraryFolder(private val context: Context) : LibraryFolder {
    private val resolver get() = context.contentResolver
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    override fun hasAccess(treeUri: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission && it.isWritePermission } && exists(treeUri)

    private fun exists(treeUri: String): Boolean = runCatching {
        val tree = treeUri.toUri()
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    override fun takeAccess(treeUri: String) = resolver.takePersistableUriPermission(treeUri.toUri(), flags)

    override fun releaseAccess(treeUri: String) {
        runCatching { resolver.releasePersistableUriPermission(treeUri.toUri(), flags) }
    }

    override fun label(treeUri: String): String = Library.labelFor(DocumentsContract.getTreeDocumentId(treeUri.toUri()))

    override suspend fun list(treeUri: String): List<FolderFile> = withContext(Dispatchers.IO) {
        val tree = treeUri.toUri()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        resolver.query(children, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val id = c.getString(0)
                    add(FolderFile(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), id, c.getLong(2), c.getLong(3)))
                }
            }
        } ?: error("Can't read the folder")
    }

    override suspend fun write(treeUri: String, file: File, name: String, mimeType: String): FolderFile = withContext(Dispatchers.IO) {
        val tree = treeUri.toUri()
        val existing = list(treeUri).firstOrNull { it.name == name }?.documentUri?.toUri()
        if (existing != null) {
            // Truncate and rewrite in place (the document keeps its URI).
            resolver.openOutputStream(existing, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Couldn't write $name")
            return@withContext FolderFile(name, existing.toString(), DocumentsContract.getDocumentId(existing), file.length(), System.currentTimeMillis())
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val created: Uri = DocumentsContract.createDocument(resolver, parent, mimeType, name) ?: error("Couldn't create $name")
        try {
            resolver.openOutputStream(created, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Couldn't write $name")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
        // The provider may rename on a clash ("name (1).mtz"); read back what it used.
        val id = DocumentsContract.getDocumentId(created)
        val actualName = resolver.query(created, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: name
        FolderFile(actualName, created.toString(), id, file.length(), System.currentTimeMillis())
    }

    override suspend fun delete(documentUri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(resolver, documentUri.toUri()) }.getOrDefault(false)
    }

    override fun open(treeUri: String): Boolean {
        val tree = treeUri.toUri()
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
        )
        return candidates.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }
}
