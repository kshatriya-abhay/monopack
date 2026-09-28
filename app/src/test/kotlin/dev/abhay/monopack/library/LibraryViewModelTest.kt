package dev.abhay.monopack.library

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.data.LastTheme
import dev.abhay.monopack.data.SavedSelections
import dev.abhay.monopack.data.SelectionStore
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.InstalledPack
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tree = "content://tree/primary%3ADownload%2FMonopack"

    private class Selections : SelectionStore {
        var lastTheme: LastTheme? = null
        override suspend fun load(): SavedSelections? = null
        override suspend fun save(saved: SavedSelections) = Unit
        override suspend fun loadEdits(): Map<String, IconEdit> = emptyMap()
        override suspend fun saveEdits(edits: Map<String, IconEdit>) = Unit
        override suspend fun loadLastTheme() = lastTheme
        override suspend fun saveLastTheme(theme: LastTheme) {
            lastTheme = theme
        }
        override suspend fun loadExportTarget(): String? = null
        override suspend fun saveExportTarget(target: String) = Unit
    }

    private val store = FakeLibraryStore()
    private val folder = FakeLibraryFolder()
    private val selections = Selections()

    private var installed = InstalledPack.SAME_SIGNER

    private fun viewModel() = LibraryViewModel(store, folder, selections, { installed })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `without a folder or its grant, onboarding is needed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.needsFolder).isTrue()

        store.tree = tree // chosen before, but the grant is gone
        vm.refresh()
        advanceUntilIdle()
        assertThat(vm.state.value.needsFolder).isTrue()

        vm.onFolderPicked(tree)
        advanceUntilIdle()
        assertThat(vm.state.value.needsFolder).isFalse()
        assertThat(folder.granted).containsExactly(tree)
    }

    @Test
    fun `items merge records and folder files, with missing ones marked`() = runTest(dispatcher) {
        store.tree = tree
        folder.granted += tree
        folder.add("A-Light-20260927-1000.mtz")
        folder.add("Monopack-Old-Dark-20250101-0000.mtz")
        store.records = mapOf(
            "A-Light-20260927-1000.mtz" to ExportRecord("A-Light-20260927-1000.mtz", ExportKind.THEME, "A · Light", IconStyle.LIGHT, 1_900_000_000_000, 200),
            "gone.apk" to ExportRecord("gone.apk", ExportKind.ICON_PACK, "Gone · Dark", IconStyle.DARK, 1_800_000_000_000, 200, packageName = "p.gone"),
        )
        val vm = viewModel()
        advanceUntilIdle()
        val items = vm.state.value.items
        assertThat(items.map { it.title }).containsExactly("A · Light", "Gone · Dark", "Monopack · Old · Dark").inOrder()
        assertThat(items[1].missing).isTrue()
        assertThat(vm.state.value.installed["gone.apk"]).isEqualTo(InstalledPack.SAME_SIGNER)
        assertThat(vm.state.value.pathFor(items[0])).isEqualTo("/storage/emulated/0/Download/Monopack/A-Light-20260927-1000.mtz")
    }

    @Test
    fun `deleting removes files and records, and a missing item can be forgotten`() = runTest(dispatcher) {
        store.tree = tree
        folder.granted += tree
        val a = folder.add("a.mtz")
        store.records = mapOf(
            "a.mtz" to ExportRecord("a.mtz", ExportKind.THEME, "A", IconStyle.LIGHT, 2, 1),
            "b.mtz" to ExportRecord("b.mtz", ExportKind.THEME, "B", IconStyle.LIGHT, 1, 1),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleSelection(vm.state.value.items.first { it.fileName == "a.mtz" })
        vm.deleteSelected()
        advanceUntilIdle()
        assertThat(folder.deleted).containsExactly(a.documentUri)
        assertThat(store.records.keys).containsExactly("b.mtz")
        assertThat(vm.state.value.selecting).isFalse()

        vm.removeMissing(vm.state.value.items.single())
        advanceUntilIdle()
        assertThat(store.records).isEmpty()
        assertThat(vm.state.value.items).isEmpty()
    }

    @Test
    fun `applying a theme makes it the Reapply target`() = runTest(dispatcher) {
        store.tree = tree
        folder.granted += tree
        folder.add("a.mtz")
        val vm = viewModel()
        advanceUntilIdle()
        val item = vm.state.value.items.single()
        vm.onApplied(item)
        advanceUntilIdle()
        assertThat(vm.state.value.isApplied(item)).isTrue()
        assertThat(selections.lastTheme!!.absolutePath).isEqualTo("/storage/emulated/0/Download/Monopack/a.mtz")
    }

    @Test
    fun `don't show again is remembered`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.applyHintDismissed).isFalse()
        vm.dismissApplyHint()
        advanceUntilIdle()
        assertThat(store.hintDismissed).isTrue()
        assertThat(viewModel().also { advanceUntilIdle() }.state.value.applyHintDismissed).isTrue()
    }

    @Test
    fun `the same-name warning follows the library, not deleted exports`() = runTest(dispatcher) {
        store.tree = tree
        folder.granted += tree
        installed = InstalledPack.NOT_INSTALLED
        folder.add("Mypack-Light-pack-20260927-1015.apk")
        store.records = mapOf(
            "Mypack-Light-pack-20260927-1015.apk" to ExportRecord(
                "Mypack-Light-pack-20260927-1015.apk", ExportKind.ICON_PACK, "My pack · Light", IconStyle.LIGHT, 1_900_000_000_000, 200,
                packageName = PackNaming.packageFor("My pack · Light"),
            ),
            // Deleted outside Monopack: no warning for it.
            "Gone-Dark-pack-20260927-1015.apk" to ExportRecord(
                "Gone-Dark-pack-20260927-1015.apk", ExportKind.ICON_PACK, "Gone · Dark", IconStyle.DARK, 1_800_000_000_000, 200,
                packageName = PackNaming.packageFor("Gone · Dark"),
            ),
        )
        folder.add("Other-Dark-pack-20260101-0000.apk") // not recorded: matched by name
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.packNameWarning("  my PACK · light ")).isEqualTo(PackNameWarning.Replaces(1_900_000_000_000))
        assertThat(vm.packNameWarning("Gone · Dark")).isNull()
        assertThat(vm.packNameWarning("Other · Dark")).isInstanceOf(PackNameWarning.Replaces::class.java)
        assertThat(vm.packNameWarning("New pack")).isNull()

        installed = InstalledPack.OTHER_SIGNER
        assertThat(vm.packNameWarning("New pack")).isEqualTo(PackNameWarning.Conflicts)
        installed = InstalledPack.SAME_SIGNER
        assertThat(vm.packNameWarning("New pack")).isEqualTo(PackNameWarning.Replaces(null))
    }
}
