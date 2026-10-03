package dev.abhay.monopack.export

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.data.LastTheme
import dev.abhay.monopack.data.SavedSelections
import dev.abhay.monopack.data.SelectionStore
import dev.abhay.monopack.hyperos.ExportRequest
import dev.abhay.monopack.hyperos.ThemeExporter
import dev.abhay.monopack.library.FakeLibraryStore
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconPalette
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

class ExportRunnerTest {
    private val dispatcher = StandardTestDispatcher()

    private val selections = object : SelectionStore {
        override suspend fun load(): SavedSelections? = null
        override suspend fun save(saved: SavedSelections) = Unit
        override suspend fun loadEdits(): Map<String, IconEdit> = emptyMap()
        override suspend fun saveEdits(edits: Map<String, IconEdit>) = Unit
        override suspend fun loadLastTheme(): LastTheme? = null
        override suspend fun saveLastTheme(theme: LastTheme) = Unit
        override suspend fun loadExportTarget(): String? = null
        override suspend fun saveExportTarget(target: String) = Unit
    }
    private val themes = object : ThemeExporter {
        override suspend fun export(request: ExportRequest, onProgress: (Int, Int) -> Unit) = File(request.fileName)
        override suspend fun clearCache() = Unit
    }
    private val saver = object : ExportSaver {
        override suspend fun save(file: File, packageName: String?) = SavedExport("content://x/${file.name}", "Download/Monopack/${file.name}", "")
        override suspend fun copyTo(file: File, uri: String) = Unit
    }

    /** Pack A blocks in work that can't be cancelled (like ARSCLib and apksig) until [releaseA]. */
    private val releaseA = CompletableDeferred<Unit>()
    private val packs = object : PackExporter {
        override suspend fun export(request: PackRequest, onProgress: (Int, Int) -> Unit, onSigning: () -> Unit): File {
            if (request.name == "A") withContext(NonCancellable) { releaseA.await() }
            onProgress(1, 1)
            return File(request.fileName)
        }
    }

    private fun pack(name: String) = ExportJob.Pack(PackRequest(name, "$name.apk", 1, "1", emptyList(), IconPalette(0, 0)))

    @Test
    fun aCancelledExportFinishingLateDoesNotOverwriteTheNextOne() = runTest(dispatcher) {
        val runner = ExportRunner(CoroutineScope(dispatcher), themes, packs, saver, selections, FakeLibraryStore()) {}
        val seen = mutableListOf<ExportState>()
        backgroundScope.launch(dispatcher) { runner.state.collect { seen += it } }

        runner.start(pack("A"))
        runCurrent()
        runner.cancel()
        assertThat(runner.start(pack("B"))).isTrue()
        runCurrent()
        val afterB = seen.size
        // A's build ends after B started: B's state must not be replaced by A's Idle.
        releaseA.complete(Unit)
        advanceUntilIdle()

        val done = runner.state.value as ExportState.Done
        assertThat(done.files.single().title).isEqualTo("B")
        assertThat(seen.drop(afterB).none { it == ExportState.Idle }).isTrue()
    }
}
