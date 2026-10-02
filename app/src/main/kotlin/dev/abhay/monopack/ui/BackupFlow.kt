package dev.abhay.monopack.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import dev.abhay.monopack.R
import dev.abhay.monopack.data.Backup
import dev.abhay.monopack.data.BackupFiles
import dev.abhay.monopack.util.catching
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Starts a backup (the user picks where to save it) or a restore (the user picks the file). */
class BackupActions(val backUp: () -> Unit, val restore: () -> Unit)

/**
 * Backup and restore: [current] builds the backup to save; [onRestored] applies a restored one.
 * The file work happens in [files], off the main thread; the result is reported in a toast.
 */
@Composable
fun rememberBackupFlow(files: BackupFiles, current: () -> Backup, onRestored: (Backup) -> Unit): BackupActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    fun edits(count: Int) = resources.getQuantityString(R.plurals.backup_edits, count, count)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val backup = current()
        scope.launch {
            val saved = catching { files.write(uri.toString(), backup) }
            val text = if (saved.isSuccess) resources.getString(R.string.backup_saved, edits(backup.edits.size)) else resources.getString(R.string.backup_save_failed)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val backup = catching { files.read(uri.toString()) }.getOrNull()
            if (backup == null) {
                Toast.makeText(context, resources.getString(R.string.backup_not_backup), Toast.LENGTH_SHORT).show()
            } else {
                onRestored(backup)
                Toast.makeText(context, resources.getString(R.string.backup_restored, edits(backup.edits.size)), Toast.LENGTH_SHORT).show()
            }
        }
    }
    return remember(save, open) {
        BackupActions(
            backUp = { save.launch("Monopack-backup-${LocalDate.now()}.json") },
            restore = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }
}
