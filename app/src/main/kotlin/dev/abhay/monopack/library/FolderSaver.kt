package dev.abhay.monopack.library

import android.util.Log
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportSaver
import dev.abhay.monopack.export.SavedExport
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves exports into the library folder; without one (or without access to it), falls back to
 * [DownloadsSaver] (`Download/Monopack/` through MediaStore).
 */
class FolderSaver(
    private val store: LibraryStore,
    private val folder: LibraryFolder,
    private val fallback: ExportSaver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExportSaver {
    override suspend fun save(file: File, packageName: String?): SavedExport {
        // Without a usable folder (no grant, or deleted in a file manager), save to Downloads instead.
        val tree = store.loadTree()?.takeIf { withContext(io) { folder.hasAccess(it) } } ?: return fallback.save(file, packageName)
        val saved = folder.write(tree, file, file.name, DownloadsSaver.mimeTypeFor(file.name))
        if (packageName != null) removeOlderExports(tree, packageName, keep = saved.name)
        return SavedExport(
            uri = saved.documentUri,
            displayPath = "${folder.label(tree)}/${saved.name}",
            absolutePath = Library.pathFor(saved.documentId).orEmpty(),
            name = saved.name,
        )
    }

    override suspend fun copyTo(file: File, uri: String) = fallback.copyTo(file, uri)

    /** Deletes the pack's other recorded files (older, date-stamped exports) and their records. */
    private suspend fun removeOlderExports(tree: String, packageName: String, keep: String) {
        runCatching {
            val older = store.loadRecords().values.filter { it.packageName == packageName && it.fileName != keep }.map { it.fileName }.toSet()
            if (older.isEmpty()) return
            folder.list(tree).filter { it.name in older }.forEach { folder.delete(it.documentUri) }
            store.removeRecords(older)
        }.onFailure { Log.w("Monopack", "Couldn't remove older exports of $packageName", it) }
    }
}
