package dev.abhay.hypericon.data

import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.Selection
import dev.abhay.hypericon.palette.SeedColors
import dev.abhay.hypericon.palette.SeedPresets
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectionStoreTest {
    // One test: DataStore allows a single active instance per file in a process.
    @Test
    fun `saves and restores selections, and clears the preview`() = runTest {
        val store = DataStoreSelectionStore(RuntimeEnvironment.getApplication())
        assertThat(store.load()).isNull()

        val pending = Selection(IconStyle.DARK, Accent.TERTIARY, ColorSource.CUSTOM, SeedColors.custom(42.0))
        val committed = Selection(IconStyle.LIGHT, Accent.PRIMARY, ColorSource.CUSTOM, SeedPresets.AOSP[0])
        val palette = IconPalette(0xFF112233.toInt(), 0xFFAABBCC.toInt())
        store.save(SavedSelections(pending, committed, palette))
        assertThat(store.load()).isEqualTo(SavedSelections(pending, committed, palette))

        val opposite = IconPalette(0xFF223344.toInt(), 0xFFBBCCDD.toInt())
        store.save(SavedSelections(pending, committed, palette, opposite))
        assertThat(store.load()).isEqualTo(SavedSelections(pending, committed, palette, opposite))

        store.save(SavedSelections(committed, null, null))
        assertThat(store.load()).isEqualTo(SavedSelections(committed, null, null))

        assertThat(store.loadEdits()).isEmpty()
        val edits = mapOf(
            "com.a/.Main" to IconEdit(IconStyle.DARK, 4, -6, inverted = true, contrast = 80),
            "com.b/com.other.Alias" to IconEdit(IconStyle.LIGHT),
        )
        store.saveEdits(edits)
        assertThat(store.loadEdits()).isEqualTo(edits)
        // Edits are independent of the selection.
        store.save(SavedSelections(pending, committed, palette, opposite))
        assertThat(store.loadEdits()).isEqualTo(edits)

        assertThat(store.loadLastTheme()).isNull()
        val theme = LastTheme("HyperIcon · Primary · Dark", IconStyle.DARK, "/sdcard/Download/HyperIcon/a.mtz")
        store.saveLastTheme(theme)
        assertThat(store.loadLastTheme()).isEqualTo(theme)
    }

    @Test
    fun `unreadable edits are skipped`() {
        assertThat(EditsJson.decode("not json")).isEmpty()
        val json = """{"ok/.A": {"base": "LIGHT", "glyph": 3, "plate": -2}, "bad/.B": {"base": "PURPLE"}, "worse/.C": 5}"""
        assertThat(EditsJson.decode(json)).containsExactly("ok/.A", IconEdit(IconStyle.LIGHT, 3, -2))
    }

    @Test
    fun `edits saved with one dark-colour offset move it to the layer showing that colour`() {
        val json = """{
            "l/.A": {"base": "LIGHT", "offset": -5},
            "d/.B": {"base": "DARK", "offset": -6},
            "li/.C": {"base": "LIGHT", "offset": 4, "inverted": true},
            "di/.D": {"base": "DARK", "offset": 2, "inverted": true}
        }"""
        assertThat(EditsJson.decode(json)).containsExactly(
            "l/.A", IconEdit(IconStyle.LIGHT, glyphToneOffset = -5),
            "d/.B", IconEdit(IconStyle.DARK, plateToneOffset = -6),
            "li/.C", IconEdit(IconStyle.LIGHT, plateToneOffset = 4, inverted = true),
            "di/.D", IconEdit(IconStyle.DARK, glyphToneOffset = 2, inverted = true),
        )
    }

    @Test
    fun `library records round-trip and bad entries are skipped`() {
        val records = mapOf(
            "a.mtz" to dev.abhay.hypericon.library.ExportRecord("a.mtz", dev.abhay.hypericon.export.ExportKind.THEME, "A · Dark", IconStyle.DARK, 5, 208, 1, 2),
            "b.apk" to dev.abhay.hypericon.library.ExportRecord("b.apk", dev.abhay.hypericon.export.ExportKind.ICON_PACK, "B · Light", IconStyle.LIGHT, 6, 200, packageName = "p.b", versionCode = 7),
        )
        val json = dev.abhay.hypericon.library.RecordsJson.encode(records)
        assertThat(dev.abhay.hypericon.library.RecordsJson.decode(json)).isEqualTo(records)
        assertThat(dev.abhay.hypericon.library.RecordsJson.decode("""{"x": {"kind": "NOPE"}, "y": 3}""")).isEmpty()
    }
}
