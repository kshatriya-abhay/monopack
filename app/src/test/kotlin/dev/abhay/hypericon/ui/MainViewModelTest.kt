package dev.abhay.hypericon.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.apps.AppSource
import dev.abhay.hypericon.data.LastTheme
import dev.abhay.hypericon.data.SavedSelections
import dev.abhay.hypericon.data.SelectionStore
import dev.abhay.hypericon.export.ExportRequest
import dev.abhay.hypericon.export.ExportSaver
import dev.abhay.hypericon.export.SavedExport
import dev.abhay.hypericon.export.ThemeExporter
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.IconEdits
import dev.abhay.hypericon.palette.PaletteSource
import dev.abhay.hypericon.palette.SeedPresets
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val wallpaper = SeedPresets.AOSP[3].palettes()

    private fun app(pkg: String, label: String) = LauncherApp(
        component = ComponentName(pkg, "$pkg.Main"),
        label = label,
        iconRes = 0,
        appInfo = ApplicationInfo(),
        isMainActivity = true,
        lastUpdateTime = 1L,
    )

    private val apps = listOf(app("a.native", "Alpha"), app("b.forced", "Beta"), app("c.forced", "Gamma"))

    private val fakeApps = object : AppSource {
        override suspend fun scan() = apps
    }

    private val fakeLoader = object : ItemLoader {
        override fun load(app: LauncherApp, iconPx: Int, glyphPx: Int): DrawerItem {
            val source = if (app.packageName.endsWith("native")) GlyphSource.NATIVE_MONO else GlyphSource.FORCED_MONO
            return DrawerItem(app, glyph = Glyph(createBitmap(4, 4, Bitmap.Config.ALPHA_8), source))
        }

        override fun details(app: LauncherApp, sizePx: Int) = DetailImages(null, null)

        override fun glyph(app: LauncherApp, sizePx: Int) = null
    }

    private val fakePalettes = object : PaletteSource {
        override fun load() = wallpaper
    }

    private class FakeStore(
        var saved: SavedSelections? = null,
        val gate: CompletableDeferred<Unit>? = null,
        var edits: Map<String, IconEdit> = emptyMap(),
        val editsGate: CompletableDeferred<Unit>? = null,
    ) : SelectionStore {
        override suspend fun load(): SavedSelections? {
            gate?.await()
            return saved
        }

        override suspend fun save(saved: SavedSelections) {
            this.saved = saved
        }

        override suspend fun loadEdits(): Map<String, IconEdit> {
            editsGate?.await()
            return edits
        }

        override suspend fun saveEdits(edits: Map<String, IconEdit>) {
            this.edits = edits
        }

        var lastTheme: LastTheme? = null

        override suspend fun loadLastTheme() = lastTheme

        override suspend fun saveLastTheme(theme: LastTheme) {
            lastTheme = theme
        }
    }

    private class FakeExporter : ThemeExporter {
        val requests = mutableListOf<ExportRequest>()
        val request get() = requests.lastOrNull()
        override suspend fun export(request: ExportRequest, onProgress: (Int, Int) -> Unit): File {
            requests += request
            request.apps.indices.forEach { onProgress(it + 1, request.apps.size) }
            return File(request.fileName)
        }

        override suspend fun clearCache() = Unit
    }

    private val exporter = FakeExporter()

    private val saver = object : ExportSaver {
        override suspend fun save(file: File) =
            SavedExport("content://downloads/1", "Download/HyperIcon/${file.name}", "/sdcard/Download/HyperIcon/${file.name}")

        override suspend fun copyTo(file: File, uri: String) = Unit
    }

    private val existingFiles = mutableSetOf<String>()

    private fun TestScope.viewModel(store: SelectionStore = FakeStore()) = MainViewModel(
        apps = fakeApps,
        loader = fakeLoader,
        palettes = fakePalettes,
        store = store,
        exporter = exporter,
        saver = saver,
        systemStyle = IconStyle.DARK,
        iconPx = 160,
        detailPx = 264,
        fileExists = { it in existingFiles },
        loadDispatcher = dispatcher,
        workDispatcher = dispatcher,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `icons are fetched on launch without any user action`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value
        assertThat(state.iconsReady).isTrue()
        assertThat(state.items.map { it.app.label }).containsExactly("Alpha", "Beta", "Gamma").inOrder()
        assertThat(state.pending.style).isEqualTo(IconStyle.DARK)
        assertThat(state.previewEnabled).isTrue()
    }

    @Test
    fun `preview commits the pending selection and saves it`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()

        vm.setAccent(Accent.SECONDARY)
        vm.preview()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.committed).isEqualTo(state.pending)
        assertThat(state.committedPalette).isEqualTo(wallpaper[Accent.SECONDARY]!![IconStyle.DARK])
        assertThat(state.previewEnabled).isFalse()
        assertThat(store.saved?.committed?.accent).isEqualTo(Accent.SECONDARY)

        vm.setIconStyle(IconStyle.LIGHT)
        assertThat(vm.state.value.previewEnabled).isTrue()
    }

    @Test
    fun `a saved preview is restored with Preview disabled`() = runTest(dispatcher) {
        val selection = Selection(IconStyle.LIGHT, Accent.TERTIARY, ColorSource.CUSTOM, SeedPresets.AOSP[1])
        val palette = SeedPresets.AOSP[1].palettes()[Accent.TERTIARY]!![IconStyle.LIGHT]!!
        val vm = viewModel(FakeStore(SavedSelections(selection, selection, palette)))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.pending).isEqualTo(selection)
        assertThat(state.committed).isEqualTo(selection)
        assertThat(state.committedPalette).isEqualTo(palette)
        assertThat(state.previewEnabled).isFalse()
    }

    @Test
    fun `a change made before the saved selection loads wins`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val saved = Selection(IconStyle.LIGHT, Accent.TERTIARY)
        val store = FakeStore(SavedSelections(saved, null, null), gate)
        val vm = viewModel(store)
        advanceUntilIdle()

        vm.setAccent(Accent.SECONDARY)
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(vm.state.value.pending.accent).isEqualTo(Accent.SECONDARY)
        assertThat(vm.state.value.pending.style).isEqualTo(IconStyle.DARK)
    }

    @Test
    fun `filter shows native or generated icons only`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilter(GridFilter.NATIVE)
        assertThat(vm.state.value.visibleItems.map { it.app.label }).containsExactly("Alpha")
        vm.setFilter(GridFilter.GENERATED)
        assertThat(vm.state.value.visibleItems.map { it.app.label }).containsExactly("Beta", "Gamma").inOrder()
        vm.setFilter(GridFilter.ALL)
        assertThat(vm.state.value.visibleItems).hasSize(3)
    }

    @Test
    fun `edit icon works on one selected app and opens the editor`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val (alpha, beta) = vm.state.value.items

        // No editor before a Preview.
        vm.openEditor(alpha.app.key)
        assertThat(vm.state.value.editorTarget).isNull()

        vm.preview()
        vm.onLongPress(alpha)
        assertThat(vm.state.value.canEditSelection).isTrue()
        vm.toggleSelection(beta)
        assertThat(vm.state.value.canEditSelection).isFalse()
        vm.editSelection()
        assertThat(vm.state.value.editorTarget).isNull()

        vm.toggleSelection(beta)
        vm.editSelection()
        val state = vm.state.value
        assertThat(state.editorTarget).isEqualTo(alpha.app.key)
        assertThat(state.selecting).isFalse()
        vm.closeEditor()
        assertThat(vm.state.value.editorTarget).isNull()
    }

    @Test
    fun `saved edits use an absolute base and follow new colours`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.preview() // Dark icons, Primary
        val alpha = vm.state.value.items[0].app.key
        val beta = vm.state.value.items[1].app.key

        vm.openEditor(alpha)
        vm.saveEdit(alpha, IconEdit(IconStyle.LIGHT, -5))
        var state = vm.state.value
        assertThat(state.editorTarget).isNull()
        val lightPrimary = wallpaper[Accent.PRIMARY]!![IconStyle.LIGHT]!!
        assertThat(state.paletteFor(alpha)).isEqualTo(IconEdits.withDarkToneOffset(lightPrimary, -5))
        assertThat(state.paletteFor(beta)).isEqualTo(wallpaper[Accent.PRIMARY]!![IconStyle.DARK])

        // Switching the global style keeps the absolute base; a new accent recolours with the offset kept.
        vm.setIconStyle(IconStyle.LIGHT)
        vm.setAccent(Accent.TERTIARY)
        vm.preview()
        state = vm.state.value
        val lightTertiary = wallpaper[Accent.TERTIARY]!![IconStyle.LIGHT]!!
        assertThat(state.paletteFor(alpha)).isEqualTo(IconEdits.withDarkToneOffset(lightTertiary, -5))
        vm.setIconStyle(IconStyle.DARK)
        vm.preview()
        val darkTertiary = wallpaper[Accent.TERTIARY]!![IconStyle.DARK]!!
        assertThat(vm.state.value.paletteFor(alpha)).isEqualTo(IconEdits.withDarkToneOffset(lightTertiary, -5))
        assertThat(vm.state.value.paletteFor(beta)).isEqualTo(darkTertiary)

        // Reset brings it back to the global style.
        vm.resetEdit(alpha)
        assertThat(vm.state.value.paletteFor(alpha)).isEqualTo(darkTertiary)

        // Edits aren't persisted (session only).
        advanceUntilIdle()
        assertThat(store.saved!!.toString()).doesNotContain("IconEdit")
    }

    @Test
    fun `reset clears the edits of the selected apps`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.preview()
        val (alpha, beta, gamma) = vm.state.value.items.map { it.app.key }
        vm.saveEdit(alpha, IconEdit(IconStyle.LIGHT))
        vm.saveEdit(gamma, IconEdit(IconStyle.LIGHT, 3))
        vm.onLongPress(vm.state.value.items[1])
        assertThat(vm.state.value.selectionHasEdits).isFalse()
        vm.toggleSelection(vm.state.value.items[2])
        assertThat(vm.state.value.selectionHasEdits).isTrue()
        vm.resetSelectedEdits()
        // Only the selected apps (beta, gamma) are reset; alpha keeps its edit.
        assertThat(vm.state.value.edits.keys).containsExactly(alpha)
        assertThat(vm.state.value.selecting).isFalse()
        assertThat(beta).isNotEmpty()
    }

    @Test
    fun `cancel clears the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.preview()
        vm.onLongPress(vm.state.value.items[0])
        vm.toggleSelection(vm.state.value.items[1])
        assertThat(vm.state.value.selected).hasSize(2)
        vm.clearSelection()
        assertThat(vm.state.value.selecting).isFalse()
    }

    @Test
    fun `reapply targets the last export until a theme is applied, and survives a restart`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        assertThat(vm.state.value.lastTheme).isNull()
        vm.preview()
        existingFiles += listOf(
            "/sdcard/Download/HyperIcon/HyperIcon-Primary-Light-20260927-1015.mtz",
            "/sdcard/Download/HyperIcon/HyperIcon-Primary-Dark-20260927-1015.mtz",
        )
        vm.export(ExportOptions("HyperIcon · Primary", setOf(IconStyle.LIGHT, IconStyle.DARK)), java.time.LocalDateTime.of(2026, 9, 27, 10, 15))
        advanceUntilIdle()

        // Both: the previewed (Dark, the system style here) file is the target.
        assertThat(vm.state.value.lastTheme!!.style).isEqualTo(IconStyle.DARK)
        assertThat(vm.state.value.lastThemeAvailable).isTrue()
        val light = (vm.state.value.export as ExportState.Done).files.first { it.style == IconStyle.LIGHT }
        vm.onThemeApplied(light)
        advanceUntilIdle()
        assertThat(store.lastTheme).isEqualTo(LastTheme("HyperIcon · Primary · Light", IconStyle.LIGHT, light.absolutePath))

        val restarted = viewModel(store)
        advanceUntilIdle()
        assertThat(restarted.state.value.lastTheme).isEqualTo(store.lastTheme)
        assertThat(restarted.state.value.lastThemeAvailable).isTrue()

        // Deleted from Downloads: noticed on the next resume.
        existingFiles.clear()
        restarted.onResume()
        assertThat(restarted.state.value.lastThemeAvailable).isFalse()
    }

    @Test
    fun `edits are saved and survive a restart`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.preview()
        val (alpha, beta) = vm.state.value.items.map { it.app.key }
        vm.saveEdit(alpha, IconEdit(IconStyle.LIGHT, -4, inverted = true, contrast = 70))
        vm.saveEdit(beta, IconEdit(IconStyle.DARK))
        vm.resetEdit(beta)
        advanceUntilIdle()
        assertThat(store.edits).containsExactly(alpha, IconEdit(IconStyle.LIGHT, -4, inverted = true, contrast = 70))

        val restarted = viewModel(store)
        advanceUntilIdle()
        assertThat(restarted.state.value.edits).isEqualTo(store.edits)
        assertThat(restarted.state.value.paletteFor(alpha)).isNotNull()
    }

    @Test
    fun `an edit made before saved edits load keeps both`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(edits = mapOf("a.native/.Main" to IconEdit(IconStyle.DARK, 2)), editsGate = gate)
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.preview()
        val gamma = vm.state.value.items[2].app.key
        vm.saveEdit(gamma, IconEdit(IconStyle.LIGHT))
        advanceUntilIdle()
        // Nothing is written until the saved edits are merged in.
        assertThat(store.edits.keys).containsExactly("a.native/.Main")

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.state.value.edits.keys).containsExactly("a.native/.Main", gamma)
        assertThat(store.edits.keys).containsExactly("a.native/.Main", gamma)
    }

    @Test
    fun `the saved opposite-style colours are restored as previewed`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.preview()
        advanceUntilIdle()
        val previewedOpposite = vm.state.value.committedOppositePalette
        assertThat(store.saved!!.committedOppositePalette).isEqualTo(previewedOpposite)

        // A wallpaper change after the preview must not change a restored grid.
        val changed = SavedSelections(store.saved!!.pending, store.saved!!.committed, store.saved!!.committedPalette, IconPalette(1, 2))
        val restarted = viewModel(FakeStore(changed))
        advanceUntilIdle()
        assertThat(restarted.state.value.committedOppositePalette).isEqualTo(IconPalette(1, 2))
    }

    @Test
    fun `the edited filter shows edited apps and falls back to all when emptied`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.preview()
        val beta = vm.state.value.items[1]
        vm.saveEdit(beta.app.key, IconEdit(IconStyle.LIGHT))
        assertThat(vm.state.value.editedCount).isEqualTo(1)
        vm.setFilter(GridFilter.EDITED)
        assertThat(vm.state.value.visibleItems).containsExactly(beta)
        vm.resetEdit(beta.app.key)
        assertThat(vm.state.value.filter).isEqualTo(GridFilter.ALL)
    }

    @Test
    fun `edits of apps that are not installed are kept but not counted`() = runTest(dispatcher) {
        val store = FakeStore(edits = mapOf("gone.app/.Main" to IconEdit(IconStyle.DARK)))
        val vm = viewModel(store)
        advanceUntilIdle()
        assertThat(vm.state.value.edits).containsKey("gone.app/.Main")
        assertThat(vm.state.value.editedCount).isEqualTo(0)
    }

    @Test
    fun `restored preview from before 3a recomputes the opposite-style colours`() = runTest(dispatcher) {
        val seed = SeedPresets.AOSP[1]
        val selection = Selection(IconStyle.LIGHT, Accent.SECONDARY, ColorSource.CUSTOM, seed)
        val palette = seed.palettes()[Accent.SECONDARY]!![IconStyle.LIGHT]!!
        val vm = viewModel(FakeStore(SavedSelections(selection, selection, palette)))
        advanceUntilIdle()
        assertThat(vm.state.value.committedOppositePalette).isEqualTo(seed.palettes()[Accent.SECONDARY]!![IconStyle.DARK])
    }

    @Test
    fun `export needs a preview and exports every launcher entry with edits applied`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.exportEnabled).isFalse()
        assertThat(vm.defaultExportOptions()).isNull()

        vm.preview()
        val beta = vm.state.value.items[1]
        vm.saveEdit(beta.app.key, IconEdit(IconStyle.LIGHT, contrast = 60))
        val options = vm.defaultExportOptions()!!
        assertThat(options).isEqualTo(ExportOptions("HyperIcon · Primary", setOf(IconStyle.DARK)))
        vm.export(options, java.time.LocalDateTime.of(2026, 9, 27, 10, 15))
        advanceUntilIdle()

        val request = exporter.request!!
        assertThat(request.title).isEqualTo("HyperIcon · Primary · Dark")
        assertThat(request.fileName).isEqualTo("HyperIcon-Primary-Dark-20260927-1015.mtz")
        assertThat(request.apps.map { it.app.label }).containsExactly("Alpha", "Beta", "Gamma").inOrder()
        val dark = wallpaper[Accent.PRIMARY]!![IconStyle.DARK]
        val light = wallpaper[Accent.PRIMARY]!![IconStyle.LIGHT]
        assertThat(request.apps.map { it.palette }).containsExactly(dark, light, dark).inOrder()
        assertThat(request.apps.map { it.contrast }).containsExactly(0, 60, 0).inOrder()
        assertThat(request.apps.first().folders).containsExactly("a.native.Main", "a.native").inOrder()

        val done = vm.state.value.export as ExportState.Done
        val file = done.files.single()
        assertThat(file.location).isEqualTo("Download/HyperIcon/HyperIcon-Primary-Dark-20260927-1015.mtz")
        assertThat(file.absolutePath).isEqualTo("/sdcard/Download/HyperIcon/HyperIcon-Primary-Dark-20260927-1015.mtz")
        assertThat(file.iconCount).isEqualTo(3)
        vm.dismissExport()
        assertThat(vm.state.value.export).isEqualTo(ExportState.Idle)
    }

    @Test
    fun `exporting both styles keeps edited apps on their absolute base`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.preview() // Dark icons
        vm.saveEdit(vm.state.value.items[1].app.key, IconEdit(IconStyle.LIGHT, -4))
        vm.export(ExportOptions("Mine", setOf(IconStyle.LIGHT, IconStyle.DARK)), java.time.LocalDateTime.of(2026, 9, 27, 10, 15))
        advanceUntilIdle()

        val (lightReq, darkReq) = exporter.requests
        val dark = wallpaper[Accent.PRIMARY]!![IconStyle.DARK]
        val light = wallpaper[Accent.PRIMARY]!![IconStyle.LIGHT]
        val edited = IconEdits.withDarkToneOffset(light!!, -4)
        assertThat(lightReq.title).isEqualTo("Mine · Light")
        assertThat(lightReq.apps.map { it.palette }).containsExactly(light, edited, light).inOrder()
        assertThat(darkReq.title).isEqualTo("Mine · Dark")
        assertThat(darkReq.apps.map { it.palette }).containsExactly(dark, edited, dark).inOrder()
        assertThat((vm.state.value.export as ExportState.Done).files.map { it.style })
            .containsExactly(IconStyle.LIGHT, IconStyle.DARK).inOrder()
    }

    @Test
    fun `generated icons can be left out`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.preview()
        vm.export(ExportOptions("x", setOf(IconStyle.DARK), includeGenerated = false))
        advanceUntilIdle()
        assertThat(exporter.request!!.apps.map { it.app.label }).containsExactly("Alpha")
    }

    @Test
    fun `custom colours are named in the export title`() {
        val selection = Selection(IconStyle.LIGHT, Accent.TERTIARY, ColorSource.CUSTOM, SeedPresets.AOSP[4])
        assertThat(MainViewModel.exportTitle(selection)).isEqualTo("HyperIcon · Blue · Tertiary · Light")
        assertThat(MainViewModel.fileNameFor("My theme! · Light", java.time.LocalDateTime.of(2026, 1, 2, 3, 4)))
            .isEqualTo("Mytheme-Light-20260102-0304.mtz")
    }

    @Test
    fun `apply intent asks Theme Manager for icons only`() {
        val intent = dev.abhay.hypericon.export.ThemeApplier.intent("/sdcard/Download/HyperIcon/t.mtz")
        assertThat(intent.component?.flattenToString())
            .isEqualTo("com.android.thememanager/com.android.thememanager.ApplyThemeForScreenshot")
        assertThat(intent.getLongExtra("theme_apply_flags", -1)).isEqualTo(0x8L)
        assertThat(intent.getLongExtra("theme_remove_flags", -1)).isEqualTo(0L)
        assertThat(intent.getStringExtra("theme_file_path")).isEqualTo("/sdcard/Download/HyperIcon/t.mtz")
        assertThat(intent.getStringExtra("api_called_from")).isEqualTo("com.android.thememanager")
    }

    @Test
    fun `default palette banner can be dismissed for the session`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value.defaultPaletteBannerDismissed).isFalse()
        vm.dismissDefaultPaletteBanner()
        assertThat(vm.state.value.defaultPaletteBannerDismissed).isTrue()
    }
}
