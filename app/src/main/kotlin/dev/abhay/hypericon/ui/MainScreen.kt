package dev.abhay.hypericon.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.abhay.hypericon.export.ThemeApplier
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.GlyphSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    /** The app whose details sheet is open. */
    var detailsItem by remember { mutableStateOf<DrawerItem?>(null) }
    var exportOptions by remember { mutableStateOf<ExportOptions?>(null) }
    var collapseRequests by remember { mutableIntStateOf(0) }
    var expandRequests by remember { mutableIntStateOf(0) }
    val previewed = state.committedPalette != null
    val editorItem = state.editorTarget?.let { key -> state.items.firstOrNull { it.app.key == key } }

    // Back leaves selection mode first.
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    if (state.selecting) {
                        TopAppBar(
                            title = { Text("${state.selected.size} selected") },
                            navigationIcon = { TextButton(onClick = viewModel::clearSelection) { Text("Cancel") } },
                            actions = {
                                if (state.selectionHasEdits) {
                                    TextButton(onClick = viewModel::resetSelectedEdits) { Text("Reset") }
                                }
                                TextButton(onClick = viewModel::editSelection, enabled = state.canEditSelection) { Text("Edit icon") }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    } else {
                        TopAppBar(
                            title = { Text("HyperIcon") },
                            actions = {
                                // HyperOS sometimes drops applied icons; this re-applies the last theme in one tap.
                                val lastTheme = state.lastTheme
                                if (lastTheme != null && state.lastThemeAvailable) {
                                    TextButton(onClick = {
                                        val applied = ThemeApplier.apply(context, lastTheme.absolutePath)
                                        val message = if (applied) "Reapplying ${lastTheme.title}" else "Theme Manager isn't available"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }) { Text("Reapply") }
                                }
                                TextButton(onClick = viewModel::refresh, enabled = state.iconsReady) { Text("Refresh") }
                                TextButton(onClick = { exportOptions = viewModel.defaultExportOptions() }, enabled = state.exportEnabled) {
                                    Text("Export")
                                }
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
                    onPreview = viewModel::preview,
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
                        showCountsReady = state.iconsReady,
                        showDefaultPaletteBanner = state.paletteLooksDefault &&
                            state.pending.source == ColorSource.WALLPAPER &&
                            !state.defaultPaletteBannerDismissed,
                        onFilterChange = viewModel::setFilter,
                        onUseCustomColours = {
                            viewModel.setColorSource(ColorSource.CUSTOM)
                            expandRequests++
                        },
                        onDismissDefaultPaletteBanner = viewModel::dismissDefaultPaletteBanner,
                    ),
                    contentPadding = padding,
                    selected = state.selected,
                    onItemClick = { item -> if (state.selecting) viewModel.toggleSelection(item) else detailsItem = item },
                    // Selecting only makes sense once the themed icons are shown.
                    onItemLongClick = { item -> if (previewed) viewModel.onLongPress(item) else detailsItem = item },
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
                    .filter { it.glyph?.source == GlyphSource.NATIVE_MONO && it.app.key != item.app.key }
                    .take(EDITOR_REFERENCE_COUNT),
                loadGlyph = viewModel::loadEditorGlyph,
                onSave = { viewModel.saveEdit(item.app.key, it) },
                onReset = { viewModel.resetEdit(item.app.key) },
                onClose = viewModel::closeEditor,
            )
        }
    }

    exportOptions?.let { defaults ->
        ExportOptionsSheet(
            defaults = defaults,
            onExport = {
                exportOptions = null
                viewModel.export(it)
            },
            onDismiss = { exportOptions = null },
        )
    }
    ExportDialogs(
        state = state.export,
        onCancel = viewModel::cancelExport,
        onDismiss = viewModel::dismissExport,
        onSaveCopy = viewModel::saveCopy,
        onApplied = viewModel::onThemeApplied,
    )

    detailsItem?.let { item ->
        AppDetailsSheet(
            item = item,
            colors = state.paletteFor(item.app.key)?.toIconColors(),
            edit = state.edits[item.app.key],
            onEdit = if (previewed && item.glyph != null) {
                {
                    detailsItem = null
                    viewModel.openEditor(item.app.key)
                }
            } else {
                null
            },
            loadDetails = viewModel::loadDetails,
            onDismiss = { detailsItem = null },
        )
    }
}

/** Native-monochrome apps shown in the editor's comparison row. */
private const val EDITOR_REFERENCE_COUNT = 4

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
