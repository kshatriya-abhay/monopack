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
import dev.abhay.monopack.render.IconShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the export sheet should warn about for an icon-pack name. */
sealed interface PackNameWarning {
    /** A pack with this name is in the library (exported at [exportedAt]) or installed: this one replaces it. */
    data class Replaces(val exportedAt: Long?) : PackNameWarning

    /** A pack with this name is installed but signed with another key: Android won't replace it. */
    data object Conflicts : PackNameWarning
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
    val error: String? = null,
    /** The first-apply explanation was turned off. */
    val applyHintDismissed: Boolean = false,
    /** The preview icon shape (all previews; exports are unaffected). */
    val iconShape: IconShape = IconShape.DEFAULT,
) {
    /** No folder yet, or its grant was lost: show onboarding. */
    val needsFolder: Boolean get() = !loading && (tree == null || !hasAccess)

    val selecting: Boolean get() = selected.isNotEmpty()

    /** The filesystem path Theme Manager needs, or null (missing file, or a folder off internal storage). */
    fun pathFor(item: LibraryItem): String? = item.file?.documentId?.let(Library::pathFor)

    fun isApplied(item: LibraryItem): Boolean = item.kind == ExportKind.THEME && lastTheme != null && pathFor(item) == lastTheme.absolutePath
}

/** The home screen: exported themes and packs in the library folder. */
class LibraryViewModel(
    private val store: LibraryStore,
    private val folder: LibraryFolder,
    private val selections: SelectionStore,
    private val packInstalls: PackInstalls,
    runner: ExportRunner? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        refresh()
        // A finished export adds an item (and may change the Reapply target).
        runner?.let { r -> viewModelScope.launch { r.state.collect { if (it is ExportState.Done) refresh() } } }
    }

    /** Re-reads the folder and the records (on open, on resume and after changes). */
    fun refresh() {
        viewModelScope.launch {
            val tree = runCatching { store.loadTree() }.getOrNull()
            val access = tree != null && folder.hasAccess(tree)
            val lastTheme = runCatching { selections.loadLastTheme() }.getOrNull()
            val hintDismissed = runCatching { store.loadApplyHintDismissed() }.getOrDefault(false)
            val shape = IconShape.fromName(runCatching { store.loadIconShape() }.getOrNull())
            _state.update { it.copy(applyHintDismissed = hintDismissed, iconShape = shape) }
            if (tree == null || !access) {
                _state.update { it.copy(loading = false, tree = tree, hasAccess = false, lastTheme = lastTheme, items = emptyList()) }
                return@launch
            }
            val records = runCatching { store.loadRecords() }.getOrDefault(emptyMap())
            val files = runCatching { folder.list(tree) }
            val items = Library.merge(records.values, files.getOrDefault(emptyList()))
            val installed = items.mapNotNull { item ->
                item.packageName?.takeIf { item.kind == ExportKind.ICON_PACK }?.let { pkg ->
                    item.fileName to runCatching { packInstalls.status(pkg) }.getOrDefault(InstalledPack.NOT_INSTALLED)
                }
            }.toMap()
            _state.update { s ->
                s.copy(
                    loading = false,
                    tree = tree,
                    hasAccess = true,
                    folderLabel = runCatching { folder.label(tree) }.getOrDefault(""),
                    items = items,
                    installed = installed,
                    selected = s.selected.intersect(items.map { it.fileName }.toSet()),
                    lastTheme = lastTheme,
                    error = files.exceptionOrNull()?.let { "Can't read ${folder.label(tree)}" },
                )
            }
        }
    }

    /** The user picked (or created) the library folder. */
    fun onFolderPicked(treeUri: String) {
        viewModelScope.launch {
            val old = _state.value.tree
            runCatching { folder.takeAccess(treeUri) }.onFailure {
                Log.w(TAG, "Couldn't keep access to the folder", it)
                _state.update { s -> s.copy(error = "Couldn't keep access to that folder") }
                return@launch
            }
            if (old != null && old != treeUri) folder.releaseAccess(old)
            store.saveTree(treeUri)
            refresh()
        }
    }

    fun toggleSelection(item: LibraryItem) = _state.update {
        it.copy(selected = if (item.fileName in it.selected) it.selected - item.fileName else it.selected + item.fileName)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Deletes the selected items' files from the folder and their records. */
    fun deleteSelected() {
        val s = _state.value
        val chosen = s.items.filter { it.fileName in s.selected }
        viewModelScope.launch {
            chosen.forEach { item -> item.file?.let { folder.delete(it.documentUri) } }
            runCatching { store.removeRecords(chosen.map { it.fileName }) }
            _state.update { it.copy(selected = emptySet()) }
            refresh()
        }
    }

    /** Forgets an item whose file is missing. */
    fun removeMissing(item: LibraryItem) {
        viewModelScope.launch {
            runCatching { store.removeRecords(listOf(item.fileName)) }
            refresh()
        }
    }

    /** Apply icons was started for [item]: it becomes the Reapply target. */
    fun onApplied(item: LibraryItem) {
        val path = _state.value.pathFor(item) ?: return
        val theme = LastTheme(item.title, item.style ?: dev.abhay.monopack.model.IconStyle.LIGHT, path)
        _state.update { it.copy(lastTheme = theme) }
        viewModelScope.launch { runCatching { selections.saveLastTheme(theme) } }
    }

    /**
     * Whether exporting an icon pack named [name] would replace a pack in the library (its file is
     * still there) or clash with an installed one signed by another key.
     */
    fun packNameWarning(name: String): PackNameWarning? {
        val normalized = name.trim().ifEmpty { "Monopack" }
        val packageName = PackNaming.packageFor(normalized)
        val existing = _state.value.items.firstOrNull { !it.missing && Library.isSamePack(it, normalized, packageName) }
        return when (runCatching { packInstalls.status(packageName) }.getOrDefault(InstalledPack.NOT_INSTALLED)) {
            InstalledPack.OTHER_SIGNER -> PackNameWarning.Conflicts
            InstalledPack.SAME_SIGNER -> PackNameWarning.Replaces(existing?.createdAt)
            InstalledPack.NOT_INSTALLED -> existing?.let { PackNameWarning.Replaces(it.createdAt) }
        }
    }

    fun setIconShape(shape: IconShape) {
        _state.update { it.copy(iconShape = shape) }
        viewModelScope.launch { runCatching { store.saveIconShape(shape.name) } }
    }

    /** "Don't show again" on the first-apply explanation. */
    fun dismissApplyHint() {
        _state.update { it.copy(applyHintDismissed = true) }
        viewModelScope.launch { runCatching { store.saveApplyHintDismissed() } }
    }

    /** Shows the folder in a file manager (to install a pack). */
    fun openFolder(): Boolean = _state.value.tree?.let { folder.open(it) } ?: false

    companion object {
        private const val TAG = "Monopack"

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                LibraryViewModel(container.libraryStore, container.libraryFolder, container.selectionStore, container.packInstalls, container.exportRunner)
            }
        }
    }
}
