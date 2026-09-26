package dev.abhay.hypericon.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abhay.hypericon.appContainer
import dev.abhay.hypericon.glyph.GlyphExtractor
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconInfo
import dev.abhay.hypericon.model.IconKind
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.Seed
import dev.abhay.hypericon.render.HyperOsIconShape
import kotlinx.coroutines.CancellationException
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

data class DrawerItem(
    val app: LauncherApp,
    /** Original icon at grid size, clipped to the HyperOS squircle; null until fetched. */
    val original: ImageBitmap? = null,
    val info: IconInfo? = null,
    /** Alpha-only glyph on the full adaptive layer; null until fetched. */
    val glyph: Glyph? = null,
) {
    val glyphImage: ImageBitmap? by lazy { glyph?.mask?.asImageBitmap() }
}

data class UiState(
    val scanning: Boolean = true,
    val items: List<DrawerItem> = emptyList(),
    val error: String? = null,
    /** The system's wallpaper-derived palettes. */
    val palettes: Map<Accent, Map<IconStyle, IconPalette>> = emptyMap(),
    /** Palettes generated from the pending custom seed. */
    val customPalettes: Map<Accent, Map<IconStyle, IconPalette>> = emptyMap(),
    /** What the controls show. */
    val pending: Selection = Selection(IconStyle.LIGHT, Accent.PRIMARY),
    /** What the grid shows; null until the first Preview. */
    val committed: Selection? = null,
    /** Colors captured when Preview was tapped, so later palette changes don't alter the grid. */
    val committedPalette: IconPalette? = null,
) {
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
}

/**
 * Preview is available once icons are ready and the pending selection resolves to colors
 * different from what the grid currently shows.
 */
fun isPreviewEnabled(iconsReady: Boolean, pending: IconPalette?, committed: IconPalette?): Boolean =
    iconsReady && pending != null && pending != committed

/** Images for the per-app details sheet. */
data class DetailImages(
    val fromResources: ImageBitmap?,
    val fromPackageManager: ImageBitmap?,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val repo = container.appRepository
    private val loader = container.iconLoader
    private val paletteProvider = container.paletteProvider
    private val density = application.resources.displayMetrics.density

    private val _state = MutableStateFlow(
        Selection(style = systemIconStyle(application), accent = Accent.PRIMARY).let { pending ->
            UiState(palettes = paletteProvider.load(), customPalettes = pending.seed.palettes(), pending = pending)
        },
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val loadDispatcher = Dispatchers.Default.limitedParallelism(4)
    private var fetchJob: Job? = null

    init {
        refresh()
    }

    fun setIconStyle(style: IconStyle) = _state.update { it.copy(pending = it.pending.copy(style = style)) }

    fun setAccent(accent: Accent) = _state.update { it.copy(pending = it.pending.copy(accent = accent)) }

    fun setColorSource(source: ColorSource) = _state.update { it.copy(pending = it.pending.copy(source = source)) }

    /** Picks a preset or custom seed (and switches the source to Custom). */
    fun setSeed(seed: Seed) = _state.update {
        it.copy(
            pending = it.pending.copy(source = ColorSource.CUSTOM, seed = seed),
            customPalettes = if (seed == it.pending.seed) it.customPalettes else seed.palettes(),
        )
    }

    fun preview() = _state.update {
        if (!it.previewEnabled) it else it.copy(committed = it.pending, committedPalette = it.pendingPalette)
    }

    /** Called on every resume: refreshes wallpaper colors and picks up app changes. */
    fun onResume() {
        _state.update { it.copy(palettes = paletteProvider.load()) }
        if (fetchJob?.isActive == true) return
        refresh()
    }

    fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        _state.update { it.copy(scanning = true, error = null) }
        val apps = try {
            repo.scan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, error = e.message ?: e.javaClass.simpleName) }
            return
        }

        // Keep already-processed icons for apps that haven't been updated since.
        val previous = _state.value.items.associateBy { it.app.key }
        val items = apps.map { app ->
            previous[app.key]
                ?.takeIf { it.glyph != null && it.app.lastUpdateTime == app.lastUpdateTime }
                ?.copy(app = app)
                ?: DrawerItem(app)
        }.toMutableList()
        _state.update { it.copy(scanning = false, items = items.toList()) }

        val todo = items.indices.filter { items[it].glyph == null }
        if (todo.isEmpty()) return

        val iconPx = (GRID_ICON_DP * density).roundToInt()
        val glyphPx = glyphSizeFor(iconPx)
        val started = SystemClock.uptimeMillis()
        var lastPublish = started
        var done = 0
        channelFlow {
            for (index in todo) {
                launch(loadDispatcher) { send(index to loadItem(items[index].app, iconPx, glyphPx)) }
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

    /** Loads the raw icon once and derives both the original thumbnail and the glyph from it. */
    private fun loadItem(app: LauncherApp, iconPx: Int, glyphPx: Int): DrawerItem {
        val icon = loader.load(app)
        val original = icon.drawable.renderOriginal(iconPx).asImageBitmap()
        val glyph = GlyphExtractor.extract(icon.drawable, glyphPx)
        return DrawerItem(app, original, icon.info(), glyph)
    }

    suspend fun loadDetails(app: LauncherApp): DetailImages = withContext(Dispatchers.Default) {
        val sizePx = (DETAIL_ICON_DP * density).roundToInt()
        DetailImages(
            fromResources = loader.loadFromResources(app)?.renderOriginal(sizePx)?.asImageBitmap(),
            fromPackageManager = loader.loadViaPackageManager(app)?.renderPlain(sizePx)?.asImageBitmap(),
        )
    }

    companion object {
        const val GRID_ICON_DP = 58f
        const val DETAIL_ICON_DP = 96f
        private const val PUBLISH_INTERVAL_MS = 80L
        private const val TAG = "HyperIcon"

        /** The glyph covers the full layer, 1.5× the visible icon; round up to a multiple of 4. */
        fun glyphSizeFor(iconPx: Int): Int = (ceil(iconPx * 1.5 / 4).toInt()) * 4

        private fun systemIconStyle(app: Application): IconStyle {
            val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) IconStyle.DARK else IconStyle.LIGHT
        }
    }
}

private fun Drawable.renderPlain(sizePx: Int): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap
}

/**
 * Adaptive icons are clipped to the HyperOS squircle (HyperOS's own `config_icon_mask` is a
 * square), so the grid never mixes shapes. Legacy icons keep their own shape.
 */
private fun Drawable.renderOriginal(sizePx: Int): Bitmap {
    if (this !is AdaptiveIconDrawable) return renderPlain(sizePx)
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    canvas.clipPath(HyperOsIconShape.path(sizePx.toFloat(), sizePx.toFloat()))
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}
