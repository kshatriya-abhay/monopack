package dev.abhay.monopack.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.abhay.monopack.R
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.ExportState
import dev.abhay.monopack.export.ExportedFile
import dev.abhay.monopack.export.LauncherHints
import dev.abhay.monopack.export.PackToInstall
import dev.abhay.monopack.hyperos.ApplyTheme
import dev.abhay.monopack.hyperos.ThemeToApply
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.model.IconStyle

/** Progress, result and error UI for exports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialogs(
    state: ExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    /** Copies the exported file at a cache path to a picked document ("Save as…"). */
    onSaveCopy: (cachePath: String, uri: String, (Boolean) -> Unit) -> Unit,
    /** Apply icons was started for this file (it becomes the Reapply target). */
    onApplied: (ExportedFile) -> Unit = {},
    /** Hides the progress dialog; the export continues with its notification. */
    onHide: () -> Unit = {},
    dialogHidden: Boolean = false,
    /** Installs a finished icon pack. */
    installPack: InstallPack = InstallPack {},
    applyTheme: ApplyTheme = ApplyTheme {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Saveable: the picker can outlive the activity (rotation, dark mode) or the process.
    var savingPath by rememberSaveable { mutableStateOf<String?>(null) }
    // The MIME type only suggests an extension; the file name decides it.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DownloadsSaver.MIME_TYPE)) { uri ->
        val path = savingPath
        savingPath = null
        if (uri != null && path != null) {
            onSaveCopy(path, uri.toString()) { ok ->
                Toast.makeText(context, if (ok) resources.getString(R.string.export_saved) else resources.getString(R.string.export_save_failed), Toast.LENGTH_SHORT).show()
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
                            pack -> stringResource(R.string.export_building_pack)
                            state.files > 1 -> stringResource(R.string.export_theme_n_of, state.file, state.files)
                            else -> stringResource(R.string.export_exporting_theme)
                        },
                    )
                },
                text = {
                    Column {
                        Text(if (state.signing) stringResource(R.string.export_signing) else pluralStringResource(R.plurals.export_rendering, state.total, state.done, state.total))
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
                        Hint(stringResource(R.string.export_can_leave))
                    }
                },
                confirmButton = { TextButton(onClick = onHide) { Text(stringResource(R.string.action_hide)) } },
                dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        is ExportState.Done -> ModalBottomSheet(onDismissRequest = onDismiss) {
            val pack = state.files.firstOrNull()?.kind == ExportKind.ICON_PACK
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text(
                    when {
                        pack -> stringResource(R.string.export_done_pack)
                        state.files.size > 1 -> stringResource(R.string.export_done_themes)
                        else -> stringResource(R.string.export_done_theme)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Hint(
                    if (pack) {
                        stringResource(R.string.export_done_pack_hint)
                    } else {
                        stringResource(R.string.export_done_theme_hint)
                    },
                )
                if (pack) {
                    val launcher = remember { LauncherHints.defaultLauncher(context) }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(LauncherHints.forLauncher(launcher)), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Hint(stringResource(LauncherHints.afterUpdate(launcher)))
                }
                state.files.forEach { file ->
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (file.kind == ExportKind.ICON_PACK) stringResource(R.string.export_file_pack, file.title, pluralStringResource(R.plurals.icon_count, file.iconCount, file.iconCount)) else stringResource(R.string.export_file_pack, stringResource(if (file.style == IconStyle.DARK) R.string.export_dark_icons else R.string.export_light_icons), pluralStringResource(R.plurals.icon_count, file.iconCount, file.iconCount)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(file.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (file.kind == ExportKind.ICON_PACK) {
                            Button(onClick = {
                                installPack(PackToInstall(file.uri, file.title, PackNaming.packageFor(file.title)))
                            }) { Text(stringResource(R.string.action_install)) }
                        } else {
                            Button(onClick = {
                                if (file.absolutePath.isEmpty()) {
                                    Toast.makeText(context, resources.getString(R.string.library_theme_internal_only), Toast.LENGTH_SHORT).show()
                                } else {
                                    applyTheme(ThemeToApply(file.absolutePath, file.fileName) { onApplied(file) })
                                }
                            }) { Text(stringResource(R.string.export_apply_icons)) }
                        }
                        OutlinedButton(onClick = {
                            savingPath = file.cachePath
                            saveAs.launch(file.fileName)
                        }) { Text(stringResource(R.string.export_save_as)) }
                        TextButton(onClick = { context.startActivity(DownloadsSaver.shareIntent(context, file.uri, file.fileName)) }) { Text(stringResource(R.string.action_share)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (state.kind == ExportKind.ICON_PACK) stringResource(R.string.export_failed_pack) else stringResource(R.string.export_failed)) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
