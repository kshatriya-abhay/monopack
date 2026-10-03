package dev.abhay.monopack.data

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes backup files the user picked (Storage Access Framework URIs), off the main thread. */
class BackupFiles(private val resolver: ContentResolver, private val io: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun write(uri: String, backup: Backup, createdAt: Long = System.currentTimeMillis()) = withContext(io) {
        val bytes = BackupJson.encode(backup, createdAt).toByteArray()
        resolver.openOutputStream(uri.toUri(), "wt")?.use { it.write(bytes) } ?: error("Couldn't write the backup")
    }

    /** The backup in [uri], or null if it isn't a Monopack backup (including anything over [MAX_BYTES]). */
    suspend fun read(uri: String): Backup? = withContext(io) {
        val bytes = resolver.openInputStream(uri.toUri())?.use { readAtMost(it, MAX_BYTES) } ?: return@withContext null
        BackupJson.decode(bytes.decodeToString())
    }

    /** [input]'s bytes, or null if there are more than [limit]: the picker allows any file, even huge ones. */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    companion object {
        /** Backups are tens of kilobytes (edits for a few hundred apps). */
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
