package dev.abhay.monopack.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.abhay.monopack.newapps.WatchablePack
import dev.abhay.monopack.ui.TooltipIconButton

/** One open-source project Monopack builds on (mirrors the README's Credits). */
data class Credit(val name: String, val license: String, val usedFor: String, val url: String)

val CREDITS = listOf(
    Credit("AOSP Launcher3 iconloaderlib", "Apache-2.0", "Generated themed icons and their colours", "https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/"),
    Credit("AOSP ThemePicker", "Apache-2.0", "Preset basic colours", "https://android.googlesource.com/platform/packages/apps/ThemePicker/"),
    Credit("AOSP frameworks/base", "Apache-2.0", "Spotting missing wallpaper colours", "https://android.googlesource.com/platform/frameworks/base/"),
    Credit("MaterialKolor", "MIT", "Material You palettes from a colour", "https://github.com/jordond/MaterialKolor"),
    Credit("ARSCLib", "Apache-2.0", "Building icon packs on the device", "https://github.com/REAndroid/ARSCLib"),
    Credit("apksig", "Apache-2.0", "Signing icon packs", "https://android.googlesource.com/platform/tools/apksig/"),
    Credit("Alembicons", "GPL-3.0", "The on-device icon pack approach, launcher discovery", "https://codeberg.org/kaanelloed/Alembicons"),
    Credit("CandyBar", "Apache-2.0", "Icon pack formats", "https://github.com/zixpo/candybar"),
    Credit("HyperIcons", "MIT", "HyperOS theme structure and applying themes", "https://github.com/stbenjam/HyperIcons"),
    Credit("HyperMonetIconTheme", "Apache-2.0", "Layered HyperOS icons and their shape", "https://github.com/VincentAzz/HyperMonetIconTheme"),
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
            Toast.makeText(context, "Allow notifications for Monopack to use this", Toast.LENGTH_SHORT).show()
        }
    }

    fun turnOnAlerts() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) onNewAppAlerts(true) else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Couldn't open that", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Storage")
            Item("Change folder", "Where Monopack saves your icon packs") { onChangeFolder() }

            Section("New apps")
            SwitchItem(
                "Notify me",
                "When I install an app the watched pack doesn't cover. Monopack's home screen shows them too.",
                checked = newAppAlerts,
            ) { on ->
                when {
                    !on -> onNewAppAlerts(false)
                    packs.isEmpty() -> Toast.makeText(context, "Install a Monopack icon pack first", Toast.LENGTH_SHORT).show()
                    watchedPack == null -> {
                        enableAfterPick = true
                        choosingPack = true
                    }
                    else -> turnOnAlerts()
                }
            }
            Item(
                "Icon pack to watch",
                when {
                    packs.isEmpty() -> "Install a Monopack icon pack first"
                    watchedPack == null -> "Choose the pack your launcher uses"
                    else -> watchedPack.label
                },
                enabled = newAppAlerts && packs.isNotEmpty(),
            ) { choosingPack = true }

            Section("Notifications")
            Item("Notification settings", "Export progress and new apps") {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }

            Section("HyperOS themes")
            Item(
                "Show the pick-the-file help again",
                if (applyHintDismissed) "The help before applying a theme is turned off" else "The help shows before applying a theme",
                enabled = applyHintDismissed,
            ) {
                onResetApplyHint()
                Toast.makeText(context, "The help will show next time", Toast.LENGTH_SHORT).show()
            }

            Section("About")
            Item("Monopack", "Version $version · GPL-3.0") {}
            Item("Open-source credits", "Projects Monopack builds on") { showingCredits = true }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosingPack) {
        AlertDialog(
            onDismissRequest = {
                choosingPack = false
                enableAfterPick = false
            },
            title = { Text("Icon pack to watch") },
            text = {
                Column {
                    Text(
                        "Apps can't tell which icon pack your launcher uses. Pick it, so Monopack can tell you about new apps it doesn't cover.",
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
                }) { Text("Cancel") }
            },
        )
    }
    if (showingCredits) {
        AlertDialog(
            onDismissRequest = { showingCredits = false },
            title = { Text("Open-source credits") },
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
                            Text(credit.usedFor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingCredits = false }) { Text("Close") } },
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
