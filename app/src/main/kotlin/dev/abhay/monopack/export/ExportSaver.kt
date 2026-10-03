package dev.abhay.monopack.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import dev.abhay.monopack.R
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a saved export ended up. */
data class SavedExport(
    val uri: String,
    val displayPath: String,
    /** Filesystem path (what Theme Manager is given); empty when there's none (a folder off internal storage). */
    val absolutePath: String,
    /** The file name used (the destination may rename on a clash). */
    val name: String = displayPath.substringAfterLast('/'),
)

interface ExportSaver {
    /**
     * Copies the finished file to shared storage, replacing a file of the same name. For an icon
     * pack, [packageName] also removes that pack's older exports (files named before packs got
     * one fixed file name).
     */
    suspend fun save(file: File, packageName: String? = null): SavedExport

    /** Copies the finished file to a user-chosen document (Storage Access Framework). */
    suspend fun copyTo(file: File, uri: String)
}

/** Saves to `Download/Monopack/` through MediaStore (no storage permission needed). */
class DownloadsSaver(
    private val context: Context,
    /** File copies happen here, never on the caller's (often the main) thread. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExportSaver {
    override suspend fun save(file: File, packageName: String?): SavedExport = withContext(io) {
        val resolver = context.contentResolver
        // Replace our earlier file of this name (MediaStore would otherwise add "name (1)").
        runCatching {
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(file.name, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(file.name))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create ${file.name} in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write ${file.name}")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        // MediaStore may rename on a clash; read back the name it used.
        val name = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: file.name
        val absolute = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$FOLDER/$name")
        SavedExport(uri.toString(), "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name", absolute.path)
    }

    override suspend fun copyTo(file: File, uri: String) {
        withContext(io) {
            context.contentResolver.openOutputStream(uri.toUri(), "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write to the chosen file")
        }
    }

    companion object {
        const val FOLDER = "Monopack"
        const val MIME_TYPE = "application/octet-stream"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun mimeTypeFor(fileName: String): String = if (fileName.endsWith(".apk")) APK_MIME_TYPE else MIME_TYPE

        fun shareIntent(context: Context, uri: String, fileName: String = ""): Intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeFor(fileName)
                putExtra(Intent.EXTRA_STREAM, uri.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            context.getString(if (fileName.endsWith(".apk")) R.string.share_pack else R.string.share_theme),
        )
    }
}
