package dev.abhay.hypericon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.abhay.hypericon.export.DownloadsSaver

/** Progress, result and error dialogs for exporting a theme. */
@Composable
fun ExportDialogs(state: ExportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        ExportState.Idle -> Unit
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Exporting theme") },
            text = {
                Column {
                    Text("Rendering ${state.done} of ${state.total} icons…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
        is ExportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Theme exported") },
            text = {
                Column {
                    Text("${state.iconCount} icons saved to ${state.location}")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "To apply it, import the file in the Themes app and apply its icons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { context.startActivity(DownloadsSaver.shareIntent(state.uri)) }) { Text("Share") }
            },
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Export failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
