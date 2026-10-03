package dev.abhay.monopack.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abhay.monopack.R
import dev.abhay.monopack.export.InstallState
import dev.abhay.monopack.export.LauncherHints
import dev.abhay.monopack.export.PackInstaller
import dev.abhay.monopack.export.PackToInstall

/** Starts installing a saved icon pack (asking for the install permission first if needed). */
fun interface InstallPack {
    operator fun invoke(pack: PackToInstall)
}

/**
 * The install flow: without "Install unknown apps" for Monopack, explains and opens that setting,
 * then installs on return; opens Android's confirmation screen when it asks; reports the result.
 */
@Composable
fun rememberInstallPackFlow(installer: PackInstaller, onInstalled: () -> Unit = {}): InstallPack {
    val context = LocalContext.current
    val resources = LocalResources.current
    val installed by rememberUpdatedState(onInstalled)
    var asking by remember { mutableStateOf<PackToInstall?>(null) }
    var afterPermission by remember { mutableStateOf<PackToInstall?>(null) }
    val state by installer.state.collectAsStateWithLifecycle()

    fun start(pack: PackToInstall) = installer.install(pack)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val pack = afterPermission ?: return@LifecycleEventEffect
        afterPermission = null
        if (installer.canInstall()) start(pack)
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is InstallState.Installing -> if (s.update) Toast.makeText(context, resources.getString(R.string.install_updating, s.pack.title), Toast.LENGTH_SHORT).show()
            is InstallState.NeedsConfirmation -> {
                runCatching { context.startActivity(s.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                installer.confirmationShown()
            }
            is InstallState.Done -> {
                val text = when {
                    s.success && s.update -> resources.getString(R.string.install_updated, s.pack.title, resources.getString(LauncherHints.afterUpdate(LauncherHints.defaultLauncher(context))))
                    s.success -> resources.getString(R.string.install_installed, s.pack.title, resources.getString(LauncherHints.forLauncher(LauncherHints.defaultLauncher(context))))
                    s.message == null -> resources.getString(R.string.install_cancelled)
                    else -> resources.getString(R.string.install_failed, s.pack.title, s.message)
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                installer.dismiss()
                installed()
            }
            InstallState.Idle -> Unit
        }
    }

    asking?.let { pack ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.install_permission_title)) },
            text = {
                Text(stringResource(R.string.install_permission_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    afterPermission = pack
                    runCatching { context.startActivity(installer.permissionIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(stringResource(R.string.action_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    return remember(installer) {
        InstallPack { pack -> if (installer.canInstall()) start(pack) else asking = pack }
    }
}
