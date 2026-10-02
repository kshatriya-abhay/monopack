package dev.abhay.monopack.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.R
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.InstalledPack
import dev.abhay.monopack.export.PackToInstall
import dev.abhay.monopack.hyperos.ApplyTheme
import dev.abhay.monopack.hyperos.ThemeToApply
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.newapps.NewApps
import dev.abhay.monopack.render.LocalIconShape
import dev.abhay.monopack.ui.InstallPack
import dev.abhay.monopack.ui.Symbols
import dev.abhay.monopack.ui.TooltipIconButton

/**
 * The home screen: themes and icon packs in the library folder, newest first. Tap a theme to apply
 * its icons, tap a pack to edit and rebuild it (or its button to install it), long-press to select
 * and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    onCreate: () -> Unit,
    onToggle: (LibraryItem) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRemoveMissing: (LibraryItem) -> Unit,
    onApplied: (LibraryItem) -> Unit,
    onSettings: () -> Unit,
    applyTheme: ApplyTheme = ApplyTheme {},
    onDismissNewApps: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    /** Tap on an icon pack: Edit icon pack (rebuild it with the same name). */
    onEditPack: (LibraryItem) -> Unit = {},
    installPack: InstallPack = InstallPack {},
    /** The new-apps banner's Update pack: edit the watched pack. */
    onUpdateNewApps: (NewAppsFound) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmDelete by remember { mutableStateOf(false) }

    fun apply(item: LibraryItem) {
        val path = state.pathFor(item)
        if (path == null) {
            Toast.makeText(context, resources.getString(R.string.library_theme_internal_only), Toast.LENGTH_SHORT).show()
        } else {
            applyTheme(ThemeToApply(path, item.file?.name ?: item.fileName) { onApplied(item) })
        }
    }

    fun open(item: LibraryItem) {
        when {
            item.kind == ExportKind.ICON_PACK -> onEditPack(item)
            item.missing -> Unit
            else -> apply(item)
        }
    }

    fun install(item: LibraryItem) {
        val file = item.file ?: return
        installPack(PackToInstall(file.documentUri, item.title, item.packageName ?: PackNaming.packageFor(item.title)))
    }

    Scaffold(
        topBar = {
            if (state.selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selection_count, state.selected.size, state.selected.size)) },
                    navigationIcon = { TooltipIconButton(Icons.Filled.Close, stringResource(R.string.action_cancel), onClearSelection) },
                    actions = {
                        TooltipIconButton(Symbols.SelectAll, stringResource(R.string.action_select_all), onSelectAll)
                        TooltipIconButton(Icons.Filled.Delete, stringResource(R.string.action_delete), { confirmDelete = true })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        val lastTheme = state.lastTheme
                        val reapply = lastTheme?.let { theme -> state.items.firstOrNull { !it.missing && state.pathFor(it) == theme.absolutePath } }
                        if (reapply != null) TextButton(onClick = { apply(reapply) }) { Text(stringResource(R.string.library_reapply)) }
                        TooltipIconButton(Icons.Filled.Settings, stringResource(R.string.settings_title), onSettings)
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.selecting) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_create)) },
                )
            }
        },
    ) { padding ->
        when {
            state.items.isEmpty() -> EmptyLibrary(padding, state.error?.text())
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
                state.newApps?.let { found ->
                    item(key = "new-apps") { NewAppsBanner(found, onUpdate = { onUpdateNewApps(found) }, onDismiss = onDismissNewApps) }
                }
                state.error?.let { error -> item { Text(error.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(state.items, key = { it.fileName }) { item ->
                    LibraryRow(
                        item = item,
                        selected = item.fileName in state.selected,
                        applied = state.isApplied(item),
                        installed = state.installed[item.fileName],
                        onClick = { if (state.selecting) onToggle(item) else open(item) },
                        onLongClick = { onToggle(item) },
                        onRemove = { onRemoveMissing(item) },
                        onInstall = { install(item) },
                    )
                    HorizontalDivider(Modifier.padding(start = 88.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        val count = state.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.library_delete_title, count, count)) },
            text = { Text(pluralStringResource(R.plurals.library_delete_text, count)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** New apps the current icon pack doesn't cover, with a shortcut to update it. */
@Composable
private fun NewAppsBanner(found: NewAppsFound, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            val count = found.apps.size
            Text(
                if (count == 1) stringResource(R.string.new_apps_title_one, found.apps[0].label) else pluralStringResource(R.plurals.new_apps_title, count, count),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.new_apps_banner_text, count, NewApps.names(found.apps, LocalResources.current), found.packLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.new_apps_update_pack)) }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(padding: PaddingValues, error: String?) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: stringResource(R.string.library_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    item: LibraryItem,
    selected: Boolean,
    applied: Boolean,
    installed: InstalledPack?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onInstall: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.action_select))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (item.missing) 0.38f else 1f)) {
            Thumbnail(item)
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.state_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(if (item.missing) 0.38f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Icon packs are the default; HyperOS themes are marked.
                if (item.kind == ExportKind.THEME) {
                    Spacer(Modifier.width(8.dp))
                    Pill(stringResource(R.string.library_kind_theme))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = if (item.missing) stringResource(R.string.library_file_missing) else item.iconCount?.let { pluralStringResource(R.plurals.icon_count, it, it) }
                if (text != null) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val status = when {
                    item.missing -> null
                    applied -> stringResource(R.string.library_applied)
                    installed == InstalledPack.SAME_SIGNER -> stringResource(R.string.library_installed)
                    installed == InstalledPack.OTHER_SIGNER -> stringResource(R.string.library_other_installed)
                    else -> null
                }
                if (status != null) {
                    if (text != null) Spacer(Modifier.width(8.dp))
                    Pill(status)
                }
            }
        }
        when {
            item.missing -> TooltipIconButton(Icons.Filled.Delete, stringResource(R.string.library_remove, item.title), onRemove)
            item.kind == ExportKind.ICON_PACK -> TooltipIconButton(Symbols.Install, stringResource(R.string.library_install, item.title), onInstall)
        }
    }
}

/** The library folder problem, in words. */
@Composable
internal fun LibraryError.text(): String = when (this) {
    is LibraryError.CantRead -> stringResource(R.string.library_error_cant_read, folder)
    LibraryError.CantKeepAccess -> stringResource(R.string.library_error_keep_access)
}

/** A small outlined label (not clickable), like a chip. */
@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .height(24.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The item's icon: Monopack's glyph on a squircle in the export's colours (or neutral colours). */
@Composable
private fun Thumbnail(item: LibraryItem) {
    val plate = item.plate?.let { Color(it) } ?: when (item.style) {
        IconStyle.DARK -> MaterialTheme.colorScheme.inverseSurface
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val glyph = item.glyph?.let { Color(it) } ?: when (item.style) {
        IconStyle.DARK -> MaterialTheme.colorScheme.inverseOnSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = if (item.missing) stringResource(R.string.library_missing_description, item.title) else item.title
    Box(
        Modifier
            .size(56.dp)
            .clip(item.shape?.shape ?: LocalIconShape.current)
            .background(plate)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_launcher_monochrome), contentDescription = null, tint = glyph, modifier = Modifier.requiredSize(84.dp))
    }
}
