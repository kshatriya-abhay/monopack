package dev.abhay.hypericon.ui

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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.GlyphSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DrawerItem?>(null) }
    val committedColors = state.committedPalette?.let {
        remember(it) { IconColors(Color(it.background), Color(it.foreground)) }
    }
    val flipColors = state.committedFlipPalette?.let {
        remember(it) { IconColors(Color(it.background), Color(it.foreground)) }
    }

    // Back leaves selection mode first.
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        topBar = {
            Column {
                if (state.selecting) {
                    TopAppBar(
                        title = { Text("${state.selected.size} selected") },
                        navigationIcon = { TextButton(onClick = viewModel::clearSelection) { Text("Cancel") } },
                        actions = {
                            TextButton(onClick = viewModel::selectAll) { Text("All") }
                            TextButton(onClick = viewModel::flipSelected) {
                                Text(if (state.selectionAllFlipped) "Unflip" else "Flip light/dark")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                } else {
                    TopAppBar(
                        title = { Text("HyperIcon") },
                        actions = {
                            TextButton(onClick = viewModel::refresh, enabled = state.iconsReady) { Text("Refresh") }
                            TextButton(onClick = { viewModel.export() }, enabled = state.exportEnabled) { Text("Export") }
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
                colors = committedColors,
                header = GridHeader(
                    filter = state.filter,
                    counts = mapOf(
                        GridFilter.ALL to state.total,
                        GridFilter.NATIVE to state.count(GlyphSource.NATIVE_MONO),
                        GridFilter.GENERATED to state.total - state.count(GlyphSource.NATIVE_MONO),
                    ),
                    showCountsReady = state.iconsReady,
                    showDefaultPaletteBanner = state.paletteLooksDefault && state.pending.source == ColorSource.WALLPAPER,
                    onFilterChange = viewModel::setFilter,
                    onUseCustomColours = { viewModel.setColorSource(ColorSource.CUSTOM) },
                ),
                contentPadding = padding,
                flipColors = flipColors,
                flipped = state.flipped,
                selected = state.selected,
                onItemClick = { item -> if (state.selecting) viewModel.toggleSelection(item) else selected = item },
                // Selecting only makes sense once the themed icons are shown.
                onItemLongClick = { item -> if (committedColors != null) viewModel.onLongPress(item) else selected = item },
            )
        }
    }

    ExportDialogs(state.export, onCancel = viewModel::cancelExport, onDismiss = viewModel::dismissExport)

    selected?.let { item ->
        AppDetailsSheet(
            item = item,
            colors = if (item.app.key in state.flipped) flipColors ?: committedColors else committedColors,
            loadDetails = viewModel::loadDetails,
            onDismiss = { selected = null },
        )
    }
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
