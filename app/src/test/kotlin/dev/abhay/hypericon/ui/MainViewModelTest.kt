package dev.abhay.hypericon.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.apps.AppSource
import dev.abhay.hypericon.data.SavedSelections
import dev.abhay.hypericon.data.SelectionStore
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.PaletteSource
import dev.abhay.hypericon.palette.SeedPresets
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
    }

    private val fakePalettes = object : PaletteSource {
        override fun load() = wallpaper
    }

    private class FakeStore(var saved: SavedSelections? = null, val gate: CompletableDeferred<Unit>? = null) : SelectionStore {
        override suspend fun load(): SavedSelections? {
            gate?.await()
            return saved
        }

        override suspend fun save(saved: SavedSelections) {
            this.saved = saved
        }
    }

    private fun TestScope.viewModel(store: SelectionStore = FakeStore()) = MainViewModel(
        apps = fakeApps,
        loader = fakeLoader,
        palettes = fakePalettes,
        store = store,
        systemStyle = IconStyle.DARK,
        iconPx = 160,
        detailPx = 264,
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
    fun `long-press selects apps and flip swaps them to the opposite style in memory`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.preview()
        val (alpha, beta) = vm.state.value.items

        vm.onLongPress(alpha)
        vm.toggleSelection(beta)
        assertThat(vm.state.value.selected).containsExactly(alpha.app.key, beta.app.key)

        vm.flipSelected()
        var state = vm.state.value
        assertThat(state.selecting).isFalse()
        assertThat(state.flipped).containsExactly(alpha.app.key, beta.app.key)
        // Dark icons mode: flipped apps use the Light pair of the same accent.
        assertThat(state.committedFlipPalette).isEqualTo(wallpaper[Accent.PRIMARY]!![IconStyle.LIGHT])

        // Flips survive a new Preview, and follow the new selection's opposite style.
        vm.setIconStyle(IconStyle.LIGHT)
        vm.setAccent(Accent.TERTIARY)
        vm.preview()
        state = vm.state.value
        assertThat(state.flipped).containsExactly(alpha.app.key, beta.app.key)
        assertThat(state.committedFlipPalette).isEqualTo(wallpaper[Accent.TERTIARY]!![IconStyle.DARK])

        // Selecting only flipped apps turns the action into "unflip".
        vm.onLongPress(alpha)
        assertThat(vm.state.value.selectionAllFlipped).isTrue()
        vm.flipSelected()
        assertThat(vm.state.value.flipped).containsExactly(beta.app.key)

        // Nothing about flips is persisted.
        advanceUntilIdle()
        assertThat(store.saved).isNotNull()
    }

    @Test
    fun `select all picks every visible app and cancel clears the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setFilter(GridFilter.GENERATED)
        vm.selectAll()
        assertThat(vm.state.value.selected).hasSize(2)
        vm.clearSelection()
        assertThat(vm.state.value.selecting).isFalse()
    }

    @Test
    fun `restored preview also knows the opposite-style colours`() = runTest(dispatcher) {
        val seed = SeedPresets.AOSP[1]
        val selection = Selection(IconStyle.LIGHT, Accent.SECONDARY, ColorSource.CUSTOM, seed)
        val palette = seed.palettes()[Accent.SECONDARY]!![IconStyle.LIGHT]!!
        val vm = viewModel(FakeStore(SavedSelections(selection, selection, palette)))
        advanceUntilIdle()
        assertThat(vm.state.value.committedFlipPalette).isEqualTo(seed.palettes()[Accent.SECONDARY]!![IconStyle.DARK])
    }
}
