package dev.abhay.monopack.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.abhay.monopack.R
import dev.abhay.monopack.newapps.WatchablePack
import dev.abhay.monopack.ui.TooltipIconButton

/** One open-source project Monopack builds on (mirrors the README's Credits). */
data class Credit(val name: String, val license: String, @StringRes val usedFor: Int, val url: String)

val CREDITS = listOf(
    Credit("AOSP Launcher3 iconloaderlib", "Apache-2.0", R.string.credit_iconloaderlib, "https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/"),
    Credit("AOSP ThemePicker", "Apache-2.0", R.string.credit_themepicker, "https://android.googlesource.com/platform/packages/apps/ThemePicker/"),
    Credit("AOSP frameworks/base", "Apache-2.0", R.string.credit_frameworks, "https://android.googlesource.com/platform/frameworks/base/"),
    Credit("MaterialKolor", "MIT", R.string.credit_materialkolor, "https://github.com/jordond/MaterialKolor"),
    Credit("ARSCLib", "Apache-2.0", R.string.credit_arsclib, "https://github.com/REAndroid/ARSCLib"),
    Credit("apksig", "Apache-2.0", R.string.credit_apksig, "https://android.googlesource.com/platform/tools/apksig/"),
    Credit("Alembicons", "GPL-3.0", R.string.credit_alembicons, "https://codeberg.org/kaanelloed/Alembicons"),
    Credit("CandyBar", "Apache-2.0", R.string.credit_candybar, "https://github.com/zixpo/candybar"),
    Credit("HyperIcons", "MIT", R.string.credit_hypericons, "https://github.com/stbenjam/HyperIcons"),
    Credit("HyperMonetIconTheme", "Apache-2.0", R.string.credit_hypermonet, "https://github.com/VincentAzz/HyperMonetIconTheme"),
)

/**
 * App settings, opened from the home screen: the library folder, new-app checks, notifications,
 * HyperOS theme help, and about/credits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    applyHintDismissed: Boolean,
    onChangeFolder: () -> Unit,
    /** Shows the library folder in a file manager; false if nothing could. */
    onOpenFolder: () -> Boolean = { false },
    /** Whether Monopack may install packs ("Install unknown apps"), and the way to allow it. */
    canInstall: Boolean = false,
    onAllowInstalls: () -> Unit = {},
    /** Saves a backup file / restores one (the screens pick the file). */
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onResetApplyHint: () -> Unit,
    onBack: () -> Unit,
    newAppAlerts: Boolean = false,
    onNewAppAlerts: (Boolean) -> Unit = {},
    /** Installed Monopack packs, and the one new apps are checked against (null: none, or not picked yet). */
    packs: List<WatchablePack> = emptyList(),
    watchedPack: WatchablePack? = null,
    onWatchPack: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var showingCredits by remember { mutableStateOf(false) }
    var choosingPack by remember { mutableStateOf(false) }
    // Turning notifications on waits for a pack to be picked first (when there's a choice).
    var enableAfterPick by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    // The new-app notification needs the notification permission; it's only turned on once granted.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onNewAppAlerts(true)
        } else {
            Toast.makeText(context, resources.getString(R.string.settings_allow_notifications), Toast.LENGTH_SHORT).show()
        }
    }

    fun turnOnAlerts() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) onNewAppAlerts(true) else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, resources.getString(R.string.error_cant_open), Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_section_storage))
            Item(stringResource(R.string.settings_open_folder), stringResource(R.string.settings_open_folder_summary)) {
                if (!onOpenFolder()) Toast.makeText(context, resources.getString(R.string.error_no_file_manager), Toast.LENGTH_SHORT).show()
            }
            Item(stringResource(R.string.settings_change_folder), stringResource(R.string.settings_change_folder_summary)) { onChangeFolder() }
            Item(
                stringResource(R.string.settings_install),
                if (canInstall) stringResource(R.string.settings_install_allowed) else stringResource(R.string.settings_install_not_allowed),
            ) { onAllowInstalls() }

            Section(stringResource(R.string.settings_section_backup))
            Item(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_summary)) { onBackup() }
            Item(stringResource(R.string.settings_restore), stringResource(R.string.settings_restore_summary)) { onRestore() }

            Section(stringResource(R.string.settings_section_new_apps))
            SwitchItem(
                stringResource(R.string.settings_notify),
                stringResource(R.string.settings_notify_summary),
                checked = newAppAlerts,
            ) { on ->
                when {
                    !on -> onNewAppAlerts(false)
                    packs.isEmpty() -> Toast.makeText(context, resources.getString(R.string.settings_install_pack_first), Toast.LENGTH_SHORT).show()
                    watchedPack == null -> {
                        enableAfterPick = true
                        choosingPack = true
                    }
                    else -> turnOnAlerts()
                }
            }
            Item(
                stringResource(R.string.settings_watch),
                when {
                    packs.isEmpty() -> stringResource(R.string.settings_install_pack_first)
                    watchedPack == null -> stringResource(R.string.settings_watch_choose)
                    else -> watchedPack.label
                },
                enabled = newAppAlerts && packs.isNotEmpty(),
            ) { choosingPack = true }

            Section(stringResource(R.string.settings_section_notifications))
            Item(stringResource(R.string.settings_notification_settings), stringResource(R.string.settings_notification_settings_summary)) {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }

            Section(stringResource(R.string.settings_section_hyperos))
            Item(
                stringResource(R.string.settings_apply_help),
                if (applyHintDismissed) stringResource(R.string.settings_apply_help_off) else stringResource(R.string.settings_apply_help_on),
                enabled = applyHintDismissed,
            ) {
                onResetApplyHint()
                Toast.makeText(context, resources.getString(R.string.settings_apply_help_reset), Toast.LENGTH_SHORT).show()
            }

            Section(stringResource(R.string.settings_section_about))
            Item(stringResource(R.string.app_name), stringResource(R.string.settings_version, version)) {}
            Item(stringResource(R.string.settings_credits), stringResource(R.string.settings_credits_summary)) { showingCredits = true }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosingPack) {
        AlertDialog(
            onDismissRequest = {
                choosingPack = false
                enableAfterPick = false
            },
            title = { Text(stringResource(R.string.settings_watch)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_watch_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    packs.forEach { pack ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = pack == watchedPack, role = Role.RadioButton, onClick = {
                                    choosingPack = false
                                    onWatchPack(pack.packageName)
                                    if (enableAfterPick) turnOnAlerts()
                                    enableAfterPick = false
                                })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pack == watchedPack, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(pack.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    choosingPack = false
                    enableAfterPick = false
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (showingCredits) {
        AlertDialog(
            onDismissRequest = { showingCredits = false },
            title = { Text(stringResource(R.string.settings_credits)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CREDITS.forEach { credit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open(Intent(Intent.ACTION_VIEW, credit.url.toUri())) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text("${credit.name} · ${credit.license}", style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(credit.usedFor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingCredits = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Item(title: String, summary: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    }
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
