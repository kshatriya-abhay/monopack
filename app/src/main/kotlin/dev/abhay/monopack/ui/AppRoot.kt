package dev.abhay.monopack.ui

import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.data.BackupJson
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.ExportState
import dev.abhay.monopack.export.PackToInstall
import dev.abhay.monopack.hyperos.rememberApplyThemeFlow
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.library.InstallPermissionStep
import dev.abhay.monopack.library.LibraryScreen
import dev.abhay.monopack.library.LibraryViewModel
import dev.abhay.monopack.library.OnboardingScreen
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.render.IconShape
import dev.abhay.monopack.render.LocalIconShape
import dev.abhay.monopack.settings.SettingsScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Top-level destinations. */
private enum class Screen { LIBRARY, CREATE, SETTINGS }

/**
 * The app: onboarding until a library folder is chosen, then the library (home) and, from its
 * + Create button, the editor ([MainScreen]) and, from its gear, [SettingsScreen].
 */
@Composable
fun AppRoot(
    /** An icon pack to update (package, label), from the new-app notification; handled once. */
    updatePack: Pair<String, String?>? = null,
    onUpdatePackHandled: () -> Unit = {},
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
    val context = LocalContext.current
    val installer = context.appContainer.packInstaller
    val installPack = rememberInstallPackFlow(installer, onInstalled = library::refresh)
    // Re-checked on resume: the user may have just allowed installs in Settings.
    var canInstall by remember { mutableStateOf(installer.canInstall()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = installer.canInstall()
        if (canInstall && !state.installStepDone && !state.loading) library.finishInstallStep()
    }
    // Backup and restore (Settings): a JSON file the user picks.
    val scope = rememberCoroutineScope()
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val data = create.backup(state.iconShape.name)
        scope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BackupJson.encode(data, System.currentTimeMillis()).toByteArray()) }
                        ?: error("Couldn't write the file")
                }
            }
            val count = data.edits.size
            Toast.makeText(
                context,
                if (saved.isSuccess) "Backed up $count icon ${if (count == 1) "edit" else "edits"}" else "Couldn't save the backup",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val restored = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            }.getOrNull()?.let(BackupJson::decode)
            if (restored == null) {
                Toast.makeText(context, "That isn't a Monopack backup", Toast.LENGTH_SHORT).show()
                return@launch
            }
            create.restoreBackup(restored)
            restored.iconShape?.let { library.setIconShape(IconShape.fromName(it)) }
            val count = restored.edits.size
            Toast.makeText(context, "Restored $count icon ${if (count == 1) "edit" else "edits"}", Toast.LENGTH_SHORT).show()
        }
    }
    val applyTheme = rememberApplyThemeFlow(state.folderLabel, state.applyHintDismissed, library::dismissApplyHint)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { library.refresh() }
    // Back on the home screen from Create or Settings (not on launch: init and resume cover that).
    var previousScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        if (screen == Screen.LIBRARY && previousScreen != Screen.LIBRARY) library.refresh()
        previousScreen = screen
    }
    // The home screen comes first; Create's apps and icons load once it's showing.
    LaunchedEffect(state.loading) { if (!state.loading) create.preload() }

    /** Opens Edit icon pack for an installed pack (by package), or + Create if it isn't in the library. */
    fun editInstalledPack(packageName: String, label: String?) {
        val item = state.packItem(packageName)
        when {
            item != null -> create.editPack(PackToEdit(item.title, item.packStyles), item.selection)
            label != null -> create.editPack(PackToEdit(label, IconStyle.entries.toSet()), null)
            else -> create.stopEditing()
        }
        screen = Screen.CREATE
    }
    LaunchedEffect(updatePack, state.loading) {
        val (pkg, label) = updatePack ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        editInstalledPack(pkg, label)
        onUpdatePackHandled()
    }

    // Update in Edit icon pack installs the rebuilt pack as soon as it's saved.
    // Only these two fields: watching all of Create's state redrew the whole app while icons loaded.
    val updateResult by remember(create) {
        create.state.map { it.export to it.installWhenDone }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = ExportState.Idle to false)
    LaunchedEffect(updateResult) {
        val (export, installWhenDone) = updateResult
        val done = export as? ExportState.Done ?: return@LaunchedEffect
        if (!installWhenDone) return@LaunchedEffect
        val file = done.files.singleOrNull { it.kind == ExportKind.ICON_PACK }
        create.dismissExport()
        if (file != null) installPack(PackToInstall(file.uri, file.title, PackNaming.packageFor(file.title)))
    }

    CompositionLocalProvider(LocalIconShape provides state.iconShape.shape) {
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            state.needsFolder -> OnboardingScreen(
                lostAccess = state.tree != null,
                error = state.error,
                onFolderPicked = { library.onFolderPicked(it.toString()) },
            )
            !state.installStepDone && !canInstall -> InstallPermissionStep(
                onAllow = { runCatching { context.startActivity(installer.permissionIntent()) } },
                onSkip = library::finishInstallStep,
            )
            screen == Screen.SETTINGS -> {
                BackHandler { screen = Screen.LIBRARY }
                SettingsScreen(
                    applyHintDismissed = state.applyHintDismissed,
                    onChangeFolder = { changeFolder.launch(null) },
                    onOpenFolder = library::openFolder,
                    canInstall = canInstall,
                    onAllowInstalls = { runCatching { context.startActivity(installer.permissionIntent()) } },
                    onBackup = { backup.launch("Monopack-backup-${LocalDate.now()}.json") },
                    onRestore = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onResetApplyHint = library::resetApplyHint,
                    newAppAlerts = state.newAppAlerts,
                    onNewAppAlerts = library::setNewAppAlerts,
                    packs = state.packs,
                    watchedPack = state.effectiveWatchedPack,
                    onWatchPack = library::setWatchedPack,
                    onBack = { screen = Screen.LIBRARY },
                )
            }
            screen == Screen.CREATE -> {
                val leave = {
                    create.stopEditing()
                    screen = Screen.LIBRARY
                }
                BackHandler { leave() }
                MainScreen(
                    viewModel = create,
                    onBack = leave,
                    installPack = installPack,
                    applyTheme = applyTheme,
                    packNameWarning = library::packNameWarning,
                    iconShape = state.iconShape,
                    onIconShape = library::setIconShape,
                )
            }
            else -> LibraryScreen(
                state = state,
                onCreate = {
                    create.stopEditing()
                    screen = Screen.CREATE
                },
                onEditPack = { item ->
                    create.editPack(PackToEdit(item.title, item.packStyles), item.selection)
                    screen = Screen.CREATE
                },
                onToggle = library::toggleSelection,
                onClearSelection = library::clearSelection,
                onDeleteSelected = library::deleteSelected,
                onRemoveMissing = library::removeMissing,
                onApplied = library::onApplied,
                onSettings = { screen = Screen.SETTINGS },
                applyTheme = applyTheme,
                onDismissNewApps = library::dismissNewApps,
                onUpdateNewApps = { found -> editInstalledPack(found.packageName, found.packLabel) },
                installPack = installPack,
                onSelectAll = library::selectAll,
            )
        }
    }
    if (state.selecting && screen == Screen.LIBRARY) BackHandler { library.clearSelection() }
}
