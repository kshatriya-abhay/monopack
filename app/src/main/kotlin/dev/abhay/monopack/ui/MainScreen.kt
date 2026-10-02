package dev.abhay.monopack.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.abhay.monopack.export.ExportState
import dev.abhay.monopack.hyperos.ApplyTheme
import dev.abhay.monopack.hyperos.ThemeApplier
import dev.abhay.monopack.library.PackNameWarning
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.GlyphSource
import dev.abhay.monopack.render.IconShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    /** Back to the library (home). */
    onBack: () -> Unit = {},
    /** Installs a finished icon pack. */
    installPack: InstallPack = InstallPack {},
    /** Apply icons, with the first-time explanation and the file-name toast. */
    applyTheme: ApplyTheme = ApplyTheme {},
    /** Same-name warning for icon packs, from the library. */
    packNameWarning: (String) -> PackNameWarning? = { null },
    iconShape: IconShape = IconShape.DEFAULT,
    onIconShape: (IconShape) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportOptions by remember { mutableStateOf<ExportOptions?>(null) }
    var collapseRequests by remember { mutableIntStateOf(0) }
    var expandRequests by remember { mutableIntStateOf(0) }
    val previewed = state.committedPalette != null
    val editorItem = state.editorTarget?.let { key -> state.items.firstOrNull { it.app.key == key } }

    // Back leaves selection mode first.
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        searching = false
        viewModel.setQuery("")
    }
    BackHandler(enabled = searching && !state.selecting && state.editorTarget == null) { closeSearch() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    if (state.selecting) {
                        TopAppBar(
                            title = { Text("${state.selected.size} selected") },
                            navigationIcon = {
                                TooltipIconButton(Icons.Filled.Close, "Cancel", viewModel::clearSelection)
                            },
                            actions = {
                                TooltipIconButton(Symbols.SelectAll, "Select all", viewModel::selectAll)
                                if (state.selectionHasEdits) {
                                    TooltipIconButton(Symbols.Reset, "Reset", viewModel::resetSelectedEdits)
                                }
                                TooltipIconButton(Icons.Filled.Edit, "Edit icon", viewModel::editSelection, enabled = state.canEditSelection)
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    } else if (searching) {
                        TopAppBar(
                            title = {
                                TextField(
                                    value = state.query,
                                    onValueChange = viewModel::setQuery,
                                    placeholder = { Text("Search apps") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                                )
                                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                            },
                            navigationIcon = { TooltipIconButton(Icons.Filled.Close, "Close search", ::closeSearch) },
                            actions = {
                                if (state.query.isNotEmpty()) TooltipIconButton(Symbols.Clear, "Clear", { viewModel.setQuery("") })
                            },
                        )
                    } else {
                        TopAppBar(
                            title = {
                                val editing = state.editing
                                if (editing == null) {
                                    Text("Create")
                                } else {
                                    Column {
                                        Text("Edit icon pack")
                                        Text(
                                            editing.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                TooltipIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                            },
                            actions = {
                                TooltipIconButton(Icons.Filled.Search, "Search", { searching = true })
                                TooltipIconButton(Icons.Filled.Refresh, "Refresh", viewModel::refresh, enabled = state.iconsReady)
                                Button(
                                    onClick = { exportOptions = viewModel.defaultExportOptions() },
                                    enabled = state.exportEnabled,
                                    modifier = Modifier.padding(end = 8.dp),
                                ) { Text(if (state.editing != null) "Update" else "Export") }
                            },
                        )
                    }
                    if (!state.iconsReady) {
                        LinearProgressIndicator(
                            progress = { state.fetchProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            bottomBar = {
                ControlPanel(
                    state = state,
                    onStyleChange = viewModel::setIconStyle,
                    onAccentChange = viewModel::setAccent,
                    onSourceChange = viewModel::setColorSource,
                    onSeedChange = viewModel::setSeed,
                    iconShape = iconShape,
                    onIconShape = onIconShape,
                    collapseRequests = collapseRequests,
                    expandRequests = expandRequests,
                )
            },
        ) { padding ->
            when {
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, viewModel::refresh, padding)
                state.items.isEmpty() && state.scanning -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { Text("No launcher apps found") }
                else -> AppGrid(
                    items = state.visibleItems,
                    colorsFor = { item -> if (!previewed) null else state.paletteFor(item.app.key)?.toIconColors() },
                    contrastFor = { item -> if (!previewed) 0 else state.edits[item.app.key]?.contrast ?: 0 },
                    edited = state.edits.keys,
                    header = GridHeader(
                        previewed = previewed,
                        filter = state.filter,
                        counts = mapOf(
                            GridFilter.ALL to state.total,
                            GridFilter.NATIVE to state.count(GlyphSource.NATIVE_MONO),
                            GridFilter.GENERATED to state.total - state.count(GlyphSource.NATIVE_MONO),
                            GridFilter.EDITED to state.editedCount,
                        ),
                        // Not iconsReady: the rescan on every open would hide and re-show the chips.
                        showCountsReady = state.total > 0 && state.loaded == state.total,
                        showDefaultPaletteBanner = state.paletteLooksDefault &&
                            state.pending.source == ColorSource.WALLPAPER &&
                            !state.defaultPaletteBannerDismissed,
                        onFilterChange = viewModel::setFilter,
                        onUseCustomColours = {
                            viewModel.setColorSource(ColorSource.CUSTOM)
                            expandRequests++
                        },
                        onDismissDefaultPaletteBanner = viewModel::dismissDefaultPaletteBanner,
                        emptyMessage = if (state.query.isNotBlank() && state.visibleItems.isEmpty()) "No apps match \"${state.query.trim()}\"" else null,
                    ),
                    contentPadding = padding,
                    selected = state.selected,
                    // Tapping an app edits its icon (once the icons are ready and coloured).
                    onItemClick = { item ->
                        when {
                            state.selecting -> viewModel.toggleSelection(item)
                            !previewed -> Toast.makeText(context, "Icons are still loading", Toast.LENGTH_SHORT).show()
                            item.glyph == null -> Toast.makeText(context, "No icon to edit yet", Toast.LENGTH_SHORT).show()
                            else -> viewModel.openEditor(item.app.key)
                        }
                    },
                    // Selecting only makes sense once the themed icons are shown.
                    onItemLongClick = { item ->
                        if (previewed) viewModel.onLongPress(item) else Toast.makeText(context, "Icons are still loading", Toast.LENGTH_SHORT).show()
                    },
                    // Scrolling the icons collapses the control panel.
                    onUserScroll = { collapseRequests++ },
                )
            }
        }

        // The icon editor covers the main screen, which keeps its state (scroll position, panel) underneath.
        AnimatedVisibility(
            visible = editorItem != null && state.committed != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            val item = editorItem ?: return@AnimatedVisibility
            val committed = state.committed ?: return@AnimatedVisibility
            IconEditorScreen(
                item = item,
                pairs = state.committedPairs,
                globalStyle = committed.style,
                edit = state.edits[item.app.key],
                references = state.items
                    .filter { it.glyph?.source == GlyphSource.NATIVE_MONO && it.app.key != item.app.key },
                loadGlyph = viewModel::loadEditorGlyph,
                onSave = { viewModel.saveEdit(item.app.key, it) },
                onReset = { viewModel.resetEdit(item.app.key) },
                onClose = viewModel::closeEditor,
            )
        }
    }

    // Exports run in the background with a progress notification; ask once to show it (optional).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val hasThemeManager = remember { ThemeApplier.isAvailable(context) }
    exportOptions?.let { defaults ->
        ExportOptionsSheet(
            defaults = defaults,
            onExport = {
                exportOptions = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.export(it)
            },
            onDismiss = { exportOptions = null },
            hasThemeManager = hasThemeManager,
            packNameWarning = packNameWarning,
        )
    }
    ExportDialogs(
        // Updating an edited pack installs it right away instead of showing the result.
        state = if (state.installWhenDone && state.export is ExportState.Done) ExportState.Idle else state.export,
        onCancel = viewModel::cancelExport,
        onDismiss = viewModel::dismissExport,
        onSaveCopy = viewModel::saveCopy,
        onApplied = viewModel::onThemeApplied,
        installPack = installPack,
        applyTheme = applyTheme,
        onHide = viewModel::hideExportDialog,
        dialogHidden = state.exportDialogHidden,
    )

}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load apps", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
