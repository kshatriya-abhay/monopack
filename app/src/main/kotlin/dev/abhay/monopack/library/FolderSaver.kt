package dev.abhay.monopack.library

import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportSaver
import dev.abhay.monopack.export.SavedExport
import java.io.File

/**
 * Saves exports into the library folder; without one (or without access to it), falls back to
 * [DownloadsSaver] (`Download/Monopack/` through MediaStore).
 */
class FolderSaver(
    private val store: LibraryStore,
    private val folder: LibraryFolder,
    private val fallback: DownloadsSaver,
) : ExportSaver {
    override suspend fun save(file: File): SavedExport {
        val tree = store.loadTree()?.takeIf { folder.hasAccess(it) } ?: return fallback.save(file)
        val saved = folder.write(tree, file, file.name, DownloadsSaver.mimeTypeFor(file.name))
        return SavedExport(
            uri = saved.documentUri,
            displayPath = "${folder.label(tree)}/${saved.name}",
            absolutePath = Library.pathFor(saved.documentId).orEmpty(),
            name = saved.name,
        )
    }

    override suspend fun copyTo(file: File, uri: String) = fallback.copyTo(file, uri)
}
