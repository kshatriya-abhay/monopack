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
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DrawerItem?>(null) }
    val committedColors = state.committedPalette?.let {
        remember(it) { IconColors(Color(it.background), Color(it.foreground)) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("HyperIcon") },
                    actions = {
                        TextButton(onClick = viewModel::refresh, enabled = state.iconsReady) { Text("Refresh") }
                    },
                )
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
                items = state.items,
                colors = committedColors,
                contentPadding = padding,
                onItemClick = { selected = it },
            )
        }
    }

    selected?.let { item ->
        AppDetailsSheet(
            item = item,
            colors = committedColors,
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
