package dev.abhay.hypericon.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File

/** Where a saved export ended up. */
data class SavedExport(val uri: String, val displayPath: String, val absolutePath: String)

interface ExportSaver {
    /** Copies the finished file to shared storage. */
    suspend fun save(file: File): SavedExport

    /** Copies the finished file to a user-chosen document (Storage Access Framework). */
    suspend fun copyTo(file: File, uri: String)
}

/** Saves to `Download/HyperIcon/` through MediaStore (no storage permission needed). */
class DownloadsSaver(private val context: Context) : ExportSaver {
    override suspend fun save(file: File): SavedExport {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
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
        return SavedExport(uri.toString(), "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name", absolute.path)
    }

    override suspend fun copyTo(file: File, uri: String) {
        context.contentResolver.openOutputStream(uri.toUri(), "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Couldn't write to the chosen file")
    }

    companion object {
        const val FOLDER = "HyperIcon"
        const val MIME_TYPE = "application/octet-stream"

        fun shareIntent(uri: String): Intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share theme",
        )
    }
}
