package dev.abhay.monopack.ui

import android.app.DownloadManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.ExportState
import dev.abhay.monopack.export.ExportedFile
import dev.abhay.monopack.export.LauncherHints
import dev.abhay.monopack.hyperos.ApplyTheme
import dev.abhay.monopack.hyperos.ThemeToApply
import dev.abhay.monopack.model.IconStyle

/** Progress, result and error UI for exports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialogs(
    state: ExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onSaveCopy: (ExportedFile, String, (Boolean) -> Unit) -> Unit,
    /** Apply icons was started for this file (it becomes the Reapply target). */
    onApplied: (ExportedFile) -> Unit = {},
    /** Hides the progress dialog; the export continues with its notification. */
    onHide: () -> Unit = {},
    dialogHidden: Boolean = false,
    /** Shows the library folder in a file manager (to install a pack); false if nothing could. */
    onOpenFolder: () -> Boolean = { false },
    applyTheme: ApplyTheme = ApplyTheme {},
) {
    val context = LocalContext.current
    var saving by remember { mutableStateOf<ExportedFile?>(null) }
    // The MIME type only suggests an extension; the file name decides it.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DownloadsSaver.MIME_TYPE)) { uri ->
        val file = saving
        saving = null
        if (uri != null && file != null) {
            onSaveCopy(file, uri.toString()) { ok ->
                Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (state) {
        ExportState.Idle -> Unit
        is ExportState.Running -> if (!dialogHidden) {
            val pack = state.kind == ExportKind.ICON_PACK
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = {
                    Text(
                        when {
                            pack -> "Building icon pack"
                            state.files > 1 -> "Exporting theme ${state.file} of ${state.files}"
                            else -> "Exporting theme"
                        },
                    )
                },
                text = {
                    Column {
                        Text(if (state.signing) "Building and signing the APK…" else "Rendering ${state.done} of ${state.total} icons…")
                        Spacer(Modifier.height(12.dp))
                        if (state.signing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Hint("You can leave Monopack: the export continues, with a notification.")
                    }
                },
                confirmButton = { TextButton(onClick = onHide) { Text("Hide") } },
                dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        }
        is ExportState.Done -> ModalBottomSheet(onDismissRequest = onDismiss) {
            val pack = state.files.firstOrNull()?.kind == ExportKind.ICON_PACK
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text(
                    when {
                        pack -> "Icon pack exported"
                        state.files.size > 1 -> "Themes exported"
                        else -> "Theme exported"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Hint(
                    if (pack) {
                        "Open the APK in your file manager to install it. Android may ask you to allow installs from that app, and HyperOS runs a security scan first."
                    } else {
                        "Apply icons changes only the icons through Theme Manager; your wallpaper and other theme parts stay as they are. This may not work on every HyperOS build."
                    },
                )
                if (pack) {
                    val launcher = remember { LauncherHints.defaultLauncher(context) }
                    Spacer(Modifier.height(8.dp))
                    Text(LauncherHints.forLauncher(launcher), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Hint(LauncherHints.afterUpdate(launcher))
                }
                state.files.forEach { file ->
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (file.kind == ExportKind.ICON_PACK) "${file.title} · ${file.iconCount} icons" else "${if (file.style == IconStyle.DARK) "Dark" else "Light"} icons · ${file.iconCount} icons",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(file.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (file.kind == ExportKind.ICON_PACK) {
                            Button(onClick = {
                                val opened = onOpenFolder() || runCatching {
                                    context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.isSuccess
                                if (!opened) Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show()
                            }) { Text("Open folder") }
                        } else {
                            Button(onClick = {
                                if (file.absolutePath.isEmpty()) {
                                    Toast.makeText(context, "Themes can only be applied from internal storage", Toast.LENGTH_SHORT).show()
                                } else {
                                    applyTheme(ThemeToApply(file.absolutePath, file.fileName) { onApplied(file) })
                                }
                            }) { Text("Apply icons") }
                        }
                        OutlinedButton(onClick = {
                            saving = file
                            saveAs.launch(file.fileName)
                        }) { Text("Save as…") }
                        TextButton(onClick = { context.startActivity(DownloadsSaver.shareIntent(file.uri, file.fileName)) }) { Text("Share") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (state.kind == ExportKind.ICON_PACK) "Icon pack failed" else "Export failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
