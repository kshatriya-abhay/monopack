package dev.abhay.monopack.hyperos

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A theme to apply: its filesystem path (for Theme Manager) and file name (for the user). */
data class ThemeToApply(val path: String, val fileName: String, val onApplied: () -> Unit = {})

/** Starts Apply icons for a theme, with the first-time explanation (see [rememberApplyThemeFlow]). */
@Stable
fun interface ApplyTheme {
    operator fun invoke(theme: ThemeToApply)
}

/**
 * Applying a theme opens Theme Manager, which asks the user to pick the theme file. The first time
 * (until "Don't show again"), a dialog says which file to pick ([folderLabel] + file name). Every
 * time, a toast shows the file name alone, so it isn't cut off.
 */
@Composable
fun rememberApplyThemeFlow(folderLabel: String, hintDismissed: Boolean, onDontShowAgain: () -> Unit): ApplyTheme {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ThemeToApply?>(null) }
    val dismissed by rememberUpdatedState(hintDismissed)

    fun launch(theme: ThemeToApply) {
        if (ThemeApplier.apply(context, theme.path)) {
            theme.onApplied()
            Toast.makeText(context, "Pick ${theme.fileName}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Theme Manager isn't available", Toast.LENGTH_SHORT).show()
        }
    }

    pending?.let { theme ->
        var dontShow by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Pick the theme file") },
            text = {
                Column {
                    Text("Theme Manager opens next and asks you to choose the theme. Pick this file:")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (folderLabel.isEmpty()) theme.fileName else "$folderLabel/${theme.fileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(HYPEROS_RESET_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.toggleable(value = dontShow, role = Role.Checkbox, onValueChange = { dontShow = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontShow, onCheckedChange = null)
                        Text("Don't show again")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    if (dontShow) onDontShowAgain()
                    launch(theme)
                }) { Text("Proceed") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    return remember { ApplyTheme { theme -> if (dismissed) launch(theme) else pending = theme } }
}
