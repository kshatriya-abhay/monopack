package dev.abhay.hypericon.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.abhay.hypericon.apps.AppSource
import dev.abhay.hypericon.appContainer
import dev.abhay.hypericon.data.SavedSelections
import dev.abhay.hypericon.data.SelectionStore
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.DefaultPalette
import dev.abhay.hypericon.palette.PaletteSource
import dev.abhay.hypericon.palette.Seed
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
import kotlin.math.ceil
import kotlin.math.roundToInt

fun IconStyle.opposite(): IconStyle = if (this == IconStyle.LIGHT) IconStyle.DARK else IconStyle.LIGHT

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
     * Colours of the committed selection in the *opposite* icon style, used for apps whose style
     * is flipped.
     */
    val committedFlipPalette: IconPalette? = null,
    /**
     * Apps (keys of [DrawerItem.app]) shown in the opposite icon style: Light icons for them in
     * Dark mode and vice versa. Kept in memory only for this session.
     */
    val flipped: Set<String> = emptySet(),
    /** Apps selected with long-press; non-empty means selection mode. */
    val selected: Set<String> = emptySet(),
) {
    val selecting: Boolean get() = selected.isNotEmpty()

    /** True if every selected app is already flipped (so the action restores them). */
    val selectionAllFlipped: Boolean get() = selecting && flipped.containsAll(selected)

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
    systemStyle: IconStyle,
    private val iconPx: Int,
    private val detailPx: Int,
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

    fun selectAll() = _state.update { s ->
        s.copy(selected = s.visibleItems.filter { it.glyph != null }.map { it.app.key }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /**
     * Flips the selected apps to the opposite icon style, or restores them if they're all flipped
     * already; ends selection mode. In memory only.
     */
    fun flipSelected() = _state.update {
        val flipped = if (it.selectionAllFlipped) it.flipped - it.selected else it.flipped + it.selected
        it.copy(flipped = flipped, selected = emptySet())
    }

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
                committedFlipPalette = it.activePalettes[it.pending.accent]?.get(it.pending.style.opposite()),
            )
        }
    }

    fun setFilter(filter: GridFilter) = _state.update { it.copy(filter = filter) }

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
        val flipPalette = committed?.let { c ->
            val source = if (c.source == ColorSource.CUSTOM) withContext(workDispatcher) { c.seed.palettes() } else _state.value.palettes
            source[c.accent]?.get(c.style.opposite())
        }
        _state.update {
            it.copy(
                pending = saved.pending,
                customPalettes = customPalettes,
                committed = committed,
                committedPalette = saved.committedPalette,
                committedFlipPalette = flipPalette,
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
        const val GRID_ICON_DP = 58f
        const val DETAIL_ICON_DP = 96f
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
                    systemStyle = if (night == Configuration.UI_MODE_NIGHT_YES) IconStyle.DARK else IconStyle.LIGHT,
                    iconPx = (GRID_ICON_DP * density).roundToInt(),
                    detailPx = (DETAIL_ICON_DP * density).roundToInt(),
                )
            }
        }
    }
}
