package dev.abhay.hypericon.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.abhay.hypericon.appContainer
import dev.abhay.hypericon.apps.AppSource
import dev.abhay.hypericon.data.SavedSelections
import dev.abhay.hypericon.data.SelectionStore
import dev.abhay.hypericon.export.ExportApp
import dev.abhay.hypericon.export.ExportRequest
import dev.abhay.hypericon.export.ExportSaver
import dev.abhay.hypericon.export.ThemeExporter
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.mtz.MtzNaming
import dev.abhay.hypericon.palette.DefaultPalette
import dev.abhay.hypericon.palette.IconEdits
import dev.abhay.hypericon.palette.PaletteSource
import dev.abhay.hypericon.palette.Seed
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun IconStyle.opposite(): IconStyle = if (this == IconStyle.LIGHT) IconStyle.DARK else IconStyle.LIGHT

/** What the user chose in the export sheet. */
data class ExportOptions(
    /** Theme name without the style suffix, e.g. "HyperIcon · Primary". */
    val name: String,
    val styles: Set<IconStyle>,
    /** Include apps whose glyph was generated (off: only apps with their own monochrome icon). */
    val includeGenerated: Boolean = true,
)

/** One exported theme file. */
data class ExportedFile(
    val style: IconStyle,
    val fileName: String,
    /** Human-readable location, e.g. "Download/HyperIcon/…mtz". */
    val location: String,
    val uri: String,
    /** Filesystem path of the Downloads copy (what Theme Manager is given). */
    val absolutePath: String,
    /** The copy in the app's cache (source for "Save as…"). */
    val cachePath: String,
    val iconCount: Int,
)

/** Progress of a theme export. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val done: Int, val total: Int, val file: Int = 1, val files: Int = 1) : ExportState
    data class Done(val files: List<ExportedFile>) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Which apps the grid shows. */
enum class GridFilter { ALL, NATIVE, GENERATED }

data class UiState(
    val scanning: Boolean = true,
    val items: List<DrawerItem> = emptyList(),
    val error: String? = null,
    /** The system's wallpaper-derived palettes. */
    val palettes: Map<Accent, Map<IconStyle, IconPalette>> = emptyMap(),
    /** True when the system palettes are AOSP's framework defaults (no wallpaper colours). */
    val paletteLooksDefault: Boolean = false,
    /** The default-palette banner was dismissed (for this session). */
    val defaultPaletteBannerDismissed: Boolean = false,
    /** Palettes generated from the pending custom seed. */
    val customPalettes: Map<Accent, Map<IconStyle, IconPalette>> = emptyMap(),
    /** What the controls show. */
    val pending: Selection = Selection(IconStyle.LIGHT, Accent.PRIMARY),
    /** What the grid shows; null until the first Preview. */
    val committed: Selection? = null,
    /** Colors captured when Preview was tapped, so later palette changes don't alter the grid. */
    val committedPalette: IconPalette? = null,
    val filter: GridFilter = GridFilter.ALL,
    /**
     * Colours of the committed selection in the *other* icon style, so edited apps can use either
     * style's pair ([committedPairs]).
     */
    val committedOppositePalette: IconPalette? = null,
    /** Per-app icon edits (keys of [DrawerItem.app]); session only. */
    val edits: Map<String, IconEdit> = emptyMap(),
    /** The app whose icon is open in the icon editor, or null. */
    val editorTarget: String? = null,
    /** Apps selected with long-press; non-empty means selection mode. */
    val selected: Set<String> = emptySet(),
    val export: ExportState = ExportState.Idle,
) {
    /** Export needs a preview (so what you export is what you saw) and all icons loaded. */
    val exportEnabled: Boolean get() = committed != null && committedPalette != null && iconsReady && export !is ExportState.Running

    val selecting: Boolean get() = selected.isNotEmpty()

    /** The icon editor works on one app at a time. */
    val canEditSelection: Boolean get() = selected.size == 1 && committedPalette != null

    /** Some selected app has an edit (so Reset is offered). */
    val selectionHasEdits: Boolean get() = selected.any { it in edits }

    /** The committed selection's plate/glyph pair for each icon style (empty before a Preview). */
    val committedPairs: Map<IconStyle, IconPalette>
        get() {
            val style = committed?.style ?: return emptyMap()
            val shown = committedPalette ?: return emptyMap()
            return mapOf(style to shown, style.opposite() to (committedOppositePalette ?: shown))
        }

    /** The colours an app is drawn with (its edit applied), or null before a Preview. */
    fun paletteFor(key: String): IconPalette? =
        committed?.let { IconEdits.resolve(committedPairs, it.style, edits[key]) }

    val total: Int get() = items.size
    val loaded: Int get() = items.count { it.glyph != null }
    val fetchProgress: Float get() = if (total == 0) (if (scanning) 0f else 1f) else loaded.toFloat() / total
    val iconsReady: Boolean get() = !scanning && loaded == total

    /** The palettes the controls currently draw from (wallpaper or custom seed). */
    val activePalettes: Map<Accent, Map<IconStyle, IconPalette>>
        get() = if (pending.source == ColorSource.CUSTOM) customPalettes else palettes
    val pendingPalette: IconPalette? get() = activePalettes[pending.accent]?.get(pending.style)
    val previewEnabled: Boolean get() = isPreviewEnabled(iconsReady, pendingPalette, committedPalette)
    fun count(source: GlyphSource) = items.count { it.glyph?.source == source }

    val visibleItems: List<DrawerItem>
        get() = when (filter) {
            GridFilter.ALL -> items
            GridFilter.NATIVE -> items.filter { it.glyph?.source == GlyphSource.NATIVE_MONO }
            GridFilter.GENERATED -> items.filter { it.glyph != null && it.glyph.source != GlyphSource.NATIVE_MONO }
        }
}

/**
 * Preview is available once icons are ready and the pending selection resolves to colors
 * different from what the grid currently shows.
 */
fun isPreviewEnabled(iconsReady: Boolean, pending: IconPalette?, committed: IconPalette?): Boolean =
    iconsReady && pending != null && pending != committed

class MainViewModel(
    private val apps: AppSource,
    private val loader: ItemLoader,
    private val palettes: PaletteSource,
    private val store: SelectionStore,
    private val exporter: ThemeExporter,
    private val saver: ExportSaver,
    systemStyle: IconStyle,
    private val iconPx: Int,
    private val detailPx: Int,
    /** Size of the editor's large preview in pixels. */
    private val editorIconPx: Int = detailPx,
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(4),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(
        Selection(style = systemStyle, accent = Accent.PRIMARY).let { pending ->
            val system = palettes.load()
            UiState(
                palettes = system,
                paletteLooksDefault = DefaultPalette.looksDefault(system),
                customPalettes = pending.seed.palettes(),
                pending = pending,
            )
        },
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var fetchJob: Job? = null

    /** Set once the saved selection has been applied (or found missing), or the user changed something. */
    private var selectionSettled = false

    init {
        viewModelScope.launch { restore() }
        refresh()
    }

    /** Long-press: starts selection mode, or toggles the app if already selecting. */
    fun onLongPress(item: DrawerItem) = toggleSelection(item)

    /** Adds or removes an app from the selection. */
    fun toggleSelection(item: DrawerItem) {
        if (item.glyph == null) return
        _state.update {
            val key = item.app.key
            it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Opens the icon editor for [key] (needs a Preview, so the committed palettes exist). */
    fun openEditor(key: String) = _state.update {
        if (it.committedPalette == null || it.items.none { item -> item.app.key == key && item.glyph != null }) {
            it
        } else {
            it.copy(editorTarget = key, selected = emptySet())
        }
    }

    /** Opens the editor for the single selected app. */
    fun editSelection() {
        val s = _state.value
        if (s.canEditSelection) openEditor(s.selected.single())
    }

    fun closeEditor() = _state.update { it.copy(editorTarget = null) }

    /** Saves [edit] for [key] (session only) and closes the editor. */
    fun saveEdit(key: String, edit: IconEdit) = _state.update {
        it.copy(edits = it.edits + (key to edit), editorTarget = null)
    }

    /** Removes the edit for [key], so it follows the global icon style again, and closes the editor. */
    fun resetEdit(key: String) = _state.update { it.copy(edits = it.edits - key, editorTarget = null) }

    /** Removes the edits of every selected app and leaves selection mode. */
    fun resetSelectedEdits() = _state.update { it.copy(edits = it.edits - it.selected, selected = emptySet()) }

    /** A glyph for the editor's large preview, sharper than the grid's (null if it fails). */
    suspend fun loadEditorGlyph(app: LauncherApp): ImageBitmap? =
        withContext(workDispatcher) { runCatching { loader.glyph(app, glyphSizeFor(editorIconPx)) }.getOrNull() }

    fun setIconStyle(style: IconStyle) = edit { it.copy(pending = it.pending.copy(style = style)) }

    fun setAccent(accent: Accent) = edit { it.copy(pending = it.pending.copy(accent = accent)) }

    fun setColorSource(source: ColorSource) = edit { it.copy(pending = it.pending.copy(source = source)) }

    /** Picks a preset or custom seed (and switches the source to Custom). */
    fun setSeed(seed: Seed) = edit {
        it.copy(
            pending = it.pending.copy(source = ColorSource.CUSTOM, seed = seed),
            customPalettes = if (seed == it.pending.seed) it.customPalettes else seed.palettes(),
        )
    }

    fun preview() = edit {
        if (!it.previewEnabled) {
            it
        } else {
            it.copy(
                committed = it.pending,
                committedPalette = it.pendingPalette,
                committedOppositePalette = it.activePalettes[it.pending.accent]?.get(it.pending.style.opposite()),
            )
        }
    }

    fun setFilter(filter: GridFilter) = _state.update { it.copy(filter = filter) }

    fun dismissDefaultPaletteBanner() = _state.update { it.copy(defaultPaletteBannerDismissed = true) }

    private var exportJob: Job? = null

    /** Default export options for the export sheet, from the previewed selection. */
    fun defaultExportOptions(): ExportOptions? {
        val committed = _state.value.committed ?: return null
        return ExportOptions(name = exportName(committed), styles = setOf(committed.style))
    }

    /**
     * Exports the previewed icons as one `.mtz` per chosen icon style (every launcher entry,
     * icon edits applied) and saves them to Downloads.
     */
    fun export(options: ExportOptions, now: LocalDateTime = LocalDateTime.now()) {
        val s = _state.value
        if (!s.exportEnabled || options.styles.isEmpty()) return
        val committed = s.committed ?: return
        if (s.committedPalette == null) return
        val pairs = s.committedPairs
        val entries = s.items.filter {
            it.glyph != null && it.glyph.source != GlyphSource.FAILED &&
                (options.includeGenerated || it.glyph.source == GlyphSource.NATIVE_MONO)
        }
        val styles = IconStyle.entries.filter { it in options.styles }
        val requests = styles.map { style ->
            val title = titleFor(options.name, style)
            style to ExportRequest(
                title = title,
                description = "Monochrome icons generated on-device by HyperIcon (${entries.size} apps).",
                fileName = fileNameFor(title, now),
                apps = entries.map {
                    ExportApp(
                        app = it.app,
                        palette = IconEdits.resolve(pairs, style, s.edits[it.app.key]) ?: pairs.getValue(style),
                        contrast = s.edits[it.app.key]?.contrast ?: 0,
                        folders = MtzNaming.folders(it.app.packageName, it.app.component.className, it.app.isMainActivity),
                    )
                },
                darkPreview = style == IconStyle.DARK,
            )
        }
        _state.update { it.copy(export = ExportState.Running(0, entries.size, 1, requests.size)) }
        exportJob = viewModelScope.launch {
            try {
                exporter.clearCache()
                val files = requests.mapIndexed { index, (style, request) ->
                    val file = exporter.export(request) { done, total ->
                        _state.update { it.copy(export = ExportState.Running(done, total, index + 1, requests.size)) }
                    }
                    val saved = saver.save(file)
                    ExportedFile(style, file.name, saved.displayPath, saved.uri, saved.absolutePath, file.path, request.apps.size)
                }
                _state.update { it.copy(export = ExportState.Done(files)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(export = ExportState.Idle) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                _state.update { it.copy(export = ExportState.Failed(e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    /** Copies an exported file to a document the user picked ("Save as…"). */
    fun saveCopy(file: ExportedFile, uri: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { saver.copyTo(File(file.cachePath), uri) }
                .onFailure { Log.w(TAG, "Save as failed", it) }
                .isSuccess
            onResult(ok)
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _state.update { it.copy(export = ExportState.Idle) }
    }

    fun dismissExport() = _state.update { it.copy(export = ExportState.Idle) }

    /** Called on every resume: refreshes wallpaper colors and picks up app changes. */
    fun onResume() {
        val system = palettes.load()
        _state.update { it.copy(palettes = system, paletteLooksDefault = DefaultPalette.looksDefault(system)) }
        if (fetchJob?.isActive == true) return
        refresh()
    }

    fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch { fetch() }
    }

    suspend fun loadDetails(app: LauncherApp): DetailImages = withContext(workDispatcher) {
        loader.details(app, detailPx)
    }

    /** Applies a selection change and saves it. */
    private fun edit(change: (UiState) -> UiState) {
        selectionSettled = true
        _state.update(change)
        val s = _state.value
        viewModelScope.launch { store.save(SavedSelections(s.pending, s.committed, s.committedPalette)) }
    }

    private suspend fun restore() {
        val saved = runCatching { store.load() }.getOrNull()
        if (selectionSettled || saved == null) {
            selectionSettled = true
            return
        }
        selectionSettled = true
        val customPalettes = withContext(workDispatcher) { saved.pending.seed.palettes() }
        val committed = saved.committed
        val oppositePalette = committed?.let { c ->
            val source = if (c.source == ColorSource.CUSTOM) withContext(workDispatcher) { c.seed.palettes() } else _state.value.palettes
            source[c.accent]?.get(c.style.opposite())
        }
        _state.update {
            it.copy(
                pending = saved.pending,
                customPalettes = customPalettes,
                committed = committed,
                committedPalette = saved.committedPalette,
                committedOppositePalette = oppositePalette,
            )
        }
    }

    private suspend fun fetch() {
        _state.update { it.copy(scanning = true, error = null) }
        val scanned = try {
            apps.scan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, error = e.message ?: e.javaClass.simpleName) }
            return
        }

        // Keep already-processed icons for apps that haven't been updated since.
        val previous = _state.value.items.associateBy { it.app.key }
        val items = scanned.map { app ->
            previous[app.key]
                ?.takeIf { it.glyph != null && it.app.lastUpdateTime == app.lastUpdateTime }
                ?.copy(app = app)
                ?: DrawerItem(app)
        }.toMutableList()
        _state.update { it.copy(scanning = false, items = items.toList()) }

        val todo = items.indices.filter { items[it].glyph == null }
        if (todo.isEmpty()) return

        val glyphPx = glyphSizeFor(iconPx)
        val started = SystemClock.uptimeMillis()
        var lastPublish = started
        var done = 0
        channelFlow {
            for (index in todo) {
                launch(loadDispatcher) { send(index to loader.load(items[index].app, iconPx, glyphPx)) }
            }
        }.collect { (index, item) ->
            items[index] = item
            done++
            val now = SystemClock.uptimeMillis()
            if (done == todo.size || now - lastPublish >= PUBLISH_INTERVAL_MS) {
                lastPublish = now
                _state.update { it.copy(items = items.toList()) }
            }
        }
        Log.i(TAG, "Prepared ${todo.size} icons (${glyphPx}px glyphs) in ${SystemClock.uptimeMillis() - started} ms")
    }

    companion object {
        /** "HyperIcon · Blue · Primary" (the seed name only for custom colours). */
        fun exportName(selection: Selection): String = buildList {
            add("HyperIcon")
            if (selection.source == ColorSource.CUSTOM) add(selection.seed.name)
            add(selection.accent.displayName)
        }.joinToString(" · ")

        /** "HyperIcon · Blue · Primary · Dark". */
        fun exportTitle(selection: Selection): String = titleFor(exportName(selection), selection.style)

        private fun titleFor(name: String, style: IconStyle) = "${name.trim().ifEmpty { "HyperIcon" }} · ${style.displayName}"

        /** "HyperIcon-Blue-Primary-Dark-20260927-1015.mtz" (letters and digits of each part). */
        fun fileNameFor(title: String, now: LocalDateTime): String {
            val parts = title.split("·").map { part -> part.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
            val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            return (parts.ifEmpty { listOf("HyperIcon") } + stamp).joinToString("-") + ".mtz"
        }

        private val IconStyle.displayName get() = if (this == IconStyle.DARK) "Dark" else "Light"

        private val Accent.displayName get() = name.lowercase().replaceFirstChar { it.uppercase() }

        const val GRID_ICON_DP = 58f
        const val DETAIL_ICON_DP = 96f
        const val EDITOR_ICON_DP = 112f
        private const val PUBLISH_INTERVAL_MS = 80L
        private const val TAG = "HyperIcon"

        /** The glyph covers the full layer, 1.5× the visible icon; round up to a multiple of 4. */
        fun glyphSizeFor(iconPx: Int): Int = (ceil(iconPx * 1.5 / 4).toInt()) * 4

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val container = app.appContainer
                val density = app.resources.displayMetrics.density
                val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                MainViewModel(
                    apps = container.appRepository,
                    loader = container.itemLoader,
                    palettes = container.paletteProvider,
                    store = container.selectionStore,
                    exporter = container.exporter,
                    saver = container.exportSaver,
                    systemStyle = if (night == Configuration.UI_MODE_NIGHT_YES) IconStyle.DARK else IconStyle.LIGHT,
                    iconPx = (GRID_ICON_DP * density).roundToInt(),
                    detailPx = (DETAIL_ICON_DP * density).roundToInt(),
                    editorIconPx = (EDITOR_ICON_DP * density).roundToInt(),
                )
            }
        }
    }
}
