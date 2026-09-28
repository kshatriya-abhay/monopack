package dev.abhay.monopack.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhay.monopack.R
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.InstalledPack
import dev.abhay.monopack.hyperos.ApplyTheme
import dev.abhay.monopack.hyperos.ThemeToApply
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.render.IconShape
import dev.abhay.monopack.render.LocalIconShape
import java.text.DateFormat
import java.util.Date

/**
 * The home screen: themes and icon packs in the library folder, newest first. Tap a theme to apply
 * its icons, tap a pack to open the folder (to install it), long-press to select and delete.
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
    onOpenFolder: () -> Boolean,
    onChangeFolder: () -> Unit,
    applyTheme: ApplyTheme = ApplyTheme {},
    onIconShape: (IconShape) -> Unit = {},
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var choosingShape by remember { mutableStateOf(false) }

    fun apply(item: LibraryItem) {
        val path = state.pathFor(item)
        if (path == null) {
            Toast.makeText(context, "Themes can only be applied from internal storage", Toast.LENGTH_SHORT).show()
        } else {
            applyTheme(ThemeToApply(path, item.file?.name ?: item.fileName) { onApplied(item) })
        }
    }

    fun open(item: LibraryItem) {
        when {
            item.missing -> Unit
            item.kind == ExportKind.THEME -> apply(item)
            onOpenFolder() -> Toast.makeText(context, "Tap ${item.fileName} to install it", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            if (state.selecting) {
                TopAppBar(
                    title = { Text("${state.selected.size} selected") },
                    navigationIcon = { TextButton(onClick = onClearSelection) { Text("Cancel") } },
                    actions = { TextButton(onClick = { confirmDelete = true }) { Text("Delete") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Monopack")
                            if (state.items.isNotEmpty()) {
                                Text(
                                    "${state.items.size} ${if (state.items.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        val lastTheme = state.lastTheme
                        val reapply = lastTheme?.let { theme -> state.items.firstOrNull { !it.missing && state.pathFor(it) == theme.absolutePath } }
                        if (reapply != null) TextButton(onClick = { apply(reapply) }) { Text("Reapply") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Icon shape") }, onClick = {
                                    menu = false
                                    choosingShape = true
                                })
                                DropdownMenuItem(text = { Text("Change folder") }, onClick = {
                                    menu = false
                                    onChangeFolder()
                                })
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.selecting) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Create") },
                )
            }
        },
    ) { padding ->
        when {
            state.items.isEmpty() -> EmptyLibrary(padding, state.error)
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(state.items, key = { it.fileName }) { item ->
                    LibraryRow(
                        item = item,
                        selected = item.fileName in state.selected,
                        applied = state.isApplied(item),
                        installed = state.installed[item.fileName],
                        onClick = { if (state.selecting) onToggle(item) else open(item) },
                        onLongClick = { onToggle(item) },
                        onRemove = { onRemoveMissing(item) },
                    )
                    HorizontalDivider(Modifier.padding(start = 88.dp))
                }
            }
        }
    }

    if (choosingShape) {
        IconShapeDialog(current = state.iconShape, onPick = {
            choosingShape = false
            onIconShape(it)
        }, onDismiss = { choosingShape = false })
    }

    if (confirmDelete) {
        val count = state.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $count ${if (count == 1) "item" else "items"}?") },
            text = { Text(if (count == 1) "Its file is deleted too." else "Their files are deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyLibrary(padding: PaddingValues, error: String?) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "Tap Create to make themed icons, then export them as an icon pack for your launcher (or a HyperOS theme).",
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (item.missing) 0.38f else 1f)) {
            Thumbnail(item)
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(if (item.missing) 0.38f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(if (item.kind == ExportKind.THEME) "Theme" else "Icon pack", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp),
                )
            }
            val details = buildList {
                item.iconCount?.let { add("$it icons") }
                add(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt)))
                if (applied) add("Applied")
                when (installed) {
                    InstalledPack.SAME_SIGNER -> add("Installed")
                    InstalledPack.OTHER_SIGNER -> add("Another version installed")
                    else -> Unit
                }
            }
            Text(
                if (item.missing) "File missing" else details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.missing) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.title}") }
        }
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
    Box(
        Modifier
            .size(56.dp)
            .clip(LocalIconShape.current)
            .background(plate)
            .semantics { contentDescription = if (item.missing) "${item.title}, file missing" else item.title },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_launcher_monochrome), contentDescription = null, tint = glyph, modifier = Modifier.requiredSize(84.dp))
    }
}

/** Picks the preview icon shape, showing each shape on a sample plate. */
@Composable
private fun IconShapeDialog(current: IconShape, onPick: (IconShape) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon shape") },
        text = {
            Column {
                Text(
                    "Match your launcher's icon shape, so previews look like your home screen. Exports aren't affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                IconShape.entries.forEach { shape ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = shape == current, role = Role.RadioButton, onClick = { onPick(shape) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = shape == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(32.dp).clip(shape.shape).background(MaterialTheme.colorScheme.primaryContainer))
                        Spacer(Modifier.width(12.dp))
                        Text(shape.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
