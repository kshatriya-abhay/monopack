package dev.abhay.monopack.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.abhay.monopack.library.LibraryScreen
import dev.abhay.monopack.library.LibraryViewModel
import dev.abhay.monopack.library.OnboardingScreen
import dev.abhay.monopack.hyperos.rememberApplyThemeFlow
import dev.abhay.monopack.render.LocalIconShape

/** Top-level destinations. */
private enum class Screen { LIBRARY, CREATE }

/**
 * The app: onboarding until a library folder is chosen, then the library (home) and, from its
 * + Create button, the editor ([MainScreen]).
 */
@Composable
fun AppRoot(
    library: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    // Created up front (activity-scoped), so apps and icons load while the home screen is showing
    // and + Create opens straight onto a ready grid.
    create: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by library.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    val changeFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { library.onFolderPicked(it.toString()) }
    }
    val applyTheme = rememberApplyThemeFlow(state.folderLabel, state.applyHintDismissed, library::dismissApplyHint)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { library.refresh() }
    LaunchedEffect(screen) { if (screen == Screen.LIBRARY) library.refresh() }

    CompositionLocalProvider(LocalIconShape provides state.iconShape.shape) {
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            state.needsFolder -> OnboardingScreen(
                lostAccess = state.tree != null,
                error = state.error,
                onFolderPicked = { library.onFolderPicked(it.toString()) },
            )
            screen == Screen.CREATE -> {
                BackHandler { screen = Screen.LIBRARY }
                MainScreen(viewModel = create, onBack = { screen = Screen.LIBRARY }, onOpenFolder = library::openFolder, applyTheme = applyTheme, packNameWarning = library::packNameWarning)
            }
            else -> LibraryScreen(
                state = state,
                onCreate = { screen = Screen.CREATE },
                onToggle = library::toggleSelection,
                onClearSelection = library::clearSelection,
                onDeleteSelected = library::deleteSelected,
                onRemoveMissing = library::removeMissing,
                onApplied = library::onApplied,
                onOpenFolder = library::openFolder,
                onChangeFolder = { changeFolder.launch(null) },
                onIconShape = library::setIconShape,
                applyTheme = applyTheme,
            )
        }
    }
    if (state.selecting && screen == Screen.LIBRARY) BackHandler { library.clearSelection() }
}
