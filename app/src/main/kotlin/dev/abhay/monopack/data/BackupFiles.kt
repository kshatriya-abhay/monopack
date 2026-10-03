package dev.abhay.monopack.data

import android.content.ContentResolver
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes backup files the user picked (Storage Access Framework URIs), off the main thread. */
class BackupFiles(private val resolver: ContentResolver, private val io: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun write(uri: String, backup: Backup, createdAt: Long = System.currentTimeMillis()) = withContext(io) {
        val bytes = BackupJson.encode(backup, createdAt).toByteArray()
        resolver.openOutputStream(uri.toUri(), "wt")?.use { it.write(bytes) } ?: error("Couldn't write the backup")
    }

    /** The backup in [uri], or null if it isn't a Monopack backup. */
    suspend fun read(uri: String): Backup? = withContext(io) {
        resolver.openInputStream(uri.toUri())?.use { it.readBytes().decodeToString() }?.let(BackupJson::decode)
    }
}
