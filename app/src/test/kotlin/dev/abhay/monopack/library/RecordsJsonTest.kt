package dev.abhay.monopack.library

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.SeedPresets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json needs Android's implementation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordsJsonTest {
    @Test
    fun recordsKeepTheirShapeAndColourSelection() {
        val selection = Selection(IconStyle.DARK, Accent.SECONDARY, ColorSource.CUSTOM, SeedPresets.AOSP[3])
        val record = ExportRecord("Monopack.apk", ExportKind.ICON_PACK, "Monopack", null, 5, 200, 1, 2, "p.one", 7, "CIRCLE", selection)
        val decoded = RecordsJson.decode(RecordsJson.encode(mapOf(record.fileName to record)))
        assertThat(decoded.values.single()).isEqualTo(record)
    }

    @Test
    fun olderRecordsWithoutASelectionStillLoad() {
        val decoded = RecordsJson.decode("""{"a.apk":{"kind":"ICON_PACK","title":"A","at":1,"icons":2}}""")
        assertThat(decoded.values.single().selection).isNull()
    }
}
