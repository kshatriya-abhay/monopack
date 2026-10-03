package dev.abhay.monopack.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.data.LastTheme
import dev.abhay.monopack.data.SelectionStore
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.ExportRunner
import dev.abhay.monopack.export.ExportState
import dev.abhay.monopack.export.InstalledPack
import dev.abhay.monopack.export.PackInstalls
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.newapps.InstalledApp
import dev.abhay.monopack.newapps.NewAppJob
import dev.abhay.monopack.newapps.NewAppStore
import dev.abhay.monopack.newapps.WatchablePack
import dev.abhay.monopack.render.IconShape
import dev.abhay.monopack.util.catching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the export sheet should warn about for an icon-pack name. */
sealed interface PackNameWarning {
    /** A pack with this name is in the library (exported at [exportedAt]) or installed: this one replaces it. */
    data class Replaces(val exportedAt: Long?) : PackNameWarning

    /** A pack with this name is installed but signed with another key: Android won't replace it. */
    data object Conflicts : PackNameWarning
}

/** What went wrong with the library folder (the screens word it). */
sealed interface LibraryError {
    data class CantRead(val folder: String) : LibraryError

    data object CantKeepAccess : LibraryError
}

data class LibraryState(
    val loading: Boolean = true,
    /** The library folder (a SAF tree URI), or null before onboarding. */
    val tree: String? = null,
    val hasAccess: Boolean = false,
    val folderLabel: String = "",
    val items: List<LibraryItem> = emptyList(),
    /** Install state of packs, by file name. */
    val installed: Map<String, InstalledPack> = emptyMap(),
    val selected: Set<String> = emptySet(),
    val lastTheme: LastTheme? = null,
    val error: LibraryError? = null,
    /** The first-apply explanation was turned off. */
    val applyHintDismissed: Boolean = false,
    /** The preview icon shape (all previews; exports are unaffected). */
    val iconShape: IconShape = IconShape.DEFAULT,
    /** Notify about installed apps the current icon pack doesn't cover. */
    val newAppAlerts: Boolean = false,
    /** Apps installed after the watched icon pack that it doesn't cover (checked on open and resume). */
    val newApps: NewAppsFound? = null,
    /** Installed Monopack icon packs, to pick the watched one from. */
    val packs: List<WatchablePack> = emptyList(),
    /** The pack the user picked to watch (null: none picked; a single installed pack is used). */
    val watchedPack: String? = null,
    /** The onboarding's install-permission step was done or skipped. */
    val installStepDone: Boolean = false,
) {
    /** The pack new apps are checked against: the picked one if installed, else the only one. */
    val effectiveWatchedPack: WatchablePack?
        get() = packs.firstOrNull { it.packageName == watchedPack } ?: packs.singleOrNull()

    /** No folder yet, or its grant was lost: show onboarding. */
    val needsFolder: Boolean get() = !loading && (tree == null || !hasAccess)

    val selecting: Boolean get() = selected.isNotEmpty()

    /** The filesystem path Theme Manager needs, or null (missing file, or a folder off internal storage). */
    fun pathFor(item: LibraryItem): String? = item.file?.documentId?.let(Library::pathFor)

    /** The library's icon pack with [packageName] (recorded, or matched by its name). */
    fun packItem(packageName: String): LibraryItem? = items.firstOrNull {
        it.kind == ExportKind.ICON_PACK && (it.packageName ?: PackNaming.packageFor(it.title)) == packageName
    }

    fun isApplied(item: LibraryItem): Boolean = item.kind == ExportKind.THEME && lastTheme != null && pathFor(item) == lastTheme.absolutePath
}

/** The home screen's new-apps banner: [apps] aren't in the pack [packLabel] ([packageName]). */
data class NewAppsFound(val packLabel: String, val apps: List<InstalledApp>, val packageName: String = "")

/** The home screen: exported themes and packs in the library folder. */
class LibraryViewModel(
    private val store: LibraryStore,
    private val folder: LibraryFolder,
    private val selections: SelectionStore,
    private val packInstalls: PackInstalls,
    runner: ExportRunner? = null,
    private val newApps: NewAppStore? = null,
    /** Schedules (true) or cancels the periodic new-app check. */
    private val scheduleNewApps: (Boolean) -> Unit = {},
    /** Apps the watched pack doesn't cover (null without one). */
    private val findNewApps: suspend () -> NewAppsFound? = { null },
    /** Installed Monopack packs. */
    private val listPacks: suspend () -> List<WatchablePack> = { emptyList() },
    /** For folder, package and keystore lookups (tests pass their own). */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** New-app banners dismissed this session (by component), until other new apps turn up. */
    private var dismissedNewApps = emptySet<String>()

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        refresh()
        // A finished export adds an item (and may change the Reapply target).
        runner?.let { r -> viewModelScope.launch { r.state.collect { if (it is ExportState.Done) refresh() } } }
    }

    private var refreshJob: Job? = null

    /**
     * Re-reads the folder and the records (on open, on resume and after changes). A new call
     * replaces one still running. The list shows as soon as the folder is read; install states,
     * the installed packs and the new-app check follow, off the main thread.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val tree = catching { store.loadTree() }.getOrNull()
            val access = tree != null && withContext(io) { folder.hasAccess(tree) }
            val lastTheme = catching { selections.loadLastTheme() }.getOrNull()
            val hintDismissed = catching { store.loadApplyHintDismissed() }.getOrDefault(false)
            val shape = IconShape.fromName(catching { store.loadIconShape() }.getOrNull())
            val alerts = catching { newApps?.loadEnabled() }.getOrNull() ?: false
            val watched = catching { newApps?.loadWatchedPack() }.getOrNull()
            val installStep = catching { store.loadInstallStepDone() }.getOrDefault(true)
            // A newer refresh cancels this one; runCatching above turns that into defaults (no
            // folder), which mustn't be shown (it flashed onboarding).
            ensureActive()
            _state.update {
                it.copy(applyHintDismissed = hintDismissed, iconShape = shape, newAppAlerts = alerts, watchedPack = watched, installStepDone = installStep)
            }
            if (tree == null || !access) {
                _state.update { it.copy(loading = false, tree = tree, hasAccess = false, lastTheme = lastTheme, items = emptyList()) }
                return@launch
            }
            val records = catching { store.loadRecords() }.getOrDefault(emptyMap())
            val files = catching { folder.list(tree) }
            val items = Library.merge(records.values, files.getOrDefault(emptyList()))
            val label = catching { folder.label(tree) }.getOrDefault("")
            ensureActive()
            _state.update { s ->
                s.copy(
                    loading = false,
                    tree = tree,
                    hasAccess = true,
                    folderLabel = label,
                    items = items,
                    selected = s.selected.intersect(items.map { it.fileName }.toSet()),
                    lastTheme = lastTheme,
                    error = files.exceptionOrNull()?.let { LibraryError.CantRead(label) },
                )
            }
            refreshExtras(items)
        }
    }

    /** Install states, the installed Monopack packs and the new-app check: slower, so after the list. */
    private suspend fun refreshExtras(items: List<LibraryItem>) {
        val installed = withContext(io) {
            items.mapNotNull { item ->
                item.packageName?.takeIf { item.kind == ExportKind.ICON_PACK }?.let { pkg ->
                    item.fileName to catching { packInstalls.status(pkg) }.getOrDefault(InstalledPack.NOT_INSTALLED)
                }
            }.toMap()
        }
        val packs = catching { listPacks() }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(installed = installed, packs = packs) }
        val found = catching { findNewApps() }.onFailure { Log.w(TAG, "New-app check failed", it) }.getOrNull()
            ?.takeIf { f -> f.apps.isNotEmpty() && !dismissedNewApps.containsAll(f.apps.map { it.component }) }
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(newApps = found) }
    }

    /** The user picked (or created) the library folder. */
    fun onFolderPicked(treeUri: String) {
        viewModelScope.launch {
            val old = _state.value.tree
            catching { withContext(io) { folder.takeAccess(treeUri) } }.onFailure {
                Log.w(TAG, "Couldn't keep access to the folder", it)
                _state.update { s -> s.copy(error = LibraryError.CantKeepAccess) }
                return@launch
            }
            if (old != null && old != treeUri) withContext(io) { folder.releaseAccess(old) }
            store.saveTree(treeUri)
            refresh()
        }
    }

    fun toggleSelection(item: LibraryItem) = _state.update {
        it.copy(selected = if (item.fileName in it.selected) it.selected - item.fileName else it.selected + item.fileName)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectAll() = _state.update { s -> s.copy(selected = s.items.map { it.fileName }.toSet()) }

    /** Deletes the selected items' files from the folder and their records. */
    fun deleteSelected() {
        val s = _state.value
        val chosen = s.items.filter { it.fileName in s.selected }
        viewModelScope.launch {
            chosen.forEach { item -> item.file?.let { folder.delete(it.documentUri) } }
            catching { store.removeRecords(chosen.map { it.fileName }) }
            _state.update { it.copy(selected = emptySet()) }
            refresh()
        }
    }

    /** Forgets an item whose file is missing. */
    fun removeMissing(item: LibraryItem) {
        viewModelScope.launch {
            catching { store.removeRecords(listOf(item.fileName)) }
            refresh()
        }
    }

    /** Apply icons was started for [item]: it becomes the Reapply target. */
    fun onApplied(item: LibraryItem) {
        val path = _state.value.pathFor(item) ?: return
        val theme = LastTheme(item.title, item.style ?: dev.abhay.monopack.model.IconStyle.LIGHT, path)
        _state.update { it.copy(lastTheme = theme) }
        viewModelScope.launch { catching { selections.saveLastTheme(theme) } }
    }

    /**
     * Whether exporting an icon pack named [name] would replace a pack in the library (its file is
     * still there) or clash with an installed one signed by another key.
     */
    fun packNameWarning(name: String): PackNameWarning? {
        val normalized = name.trim().ifEmpty { "Monopack" }
        val packageName = PackNaming.packageFor(normalized)
        val existing = _state.value.items.firstOrNull { !it.missing && Library.isSamePack(it, normalized, packageName) }
        return when (catching { packInstalls.status(packageName) }.getOrDefault(InstalledPack.NOT_INSTALLED)) {
            InstalledPack.OTHER_SIGNER -> PackNameWarning.Conflicts
            InstalledPack.SAME_SIGNER -> PackNameWarning.Replaces(existing?.createdAt)
            InstalledPack.NOT_INSTALLED -> existing?.let { PackNameWarning.Replaces(it.createdAt) }
        }
    }

    fun setIconShape(shape: IconShape) {
        _state.update { it.copy(iconShape = shape) }
        viewModelScope.launch { catching { store.saveIconShape(shape.name) } }
    }

    /** "Don't show again" on the first-apply explanation. */
    fun dismissApplyHint() {
        _state.update { it.copy(applyHintDismissed = true) }
        viewModelScope.launch { catching { store.saveApplyHintDismissed() } }
    }

    /** Settings: show the HyperOS "pick the theme file" help again. */
    fun resetApplyHint() {
        _state.update { it.copy(applyHintDismissed = false) }
        viewModelScope.launch { catching { store.saveApplyHintDismissed(false) } }
    }

    /** Hides the new-apps banner until other new apps are installed. */
    fun dismissNewApps() {
        dismissedNewApps = dismissedNewApps + _state.value.newApps?.apps.orEmpty().map { it.component }
        _state.update { it.copy(newApps = null) }
    }

    /** Onboarding: the install-permission step was completed or skipped. */
    fun finishInstallStep() {
        _state.update { it.copy(installStepDone = true) }
        viewModelScope.launch { catching { store.saveInstallStepDone() } }
    }

    /** Settings: the icon pack to check new apps against. */
    fun setWatchedPack(packageName: String) {
        _state.update { it.copy(watchedPack = packageName) }
        viewModelScope.launch {
            catching { newApps?.saveWatchedPack(packageName) }
            refresh()
        }
    }

    /** Settings: turn the new-app notification on or off. */
    fun setNewAppAlerts(enabled: Boolean) {
        _state.update { it.copy(newAppAlerts = enabled) }
        viewModelScope.launch {
            catching { newApps?.saveEnabled(enabled) }
            catching { scheduleNewApps(enabled) }.onFailure { Log.w(TAG, "Couldn't schedule the new-app check", it) }
        }
    }

    /** Shows the folder in a file manager (Settings → Open folder). */
    fun openFolder(): Boolean = _state.value.tree?.let { folder.open(it) } ?: false

    companion object {
        private const val TAG = "Monopack"

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                val app = this[APPLICATION_KEY]!!
                LibraryViewModel(
                    container.libraryStore,
                    container.libraryFolder,
                    container.selectionStore,
                    container.packInstalls,
                    container.exportRunner,
                    container.newAppStore,
                    scheduleNewApps = { NewAppJob.sync(app, it) },
                    findNewApps = { container.newAppCheck.find()?.let { NewAppsFound(it.pack.label, it.uncovered, it.pack.packageName) } },
                    listPacks = { container.newAppCheck.packs() },
                )
            }
        }
    }
}
