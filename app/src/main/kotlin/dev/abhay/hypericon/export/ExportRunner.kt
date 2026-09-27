package dev.abhay.hypericon.export

import android.util.Log
import dev.abhay.hypericon.data.LastTheme
import dev.abhay.hypericon.data.PackRecord
import dev.abhay.hypericon.data.SelectionStore
import dev.abhay.hypericon.iconpack.PackNaming
import dev.abhay.hypericon.library.ExportRecord
import dev.abhay.hypericon.library.LibraryStore
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What an export produces. */
enum class ExportKind { THEME, ICON_PACK }

/** One exported file. */
data class ExportedFile(
    val kind: ExportKind,
    /** The theme's icon style; null for an icon pack (it has both). */
    val style: IconStyle?,
    /** Theme or pack title, e.g. "HyperIcon · Primary · Light". */
    val title: String,
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

/** Progress of an export. */
sealed interface ExportState {
    data object Idle : ExportState

    data class Running(
        val kind: ExportKind,
        val done: Int,
        val total: Int,
        val file: Int = 1,
        val files: Int = 1,
        /** Building and signing the pack (after all icons are rendered). */
        val signing: Boolean = false,
    ) : ExportState

    data class Done(
        val files: List<ExportedFile>,
        /** The theme Reapply should use now (theme exports only). */
        val lastTheme: LastTheme? = null,
    ) : ExportState

    data class Failed(val kind: ExportKind, val message: String) : ExportState
}

/** Work for the [ExportRunner]. */
sealed interface ExportJob {
    val kind: ExportKind

    /**
     * One `.mtz` per style; [preferredStyle]'s file becomes the Reapply target. [pairs] are the
     * committed colours per style (for the library thumbnail).
     */
    data class Themes(
        val requests: List<Pair<IconStyle, ExportRequest>>,
        val preferredStyle: IconStyle,
        val pairs: Map<IconStyle, IconPalette> = emptyMap(),
    ) : ExportJob {
        override val kind get() = ExportKind.THEME
    }

    data class Pack(val request: PackRequest) : ExportJob {
        override val kind get() = ExportKind.ICON_PACK
    }
}

/** Keeps the process alive while an export runs (a foreground service with a notification). */
fun interface BackgroundWork {
    fun started()
}

/**
 * Runs exports in the app's scope, so they finish even if the screen goes away, and publishes
 * their progress. [BackgroundWork] keeps the process alive meanwhile.
 */
class ExportRunner(
    private val scope: CoroutineScope,
    private val themeExporter: ThemeExporter,
    private val packExporter: PackExporter,
    private val saver: ExportSaver,
    private val store: SelectionStore,
    private val library: LibraryStore,
    private val background: BackgroundWork,
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts [work] unless an export is already running. */
    fun start(work: ExportJob): Boolean {
        if (_state.value is ExportState.Running) return false
        _state.value = when (work) {
            is ExportJob.Themes -> ExportState.Running(work.kind, 0, work.requests.first().second.apps.size, 1, work.requests.size)
            is ExportJob.Pack -> ExportState.Running(work.kind, 0, work.request.apps.size)
        }
        background.started()
        job = scope.launch {
            try {
                themeExporter.clearCache()
                _state.value = when (work) {
                    is ExportJob.Themes -> runThemes(work)
                    is ExportJob.Pack -> runPack(work)
                }
            } catch (e: CancellationException) {
                _state.value = ExportState.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                _state.value = ExportState.Failed(work.kind, e.message ?: e.javaClass.simpleName)
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        _state.value = ExportState.Idle
    }

    /** Clears a finished or failed export (the result sheet was closed). */
    fun dismiss() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }

    private suspend fun runThemes(work: ExportJob.Themes): ExportState.Done {
        val files = work.requests.mapIndexed { index, (style, request) ->
            val file = themeExporter.export(request) { done, total ->
                _state.value = ExportState.Running(work.kind, done, total, index + 1, work.requests.size)
            }
            val saved = saver.save(file)
            val pair = work.pairs[style]
            record(ExportRecord(saved.name, ExportKind.THEME, request.title, style, System.currentTimeMillis(), request.apps.size, pair?.background, pair?.foreground))
            ExportedFile(ExportKind.THEME, style, request.title, saved.name, saved.displayPath, saved.uri, saved.absolutePath, file.path, request.apps.size)
        }
        // Until one is applied, Reapply uses the new export (the preferred style's file for Both).
        val newest = files.firstOrNull { it.style == work.preferredStyle } ?: files.first()
        val lastTheme = LastTheme(newest.title, newest.style ?: work.preferredStyle, newest.absolutePath)
        runCatching { store.saveLastTheme(lastTheme) }.onFailure { Log.w(TAG, "Saving the last theme failed", it) }
        return ExportState.Done(files, lastTheme)
    }

    private suspend fun runPack(work: ExportJob.Pack): ExportState.Done {
        val request = work.request
        val file: File = packExporter.export(
            request,
            onProgress = { done, total -> _state.value = ExportState.Running(work.kind, done, total) },
            onSigning = { _state.update { (it as? ExportState.Running)?.copy(signing = true) ?: it } },
        )
        val saved = saver.save(file)
        val record = PackRecord(request.name, request.packageName, System.currentTimeMillis())
        runCatching { store.savePackRecord(PackNaming.normalize(request.name), record) }
            .onFailure { Log.w(TAG, "Saving the pack history failed", it) }
        record(
            ExportRecord(
                saved.name, ExportKind.ICON_PACK, request.name, request.style, System.currentTimeMillis(), request.apps.size,
                request.iconPalette.background, request.iconPalette.foreground, request.packageName, request.versionCode,
            ),
        )
        val exported = ExportedFile(ExportKind.ICON_PACK, request.style, request.name, saved.name, saved.displayPath, saved.uri, saved.absolutePath, file.path, request.apps.size)
        return ExportState.Done(listOf(exported))
    }

    private suspend fun record(record: ExportRecord) {
        runCatching { library.saveRecord(record) }.onFailure { Log.w(TAG, "Saving the library record failed", it) }
    }

    private companion object {
        const val TAG = "HyperIcon"
    }
}
